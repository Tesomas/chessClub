import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type GameType = 'RAPID' | 'BLITZ' | 'BULLET' | 'CLASSICAL';
export type GameResult = 'WHITE_WINS' | 'BLACK_WINS' | 'DRAW';

export interface Game {
  id: string;
  whitePlayerId: string;
  blackPlayerId: string;
  whitePlayerName: string;
  blackPlayerName: string;
  whitePlayerRating: number;
  blackPlayerRating: number;
  gameType: GameType;
  result: GameResult;
  time: string;
  date: string;
  pgn: string | null;
}

export interface GameCreateDto {
  whitePlayerId: string;
  blackPlayerId: string;
  gameType: GameType;
  result: GameResult;
  time: string;
  date: string;
  pgn: string | null;
}

export const RESULT_LABEL: Record<GameResult, string> = {
  WHITE_WINS: '1-0',
  BLACK_WINS: '0-1',
  DRAW: '½-½',
};

@Injectable({ providedIn: 'root' })
export class GameService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<Game[]> {
    return this.http.get<Game[]>('/api/games');
  }

  create(dto: GameCreateDto): Observable<Game> {
    return this.http.post<Game>('/api/games', dto);
  }
}
