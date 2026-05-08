import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./home/home.component').then(m => m.HomeComponent)
  },
  {
    path: 'players',
    loadComponent: () => import('./players/players.component').then(m => m.PlayersComponent)
  }
];
