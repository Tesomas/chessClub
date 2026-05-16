import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Player {
  id: string;
  firstName: string;
  lastName: string;
  rapidElo: number;
  blitzElo: number;
  bulletElo: number;
  classicalElo: number;
}

export type PlayerCreateDto = Omit<Player, 'id'>;

@Injectable({ providedIn: 'root' })
export class PlayerService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<Player[]> {
    return this.http.get<Player[]>('/api/players');
  }

  create(dto: PlayerCreateDto): Observable<Player> {
    return this.http.post<Player>('/api/players', dto);
  }
}
