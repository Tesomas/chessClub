import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, PlayerGameQuery, PlayerGameView } from './player-game.model';
import { queryToHttpParams } from './player-game-query';

export interface Player {
  id: string;
  firstName: string;
  lastName: string;
  rapidElo: number;
  blitzElo: number;
  bulletElo: number;
  classicalElo: number;
  /** ISO `yyyy-MM-dd`, or null when unknown. */
  birthDate: string | null;
}

export type PlayerCreateDto = Omit<Player, 'id'>;

@Injectable({ providedIn: 'root' })
export class PlayerService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<Player[]> {
    return this.http.get<Player[]>('/api/players');
  }

  getById(id: string): Observable<Player> {
    return this.http.get<Player>(`/api/players/${encodeURIComponent(id)}`);
  }

  getGames(id: string, query: PlayerGameQuery): Observable<Page<PlayerGameView>> {
    return this.http.get<Page<PlayerGameView>>(`/api/players/${encodeURIComponent(id)}/games`, {
      params: queryToHttpParams(query),
    });
  }

  create(dto: PlayerCreateDto): Observable<Player> {
    return this.http.post<Player>('/api/players', dto);
  }
}
