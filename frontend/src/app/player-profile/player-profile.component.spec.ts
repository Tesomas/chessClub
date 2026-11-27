import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { PlayerProfileComponent, TODAY } from './player-profile.component';
import { FILTER_DEBOUNCE_MS } from './game-filter/game-filter.component';
import { Player } from '../player.service';
import { Page, PlayerGameView } from '../player-game.model';

const ann: Player = {
  id: 'p1',
  firstName: 'Ann',
  lastName: 'Lee',
  bulletElo: 1310,
  blitzElo: 1420,
  rapidElo: 1530,
  classicalElo: 1640,
  birthDate: '1990-11-27',
};
const bob: Player = { ...ann, id: 'p2', firstName: 'Bob', lastName: 'Brooks', birthDate: null };

const view: PlayerGameView = {
  gameId: 'g1',
  date: '2026-10-01',
  gameType: 'BLITZ',
  time: '3+2',
  color: 'WHITE',
  outcome: 'WIN',
  playerRating: 1500,
  opponentId: 'p2',
  opponentName: 'Bob Brooks',
  opponentRating: 1480,
};

function pageOf(
  content: PlayerGameView[],
  overrides: Partial<Page<PlayerGameView>> = {},
): Page<PlayerGameView> {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1, ...overrides };
}

describe('PlayerProfileComponent', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'players/:id', component: PlayerProfileComponent }]),
        { provide: TODAY, useValue: () => new Date(2026, 10, 26) },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    el = harness.routeNativeElement ?? document.createElement('div');
  });

  afterEach(() => http.verify());

  const gamesRequest = (): TestRequest =>
    http.expectOne((r) => r.url.endsWith('/games') && r.method === 'GET');

  async function stable(): Promise<void> {
    await harness.fixture.whenStable();
    el = harness.routeNativeElement as HTMLElement;
  }

  async function open(
    url: string,
    player: Player = ann,
    games: Page<PlayerGameView> = pageOf([view]),
  ): Promise<TestRequest> {
    await harness.navigateByUrl(url);
    http.expectOne(`/api/players/${player.id}`).flush(player);
    const req = gamesRequest();
    req.flush(games);
    await stable();
    return req;
  }

  const text = (selector: string) =>
    harness.routeNativeElement?.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();

  it('renders the header with placeholder avatar, name, age and ratings', async () => {
    await open('/players/p1');
    const avatar = el.querySelector('.avatar');
    expect(avatar?.getAttribute('role')).toBe('img');
    expect(avatar?.getAttribute('aria-label')).toContain('placeholder');
    expect(text('.avatar')).toBe('AL');
    expect(text('h1')).toBe('Ann Lee');
    expect(text('.age')).toBe('Age 35');
    const tiles = Array.from(el.querySelectorAll('.elo-tile')).map(
      (t) =>
        `${t.querySelector('dt')?.textContent?.trim()} ${t.querySelector('dd')?.textContent?.trim()}`,
    );
    expect(tiles).toEqual(['Bullet 1310', 'Blitz 1420', 'Rapid 1530', 'Classical 1640']);
  });

  it('hides the age when the birth date is unknown', async () => {
    await open('/players/p2', bob);
    expect(text('h1')).toBe('Bob Brooks');
    expect(el.querySelector('.age')).toBeNull();
  });

  it('shows a loading state until the player arrives', async () => {
    await harness.navigateByUrl('/players/p1');
    expect(text('.state-msg')).toContain('Loading player');
    http.expectOne('/api/players/p1').flush(ann);
    gamesRequest().flush(pageOf([]));
  });

  it('requests games with the default query', async () => {
    const req = await open('/players/p1');
    expect(req.request.urlWithParams).toBe(
      '/api/players/p1/games?page=0&size=25&sort=date&direction=desc',
    );
    expect(text('tbody tr')).toContain('Bob Brooks');
  });

  it('derives the games request from the URL query params', async () => {
    const req = await open(
      '/players/p1?opponentName=Bob%20B&from=2026-01-01&to=2026-06-30&page=2&sort=opponentName&direction=asc',
    );
    expect(req.request.urlWithParams).toBe(
      '/api/players/p1/games?opponentName=Bob%20B&from=2026-01-01&to=2026-06-30' +
        '&page=2&size=25&sort=opponentName&direction=asc',
    );
    expect(el.querySelector<HTMLInputElement>('#filter-opponent-name')?.value).toBe('Bob B');
    expect(el.querySelector<HTMLInputElement>('#filter-from')?.value).toBe('2026-01-01');
    expect(el.querySelector('th[data-sort="opponentName"]')?.getAttribute('aria-sort')).toBe(
      'ascending',
    );
  });

  it('does not load the full player list', async () => {
    await open('/players/p1');
    http.expectNone('/api/players');
  });

  it('shows a not-found state for an unknown player', async () => {
    await harness.navigateByUrl('/players/nope');
    http
      .expectOne('/api/players/nope')
      .flush({ title: 'Not Found', status: 404 }, { status: 404, statusText: 'Not Found' });
    await stable();
    http.expectNone((r) => r.url.endsWith('/games'));
    expect(text('h1')).toBe('Player not found');
    expect(el.querySelector('a[href="/players"]')).not.toBeNull();
    expect(el.querySelector('app-game-filter')).toBeNull();
  });

  it('shows an error state when the player cannot be loaded', async () => {
    await harness.navigateByUrl('/players/p1');
    http.expectOne('/api/players/p1').flush(null, { status: 500, statusText: 'Server Error' });
    await stable();
    http.expectNone((r) => r.url.endsWith('/games'));
    expect(text('.state-error')).toContain('Could not load this player');
  });

  it('shows the server problem detail when the games request is rejected', async () => {
    await harness.navigateByUrl('/players/p1');
    http.expectOne('/api/players/p1').flush(ann);
    gamesRequest().flush(
      { title: 'Bad Request', status: 400, detail: 'size must be at most 100' },
      { status: 400, statusText: 'Bad Request' },
    );
    await stable();
    expect(text('.games-error')).toBe('size must be at most 100');
  });

  it('shows a generic message when the games request fails without detail', async () => {
    await harness.navigateByUrl('/players/p1');
    http.expectOne('/api/players/p1').flush(ann);
    gamesRequest().flush(null, { status: 502, statusText: 'Bad Gateway' });
    await stable();
    expect(text('.games-error')).toContain('Could not load games');
  });

  it('does not request games for an invalid range in the URL', async () => {
    await harness.navigateByUrl('/players/p1?from=2026-05-01&to=2026-04-01');
    http.expectOne('/api/players/p1').flush(ann);
    http.expectNone((r) => r.url.endsWith('/games'));
    await stable();
    expect(text('.games-error')).toContain('invalid');
  });

  it('shows the empty state when no games match', async () => {
    await open('/players/p1', ann, pageOf([], { totalPages: 0 }));
    expect(text('.empty')).toContain('No games match');
  });

  it('writes page changes to the URL and refetches', async () => {
    await open('/players/p1', ann, pageOf([view], { totalElements: 60, totalPages: 3 }));
    el.querySelector<HTMLButtonElement>('.pager-next')?.click();
    await stable();
    expect(TestBed.inject(Router).url).toBe('/players/p1?page=1');
    expect(gamesRequest().request.params.get('page')).toBe('1');
  });

  it('writes sort changes to the URL and returns to the first page', async () => {
    await open('/players/p1?page=2', ann, pageOf([view], { page: 2, totalPages: 3 }));
    el.querySelector<HTMLButtonElement>('th[data-sort="opponentName"] button')?.click();
    await stable();
    expect(TestBed.inject(Router).url).toBe('/players/p1?sort=opponentName&direction=asc');
    const req = gamesRequest();
    expect(req.request.params.get('sort')).toBe('opponentName');
    expect(req.request.params.get('direction')).toBe('asc');
    expect(req.request.params.get('page')).toBe('0');
  });

  it('writes date filter changes to the URL and resets the page', async () => {
    await open('/players/p1?page=3&sort=opponentName', ann, pageOf([view], { page: 3 }));
    const from = el.querySelector<HTMLInputElement>('#filter-from');
    if (!from) throw new Error('missing #filter-from');
    from.value = '2026-02-01';
    from.dispatchEvent(new Event('input'));
    await stable();
    expect(TestBed.inject(Router).url).toBe('/players/p1?from=2026-02-01&sort=opponentName');
    const req = gamesRequest();
    expect(req.request.params.get('from')).toBe('2026-02-01');
    expect(req.request.params.get('page')).toBe('0');
  });

  it('writes the debounced, trimmed opponent name to the URL', async () => {
    await open('/players/p1?page=1', ann, pageOf([view], { page: 1, totalPages: 2 }));
    const name = el.querySelector<HTMLInputElement>('#filter-opponent-name');
    if (!name) throw new Error('missing #filter-opponent-name');
    name.value = '  bob ';
    name.dispatchEvent(new Event('input'));
    http.expectNone((r) => r.url.endsWith('/games'));
    await new Promise((resolve) => setTimeout(resolve, FILTER_DEBOUNCE_MS + 50));
    await stable();
    expect(TestBed.inject(Router).url).toBe('/players/p1?opponentName=bob');
    expect(gamesRequest().request.params.get('opponentName')).toBe('bob');
  });

  it('reset clears the filters from the URL', async () => {
    await open('/players/p1?opponentName=bob&to=2026-01-01');
    el.querySelector<HTMLButtonElement>('button[type="reset"]')?.click();
    await stable();
    expect(TestBed.inject(Router).url).toBe('/players/p1');
    gamesRequest().flush(pageOf([]));
  });

  it('does not request games before the player has loaded', async () => {
    await harness.navigateByUrl('/players/p1?opponentName=bob');
    http.expectNone((r) => r.url.endsWith('/games'));
    http.expectOne('/api/players/p1').flush(ann);
    expect(gamesRequest().request.params.get('opponentName')).toBe('bob');
  });

  it('keeps typed opponent text when paging during the debounce window', async () => {
    await open('/players/p1', ann, pageOf([view], { totalElements: 60, totalPages: 3 }));
    const name = el.querySelector<HTMLInputElement>('#filter-opponent-name');
    if (!name) throw new Error('missing #filter-opponent-name');
    name.value = 'x';
    name.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('.pager-next')?.click();
    await stable();
    expect(TestBed.inject(Router).url).toBe('/players/p1?page=1');
    gamesRequest().flush(pageOf([view], { page: 1, totalElements: 60, totalPages: 3 }));
    await stable();
    expect(name.value).toBe('x');

    await new Promise((resolve) => setTimeout(resolve, FILTER_DEBOUNCE_MS + 50));
    await stable();
    expect(TestBed.inject(Router).url).toBe('/players/p1?opponentName=x');
    expect(gamesRequest().request.params.get('opponentName')).toBe('x');
  });

  it('switching to another profile shows none of the previous rows and sends one request', async () => {
    const annGame = { ...view, gameId: 'a1', opponentName: 'Only Ann Played Me' };
    await open(
      '/players/p1?page=2&opponentName=only',
      ann,
      pageOf([annGame], { page: 2, totalPages: 3 }),
    );
    expect(text('tbody')).toContain('Only Ann Played Me');

    await harness.navigateByUrl('/players/p2?sort=opponentName');
    http.expectNone((r) => r.url.endsWith('/games'));
    http.expectOne('/api/players/p2').flush(bob);
    await stable();
    expect(text('h1')).toBe('Bob Brooks');
    expect(el.textContent).not.toContain('Only Ann Played Me');

    const requests = http.match((r) => r.url.endsWith('/games'));
    expect(requests.map((r) => r.request.urlWithParams)).toEqual([
      '/api/players/p2/games?page=0&size=25&sort=opponentName&direction=desc',
    ]);
    requests[0].flush(pageOf([{ ...view, gameId: 'b1', opponentName: 'Ann Lee' }]));
    await stable();
    expect(text('tbody')).toContain('Ann Lee');
    expect(el.textContent).not.toContain('Only Ann Played Me');
  });

  it('keeps the pager status element while the next page loads', async () => {
    await open('/players/p1', ann, pageOf([view], { totalElements: 60, totalPages: 3 }));
    const status = el.querySelector('.pager-status');
    el.querySelector<HTMLButtonElement>('.pager-next')?.click();
    await stable();
    gamesRequest().flush(pageOf([view], { page: 1, totalElements: 60, totalPages: 3 }));
    await stable();
    expect(el.querySelector('.pager-status')).toBe(status);
    expect(status?.textContent).toContain('Page 2 of 3');
  });
});
