import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PlayerService } from './player.service';
import { DEFAULT_QUERY, EMPTY_FILTER, Page, PlayerGameView } from './player-game.model';

describe('PlayerService', () => {
  let service: PlayerService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PlayerService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getAll fetches the player list', () => {
    let result: unknown;
    service.getAll().subscribe((players) => (result = players));
    http.expectOne({ method: 'GET', url: '/api/players' }).flush([]);
    expect(result).toEqual([]);
  });

  it('getById fetches a single player', () => {
    let name = '';
    service.getById('p1').subscribe((player) => (name = player.firstName));
    http.expectOne({ method: 'GET', url: '/api/players/p1' }).flush({ id: 'p1', firstName: 'Ann' });
    expect(name).toBe('Ann');
  });

  it('getById encodes the id path segment', () => {
    service.getById('a/b').subscribe();
    http.expectOne('/api/players/a%2Fb').flush({});
  });

  it('getById surfaces a 404', () => {
    let status = 0;
    service.getById('missing').subscribe({ error: (err) => (status = err.status) });
    http
      .expectOne('/api/players/missing')
      .flush({ title: 'Not Found' }, { status: 404, statusText: 'Not Found' });
    expect(status).toBe(404);
  });

  it('create posts the dto including birthDate', () => {
    const dto = {
      firstName: 'Ann',
      lastName: 'Lee',
      rapidElo: 1200,
      blitzElo: 1200,
      bulletElo: 1200,
      classicalElo: 1200,
      birthDate: '2000-02-29',
    };
    service.create(dto).subscribe();
    const req = http.expectOne({ method: 'POST', url: '/api/players' });
    expect(req.request.body).toEqual(dto);
    req.flush({ id: 'p1', ...dto });
  });

  it('getGames sends defaults for an empty filter', () => {
    service.getGames('p1', DEFAULT_QUERY).subscribe();
    const req = http.expectOne((r) => r.url === '/api/players/p1/games');
    expect(req.request.method).toBe('GET');
    expect(req.request.urlWithParams).toBe(
      '/api/players/p1/games?page=0&size=25&sort=date&direction=desc',
    );
    req.flush(emptyPage());
  });

  it('getGames sends set filters and omits unset ones', () => {
    const page: Page<PlayerGameView> = {
      ...emptyPage(),
      totalElements: 1,
      totalPages: 1,
      content: [
        {
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
        },
      ],
    };
    let result: Page<PlayerGameView> | undefined;
    service
      .getGames('p1', {
        ...DEFAULT_QUERY,
        filter: {
          ...EMPTY_FILTER,
          opponentName: 'bob',
          from: '2026-01-01',
        },
        page: 2,
        sort: 'opponentName',
        direction: 'asc',
      })
      .subscribe((p) => (result = p));
    const req = http.expectOne((r) => r.url === '/api/players/p1/games');
    expect(req.request.urlWithParams).toBe(
      '/api/players/p1/games?opponentName=bob&from=2026-01-01' +
        '&page=2&size=25&sort=opponentName&direction=asc',
    );
    req.flush(page);
    expect(result).toEqual(page);
  });
});

function emptyPage(): Page<PlayerGameView> {
  return { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 };
}
