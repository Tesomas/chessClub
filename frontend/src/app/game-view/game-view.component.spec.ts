import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { GameViewComponent } from './game-view.component';
import { CLIPBOARD } from './pgn-panel/pgn-panel.component';
import { Game, GameResult } from '../game.service';

const PGN = [
  '[Event "Club night"]',
  '[White "Lee, Ann"]',
  '[Black "Brooks, Bob"]',
  '',
  '1. e4 {best by test} e5 2. Nf3 (2. f4 exf4) Nc6 $1 3. Bb5 a6 1-0',
].join('\r\n');

function aGame(overrides: Partial<Game> = {}): Game {
  return {
    id: 'g1',
    whitePlayerId: 'w1',
    blackPlayerId: 'b1',
    whitePlayerName: 'Ann Lee',
    blackPlayerName: 'Bob Brooks',
    whitePlayerRating: 1510,
    blackPlayerRating: 1475,
    gameType: 'BLITZ',
    result: 'WHITE_WINS',
    time: '3+2',
    date: '2026-10-01',
    pgn: PGN,
    ...overrides,
  };
}

describe('GameViewComponent', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let writeText: ReturnType<typeof vi.fn<(text: string) => Promise<void>>>;

  async function setup(clipboard: { writeText: (text: string) => Promise<void> } | null) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'games/:id', component: GameViewComponent }]),
        { provide: CLIPBOARD, useValue: clipboard },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  }

  beforeEach(async () => {
    writeText = vi.fn<(text: string) => Promise<void>>().mockResolvedValue(undefined);
    await setup({ writeText });
  });

  afterEach(() => http.verify());

  const root = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const text = (selector: string) =>
    root().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
  const all = (selector: string) =>
    Array.from(root().querySelectorAll(selector)).map((n) =>
      n.textContent?.replace(/\s+/g, ' ').trim(),
    );
  const gameRequest = (id: string): TestRequest => http.expectOne(`/api/games/${id}`);

  async function open(game: Game = aGame()): Promise<void> {
    await harness.navigateByUrl(`/games/${game.id}`);
    gameRequest(game.id).flush(game);
    await harness.fixture.whenStable();
  }

  describe('game details', () => {
    it('shows a loading state until the game arrives', async () => {
      await harness.navigateByUrl('/games/g1');
      expect(text('.state-msg')).toContain('Loading game');
      gameRequest('g1').flush(aGame());
    });

    it('renders a card per side with avatar, linked name, color and rating', async () => {
      await open();
      const cards = Array.from(root().querySelectorAll('.player-card'));
      expect(cards).toHaveLength(2);

      const [white, black] = cards;
      expect(white.querySelector('.color-label')?.textContent?.trim()).toBe('White');
      expect(black.querySelector('.color-label')?.textContent?.trim()).toBe('Black');
      expect(white.querySelector('.avatar')?.textContent?.trim()).toBe('AL');
      // decorative next to the name link, so the name isn't announced twice
      for (const card of cards) {
        const avatar = card.querySelector('.avatar');
        expect(avatar?.getAttribute('aria-hidden')).toBe('true');
        expect(avatar?.hasAttribute('role')).toBe(false);
        expect(avatar?.hasAttribute('aria-label')).toBe(false);
      }
      expect(black.querySelector('.avatar')?.textContent?.trim()).toBe('BB');

      const whiteLink = white.querySelector<HTMLAnchorElement>('a.player-link');
      expect(whiteLink?.textContent?.trim()).toBe('Ann Lee');
      expect(whiteLink?.getAttribute('href')).toBe('/players/w1');
      expect(black.querySelector('a.player-link')?.getAttribute('href')).toBe('/players/b1');

      expect(white.querySelector('.rating')?.textContent).toContain('1510');
      expect(black.querySelector('.rating')?.textContent).toContain('1475');
      expect(white.querySelector('.rating')?.textContent).toContain('at game time');
    });

    it.each<[GameResult, string, string]>([
      ['WHITE_WINS', 'W', 'L'],
      ['BLACK_WINS', 'L', 'W'],
      ['DRAW', 'D', 'D'],
    ])('shows outcome badges for %s (white %s, black %s)', async (result, white, black) => {
      await open(aGame({ result }));
      expect(all('.player-card .badge')).toEqual([white, black]);
    });

    it('shows mode, time control, date and result label', async () => {
      await open(aGame({ gameType: 'CLASSICAL', time: '90+30', result: 'DRAW' }));
      const meta = Array.from(root().querySelectorAll('.meta > div')).map((row) => [
        row.querySelector('dt')?.textContent?.trim(),
        row.querySelector('dd')?.textContent?.trim(),
      ]);
      expect(meta).toEqual([
        ['Mode', 'Classical'],
        ['Time control', '90+30'],
        ['Date', '2026-10-01'],
        ['Result', '½-½'],
      ]);
      expect(text('h1')).toBe('Ann Lee vs Bob Brooks');
    });

    it('shows a not-found state for a 404 with an empty body', async () => {
      await harness.navigateByUrl('/games/nope');
      gameRequest('nope').flush(null, { status: 404, statusText: 'Not Found' });
      await harness.fixture.whenStable();
      expect(text('h1')).toBe('Game not found');
      expect(root().querySelector('a[href="/games"]')).not.toBeNull();
      expect(root().querySelector('.player-card')).toBeNull();
    });

    it('shows an error state for other failures', async () => {
      await harness.navigateByUrl('/games/g1');
      gameRequest('g1').flush(null, { status: 500, statusText: 'Server Error' });
      await harness.fixture.whenStable();
      expect(text('.state-error')).toContain('Could not load this game');
    });
  });

  describe('navigating between games', () => {
    it('never shows the previous game and sends exactly one request per id', async () => {
      await open(aGame({ id: 'g1', whitePlayerName: 'Old White' }));
      expect(text('h1')).toContain('Old White');

      await harness.navigateByUrl('/games/g2');
      expect(root().textContent).not.toContain('Old White');
      expect(text('.state-msg')).toContain('Loading game');

      const requests = http.match((r) => r.url.startsWith('/api/games/'));
      expect(requests.map((r) => r.request.url)).toEqual(['/api/games/g2']);
      requests[0].flush(aGame({ id: 'g2', whitePlayerName: 'New White' }));
      await harness.fixture.whenStable();
      expect(text('h1')).toContain('New White');
      expect(root().textContent).not.toContain('Old White');
    });

    it('cancels the stale request when the id changes before it returns', async () => {
      await harness.navigateByUrl('/games/g1');
      const stale = gameRequest('g1');
      await harness.navigateByUrl('/games/g2');
      expect(stale.cancelled).toBe(true);
      gameRequest('g2').flush(aGame({ id: 'g2', whitePlayerName: 'Fresh' }));
      await harness.fixture.whenStable();
      expect(text('h1')).toContain('Fresh');
    });

    it('does not refetch when only the query string changes', async () => {
      await open();
      await harness.navigateByUrl('/games/g1?tab=pgn');
      http.expectNone('/api/games/g1');
    });
  });

  describe('PGN panel', () => {
    it('shows headers as a definition list and moves as numbered pairs', async () => {
      await open();
      const tags = Array.from(root().querySelectorAll('.tags > div')).map((row) => [
        row.querySelector('dt')?.textContent?.trim(),
        row.querySelector('dd')?.textContent?.trim(),
      ]);
      expect(tags).toEqual([
        ['Event', 'Club night'],
        ['White', 'Lee, Ann'],
        ['Black', 'Brooks, Bob'],
      ]);
      expect(all('.moves li')).toEqual(['1. e4 e5', '2. Nf3 Nc6', '3. Bb5 a6']);
      expect(text('.pgn-result')).toBe('1-0');
    });

    it('marks a missing white move when the game starts with black', async () => {
      await open(aGame({ pgn: '5... Qxd5 6. Nc3' }));
      expect(all('.moves li')).toEqual(['5. … Qxd5', '6. Nc3']);
    });

    it.each([null, '', '   \n '])('shows the empty state for pgn %j', async (pgn) => {
      await open(aGame({ pgn }));
      expect(text('.pgn-empty')).toBe('No PGN recorded');
      expect(root().querySelector('.copy-btn')).toBeNull();
      expect(root().querySelector('pre')).toBeNull();
    });

    it('falls back to the raw text, rendered as text, for unparseable PGN', async () => {
      const raw = 'we drew <b>after</b> a long fight';
      await open(aGame({ pgn: raw }));
      const pre = root().querySelector('pre.pgn-raw');
      expect(pre?.textContent).toBe(raw);
      expect(pre?.querySelector('b')).toBeNull();
      expect(root().querySelector('.moves')).toBeNull();
      expect(text('.pgn-fallback')).toContain("Couldn't read");
    });

    it('renders header values as text, never as HTML', async () => {
      await open(aGame({ pgn: '[Event "<img src=x onerror=alert(1)>"]\n1. e4' }));
      const dd = root().querySelector('.tags dd');
      expect(dd?.textContent).toBe('<img src=x onerror=alert(1)>');
      expect(dd?.querySelector('img')).toBeNull();
    });

    it('copies the raw PGN and reports success', async () => {
      await open();
      root().querySelector<HTMLButtonElement>('.copy-btn')?.click();
      await harness.fixture.whenStable();
      expect(writeText).toHaveBeenCalledWith(PGN);
      expect(text('.copy-status')).toBe('Copied to clipboard');
    });

    it('reports a failed copy', async () => {
      writeText.mockRejectedValue(new Error('denied'));
      await open();
      root().querySelector<HTMLButtonElement>('.copy-btn')?.click();
      await harness.fixture.whenStable();
      expect(text('.copy-status')).toBe('Copy failed — select the text and copy it manually');
    });

    it('clears the copy feedback when moving to another game', async () => {
      await open();
      root().querySelector<HTMLButtonElement>('.copy-btn')?.click();
      await harness.fixture.whenStable();
      await harness.navigateByUrl('/games/g2');
      gameRequest('g2').flush(aGame({ id: 'g2', pgn: '1. d4' }));
      await harness.fixture.whenStable();
      expect(text('.copy-status')).toBe('');
    });
  });
});

describe('GameViewComponent without a clipboard API', () => {
  it('reports a failed copy instead of throwing', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'games/:id', component: GameViewComponent }]),
        { provide: CLIPBOARD, useValue: null },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create('/games/g1');
    http.expectOne('/api/games/g1').flush(aGame());
    await harness.fixture.whenStable();
    const root = harness.routeNativeElement as HTMLElement;
    root.querySelector<HTMLButtonElement>('.copy-btn')?.click();
    await harness.fixture.whenStable();
    expect(root.querySelector('.copy-status')?.textContent?.trim()).toContain('Copy failed');
    http.verify();
  });
});
