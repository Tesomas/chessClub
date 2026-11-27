import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GameService } from './game.service';

describe('GameService', () => {
  let service: GameService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GameService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getAll fetches the game list', () => {
    service.getAll().subscribe();
    http.expectOne({ method: 'GET', url: '/api/games' }).flush([]);
  });

  it('getById fetches a single game', () => {
    let id = '';
    service.getById('g1').subscribe((game) => (id = game.id));
    http.expectOne({ method: 'GET', url: '/api/games/g1' }).flush({ id: 'g1' });
    expect(id).toBe('g1');
  });

  it('getById encodes the id path segment', () => {
    service.getById('a/b?c').subscribe();
    http.expectOne('/api/games/a%2Fb%3Fc').flush({});
  });

  it('getById surfaces a 404 with an empty body', () => {
    let status = 0;
    service.getById('missing').subscribe({ error: (err) => (status = err.status) });
    http.expectOne('/api/games/missing').flush(null, { status: 404, statusText: 'Not Found' });
    expect(status).toBe(404);
  });

  it('create posts the dto', () => {
    const dto = {
      whitePlayerId: 'w',
      blackPlayerId: 'b',
      gameType: 'BLITZ' as const,
      result: 'DRAW' as const,
      time: '3+2',
      date: '2026-10-01',
      pgn: null,
    };
    service.create(dto).subscribe();
    const req = http.expectOne({ method: 'POST', url: '/api/games' });
    expect(req.request.body).toEqual(dto);
    req.flush({});
  });
});
