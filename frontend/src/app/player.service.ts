import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Player {
  id: string;
  firstName: string;
  lastName: string;
  eloRapid: number;
  eloBlitz: number;
  eloBullet: number;
  eloClassical: number;
}

@Injectable({ providedIn: 'root' })
export class PlayerService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<Player[]> {
    return this.http.get<Player[]>('/api/players');
  }
}
