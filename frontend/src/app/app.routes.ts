import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'players',
    loadComponent: () => import('./players/players.component').then((m) => m.PlayersComponent),
  },
  {
    path: 'players/add',
    loadComponent: () =>
      import('./add-player/add-player.component').then((m) => m.AddPlayerComponent),
  },
  {
    // After 'players/add' so that path isn't captured as an id.
    path: 'players/:id',
    loadComponent: () =>
      import('./player-profile/player-profile.component').then((m) => m.PlayerProfileComponent),
  },
  {
    path: 'games',
    loadComponent: () => import('./games/games.component').then((m) => m.GamesComponent),
  },
  {
    path: 'games/add',
    loadComponent: () => import('./add-game/add-game.component').then((m) => m.AddGameComponent),
  },
  {
    // After 'games/add' so that path isn't captured as an id.
    path: 'games/:id',
    loadComponent: () => import('./game-view/game-view.component').then((m) => m.GameViewComponent),
  },
];
