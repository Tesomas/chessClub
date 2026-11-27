import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { GamesComponent } from './games.component';

describe('GamesComponent', () => {
  it('links player names to profiles and the date to the game page', async () => {
    TestBed.configureTestingModule({
      imports: [GamesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(GamesComponent);
    await fixture.whenStable();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/games')
      .flush([
        {
          id: 'g1',
          whitePlayerId: 'w1',
          blackPlayerId: 'b1',
          whitePlayerName: 'Ann Lee',
          blackPlayerName: 'Bob Brooks',
          whitePlayerRating: 1500,
          blackPlayerRating: 1480,
          gameType: 'BLITZ',
          result: 'DRAW',
          time: '3+2',
          date: '2026-10-01',
          pgn: null,
        },
      ]);
    await fixture.whenStable();
    const links = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>('a.player-name'),
    ).map((a) => [a.textContent?.trim(), a.getAttribute('href')]);
    expect(links).toEqual([
      ['Ann Lee', '/players/w1'],
      ['Bob Brooks', '/players/b1'],
    ]);
    const gameLink = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
      'td.date-cell a.game-link',
    );
    expect(gameLink?.textContent?.trim()).toBe('2026-10-01');
    expect(gameLink?.getAttribute('href')).toBe('/games/g1');
    expect(gameLink?.getAttribute('aria-label')).toBe(
      'View game on 2026-10-01: Ann Lee vs Bob Brooks',
    );
  });
});
