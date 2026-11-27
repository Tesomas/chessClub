import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { PlayersComponent } from './players.component';

describe('PlayersComponent', () => {
  it('links each player name to their profile', async () => {
    TestBed.configureTestingModule({
      imports: [PlayersComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(PlayersComponent);
    await fixture.whenStable();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/players')
      .flush([
        {
          id: 'p1',
          firstName: 'Ann',
          lastName: 'Lee',
          rapidElo: 1,
          blitzElo: 2,
          bulletElo: 3,
          classicalElo: 4,
          birthDate: null,
        },
      ]);
    await fixture.whenStable();
    const link = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
      'td.player-name a',
    );
    expect(link?.textContent?.trim()).toBe('Ann Lee');
    expect(link?.getAttribute('href')).toBe('/players/p1');
  });
});
