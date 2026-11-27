import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { AddPlayerComponent } from './add-player/add-player.component';
import { PlayerProfileComponent } from './player-profile/player-profile.component';
import { GameViewComponent } from './game-view/game-view.component';
import { AddGameComponent } from './add-game/add-game.component';

describe('app routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    });
  });

  it('routes /players/:id to the profile page', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/players/p1');
    expect(component).toBeInstanceOf(PlayerProfileComponent);
  });

  it('keeps /players/add on the add-player form', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/players/add');
    expect(component).toBeInstanceOf(AddPlayerComponent);
  });

  it('routes /games/:id to the game view', async () => {
    const harness = await RouterTestingHarness.create();
    expect(await harness.navigateByUrl('/games/g1')).toBeInstanceOf(GameViewComponent);
  });

  it('keeps /games/add on the add-game form', async () => {
    const harness = await RouterTestingHarness.create();
    expect(await harness.navigateByUrl('/games/add')).toBeInstanceOf(AddGameComponent);
  });
});
