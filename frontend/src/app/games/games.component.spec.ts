import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { GamesComponent } from './games.component';

describe('GamesComponent', () => {
  it('links white and black player names to their profiles', async () => {
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
          whitePlayerRatingChange: -1,
          blackPlayerRatingChange: 1,
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
    const changes = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.rating-change'),
    ).map((el) => [el.textContent?.trim(), el.classList.contains('gain'), el.classList.contains('loss')]);
    expect(changes).toEqual([
      ['−1', false, true],
      ['+1', true, false],
    ]);
  });
});
