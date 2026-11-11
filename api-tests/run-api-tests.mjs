#!/usr/bin/env node
/**
 * Self-contained Bruno API test runner.
 *
 * 1. Starts the app via `gradlew bootTestRun` (TestChessClubApplication -> Testcontainers MongoDB).
 * 2. Waits for GET /actuator/health to report UP.
 * 3. Runs the Bruno collection and writes a JUnit report to build/test-results/bruno/results.xml.
 * 4. Always stops the app (whole process tree) and exits with Bruno's exit code.
 *
 * Requires Docker to be running (for the MongoDB container).
 * Port defaults to 18080; override with the API_TEST_PORT environment variable.
 */
import { spawn, spawnSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_PORT = 18080;
const PORT = parsePort(process.env.API_TEST_PORT ?? String(DEFAULT_PORT));
const BASE_URL = `http://localhost:${PORT}`;
const HEALTH_URL = `${BASE_URL}/actuator/health`;
const STARTUP_TIMEOUT_MS = 180_000;
const POLL_INTERVAL_MS = 2_000;
const SHUTDOWN_TIMEOUT_MS = 30_000;
const BRUNO_FOLDERS = ['players', 'games', 'docs', 'teardown'];

const IS_WINDOWS = process.platform === 'win32';
const API_TESTS_DIR = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(API_TESTS_DIR, '..');
const BRUNO_DIR = path.join(API_TESTS_DIR, 'bruno');
const BRU_BIN = path.join(API_TESTS_DIR, 'node_modules', '@usebruno', 'cli', 'bin', 'bru.js');
const JUNIT_REPORT = path.join(REPO_ROOT, 'build', 'test-results', 'bruno', 'results.xml');

function parsePort(value) {
  const port = Number(value);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    console.error(`[api-tests] Invalid API_TEST_PORT: ${value}`);
    process.exit(1);
  }
  return port;
}

const log = (msg) => console.log(`[api-tests] ${msg}`);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Resolves true if something is accepting TCP connections on the port. */
function isPortInUse(port) {
  return new Promise((resolve) => {
    const socket = net.connect({ port, host: '127.0.0.1' });
    socket.setTimeout(1_000);
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('timeout', () => { socket.destroy(); resolve(false); });
    socket.once('error', () => resolve(false));
  });
}

function startApp() {
  const args = ['bootTestRun', `--args=--server.port=${PORT}`, '--console=plain'];
  // .bat files cannot be spawned directly without a shell on Windows, so go through cmd.exe with an
  // absolute, quoted path (cmd may not search the cwd when NoDefaultCurrentDirectoryInExePath is set).
  const child = IS_WINDOWS
    ? spawn(
        'cmd.exe',
        ['/d', '/s', '/c', `""${path.join(REPO_ROOT, 'gradlew.bat')}" ${args.join(' ')}"`],
        { cwd: REPO_ROOT, stdio: 'inherit', windowsVerbatimArguments: true }
      )
    : spawn(path.join(REPO_ROOT, 'gradlew'), args, { cwd: REPO_ROOT, stdio: 'inherit', detached: true });
  child.exitedEarly = false;
  child.once('exit', () => { child.exitedEarly = true; });
  return child;
}

async function waitForHealthy(app) {
  const deadline = Date.now() + STARTUP_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (app.exitedEarly) throw new Error('Application process exited before becoming healthy (is Docker running?)');
    try {
      const res = await fetch(HEALTH_URL, { signal: AbortSignal.timeout(POLL_INTERVAL_MS) });
      if (res.ok && (await res.json()).status === 'UP') return;
    } catch {
      // Not up yet; keep polling.
    }
    await sleep(POLL_INTERVAL_MS);
  }
  throw new Error(`Application did not report UP at ${HEALTH_URL} within ${STARTUP_TIMEOUT_MS / 1000}s`);
}

function runBruno() {
  mkdirSync(path.dirname(JUNIT_REPORT), { recursive: true });
  const result = spawnSync(
    process.execPath,
    [BRU_BIN, 'run', ...BRUNO_FOLDERS, '--env', 'local', '--env-var', `baseUrl=${BASE_URL}`, '--reporter-junit', JUNIT_REPORT],
    { cwd: BRUNO_DIR, stdio: 'inherit' }
  );
  if (result.error) throw result.error;
  return result.status ?? 1;
}

function killTree(child) {
  if (!child.pid || child.exitedEarly) return;
  if (IS_WINDOWS) {
    spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' });
  } else {
    try { process.kill(-child.pid, 'SIGTERM'); } catch { /* already gone */ }
  }
}

/** Last resort: the Gradle daemon (not the client we spawned) owns the app JVM, so kill whatever listens on PORT. */
function killPortListener(port) {
  if (IS_WINDOWS) {
    const out = spawnSync('netstat', ['-ano', '-p', 'TCP'], { encoding: 'utf8' }).stdout ?? '';
    const pids = new Set(
      out.split(/\r?\n/)
        .map((line) => line.trim().split(/\s+/))
        .filter((cols) => cols[1]?.endsWith(`:${port}`) && cols[3] === 'LISTENING')
        .map((cols) => cols[4])
    );
    pids.forEach((pid) => spawnSync('taskkill', ['/pid', pid, '/T', '/F'], { stdio: 'ignore' }));
  } else {
    const out = spawnSync('lsof', ['-ti', `tcp:${port}`, '-sTCP:LISTEN'], { encoding: 'utf8' }).stdout ?? '';
    out.split(/\s+/).filter(Boolean).forEach((pid) => {
      try { process.kill(Number(pid), 'SIGTERM'); } catch { /* already gone */ }
    });
  }
}

async function stopApp(app) {
  log('Stopping application...');
  killTree(app);
  const deadline = Date.now() + SHUTDOWN_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (!(await isPortInUse(PORT))) return;
    await sleep(1_000);
  }
  log(`Port ${PORT} still in use after ${SHUTDOWN_TIMEOUT_MS / 1000}s; killing the listener.`);
  killPortListener(PORT);
}

async function main() {
  if (await isPortInUse(PORT)) {
    console.error(`[api-tests] Port ${PORT} is already in use. Stop whatever is running there (e.g. a bootRun) or set API_TEST_PORT to a free port.`);
    return 1;
  }

  log('Starting application with Testcontainers MongoDB (gradlew bootTestRun)...');
  const app = startApp();
  let stopping = false;
  const onSignal = async () => {
    if (stopping) return;
    stopping = true;
    await stopApp(app);
    process.exit(130);
  };
  process.once('SIGINT', onSignal);
  process.once('SIGTERM', onSignal);

  try {
    await waitForHealthy(app);
    log('Application is UP. Running Bruno collection...');
    const code = runBruno();
    log(`Bruno finished with exit code ${code}. JUnit report: ${JUNIT_REPORT}`);
    return code;
  } catch (err) {
    console.error(`[api-tests] ${err.message}`);
    return 1;
  } finally {
    stopping = true;
    await stopApp(app);
  }
}

process.exitCode = await main();
