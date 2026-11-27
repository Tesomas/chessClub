export interface PgnHeader {
  name: string;
  value: string;
}

export interface PgnMove {
  number: number;
  white?: string;
  black?: string;
}

export interface ParsedPgn {
  headers: PgnHeader[];
  moves: PgnMove[];
  result: string | null;
}

type Side = 'white' | 'black';

/** Characters that may legally follow a token. */
const END = String.raw`(?=[\s(){};$]|$)`;
const TAG_PAIR = /\[\s*([A-Za-z0-9_]+)\s+"((?:[^"\\]|\\.)*)"\s*\]/y;
const RESULT = new RegExp(String.raw`(1-0|0-1|1\/2-1\/2|\*)${END}`, 'y');
/** `12.`, `12...` and `12. ...` (dots may be spaced). */
const MOVE_NUMBER = /(\d+)\s*(\.(?:\s*\.)*)/y;
const SAN = new RegExp(
  String.raw`(?:O-O-O|O-O|0-0-0|0-0|--|[KQRBN][a-h]?[1-8]?x?[a-h][1-8]|[a-h](?:x[a-h])?[1-8](?:=?[QRBN])?)[+#]?[!?]{0,3}${END}`,
  'y',
);
const NAG = new RegExp(String.raw`\$\d+${END}`, 'y');

class PgnSyntaxError extends Error {}

/** Mutable scanner state, local to a single parse call. */
class Scanner {
  pos = 0;
  constructor(readonly text: string) {}

  get done(): boolean {
    return this.pos >= this.text.length;
  }

  peek(): string {
    return this.text[this.pos];
  }

  /** Matches a sticky regex at the current position and advances past it. */
  match(pattern: RegExp): RegExpExecArray | null {
    pattern.lastIndex = this.pos;
    const found = pattern.exec(this.text);
    if (found) this.pos = pattern.lastIndex;
    return found;
  }

  skipWhitespace(): void {
    while (!this.done && /\s/.test(this.peek())) this.pos++;
  }

  skipTo(char: string, required: boolean): void {
    const end = this.text.indexOf(char, this.pos);
    if (end === -1) {
      if (required) throw new PgnSyntaxError(`missing ${char}`);
      this.pos = this.text.length;
      return;
    }
    this.pos = end + 1;
  }

  /** True when the scanner is at the start of a line (escape lines begin with `%`). */
  atLineStart(): boolean {
    return this.pos === 0 || this.text[this.pos - 1] === '\n';
  }
}

/** Skips a `( ... )` variation, which may contain comments and nested variations. */
function skipVariation(scanner: Scanner): void {
  let depth = 0;
  while (!scanner.done) {
    const char = scanner.peek();
    if (char === '{') {
      scanner.skipTo('}', true);
      continue;
    }
    if (char === ';') {
      scanner.skipTo('\n', false);
      continue;
    }
    scanner.pos++;
    if (char === '(') depth++;
    if (char === ')' && --depth === 0) return;
  }
  throw new PgnSyntaxError('unterminated variation');
}

/** Skips comments, NAGs, escape lines and variations; false when none was found. */
function skipNonMove(scanner: Scanner): boolean {
  const char = scanner.peek();
  if (char === '{') scanner.skipTo('}', true);
  else if (char === ';' || (char === '%' && scanner.atLineStart())) scanner.skipTo('\n', false);
  else if (char === '(') skipVariation(scanner);
  else if (char === ')') throw new PgnSyntaxError('unmatched )');
  else if (!scanner.match(NAG)) return false;
  return true;
}

function unescape(value: string): string {
  return value.replace(/\\(["\\])/g, '$1');
}

function readHeaders(scanner: Scanner): PgnHeader[] {
  const headers: PgnHeader[] = [];
  for (scanner.skipWhitespace(); !scanner.done; scanner.skipWhitespace()) {
    if (scanner.peek() === '[') {
      const tag = scanner.match(TAG_PAIR);
      if (!tag) throw new PgnSyntaxError('malformed tag pair');
      headers.push({ name: tag[1], value: unescape(tag[2]) });
    } else if (!skipNonMove(scanner)) {
      break;
    }
  }
  return headers;
}

/** Appends a move for `side`, pairing it with the previous white move of the same number. */
/**
 * Mutates `moves`, which is local to one parse: copying the array per ply made long games O(n^2).
 */
function addMove(moves: PgnMove[], number: number, side: Side, san: string): void {
  const last = moves.at(-1);
  if (side === 'black' && last?.number === number && last.black === undefined) {
    moves[moves.length - 1] = { ...last, black: san };
    return;
  }
  moves.push(side === 'white' ? { number, white: san } : { number, black: san });
}

/** After the result only whitespace and comments may follow; a second game is unsupported. */
function expectOnlyComments(scanner: Scanner): void {
  for (scanner.skipWhitespace(); !scanner.done; scanner.skipWhitespace()) {
    const char = scanner.peek();
    if (char === '{') scanner.skipTo('}', true);
    else if (char === ';' || (char === '%' && scanner.atLineStart())) scanner.skipTo('\n', false);
    else throw new PgnSyntaxError(`unexpected input after the result at ${scanner.pos}`);
  }
}

/**
 * Parses a single-game PGN: tag pairs, then movetext, then nothing but comments. Comments, NAGs and variations are skipped
 * (variations are not shown), and annotation glyphs such as `!?` stay on the move.
 * Returns null for input that isn't PGN so callers can show the raw text instead.
 */
export function parsePgn(text: string): ParsedPgn | null {
  const scanner = new Scanner(text.replace(/\r\n?/g, '\n'));
  try {
    const headers = readHeaders(scanner);
    const moves: PgnMove[] = [];
    let result: string | null = null;
    let number = 1;
    let side: Side = 'white';

    for (scanner.skipWhitespace(); !scanner.done; scanner.skipWhitespace()) {
      if (skipNonMove(scanner)) continue;
      const resultToken = scanner.match(RESULT);
      if (resultToken) {
        result = resultToken[1];
        expectOnlyComments(scanner);
        break;
      }
      const san = scanner.match(SAN);
      if (san) {
        addMove(moves, number, side, san[0]);
        if (side === 'black') number++;
        side = side === 'white' ? 'black' : 'white';
        continue;
      }
      const moveNumber = scanner.match(MOVE_NUMBER);
      if (!moveNumber) throw new PgnSyntaxError(`unexpected input at ${scanner.pos}`);
      number = Number(moveNumber[1]);
      side = moveNumber[2].replace(/\s/g, '').length >= 3 ? 'black' : 'white';
    }

    if (headers.length === 0 && moves.length === 0) return null;
    const headerResult = headers.find((header) => header.name === 'Result')?.value ?? null;
    return { headers, moves, result: result ?? headerResult };
  } catch (error: unknown) {
    if (error instanceof PgnSyntaxError) return null;
    throw error;
  }
}
