import { GameType } from './game.service';

export type PlayerColor = 'WHITE' | 'BLACK';
export type PlayerOutcome = 'WIN' | 'LOSS' | 'DRAW';
export type PlayerGameSort = 'date' | 'opponentName';
export type SortDirection = 'asc' | 'desc';

export const PLAYER_GAME_SORTS: readonly PlayerGameSort[] = ['date', 'opponentName'];
export const SORT_DIRECTIONS: readonly SortDirection[] = ['asc', 'desc'];

export const DEFAULT_PAGE_SIZE = 25;
export const MAX_PAGE_SIZE = 100;
export const MAX_OPPONENT_NAME_LENGTH = 100;
export const DEFAULT_SORT: PlayerGameSort = 'date';
export const DEFAULT_DIRECTION: SortDirection = 'desc';

/** One game seen from a single player's perspective (`GET /api/players/{id}/games`). */
export interface PlayerGameView {
  gameId: string;
  date: string;
  gameType: GameType;
  time: string;
  color: PlayerColor;
  outcome: PlayerOutcome;
  playerRating: number;
  opponentId: string;
  opponentName: string;
  opponentRating: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Server-side filters. `null` means "not set" and is never sent. */
export interface PlayerGameFilter {
  /** Case-insensitive substring of the opponent's name; trimmed, never empty. */
  opponentName: string | null;
  /** ISO `yyyy-MM-dd`, inclusive. */
  from: string | null;
  /** ISO `yyyy-MM-dd`, inclusive. */
  to: string | null;
}

export interface PlayerGameQuery {
  filter: PlayerGameFilter;
  page: number;
  size: number;
  sort: PlayerGameSort;
  direction: SortDirection;
}

export const EMPTY_FILTER: PlayerGameFilter = { opponentName: null, from: null, to: null };

export const DEFAULT_QUERY: PlayerGameQuery = {
  filter: EMPTY_FILTER,
  page: 0,
  size: DEFAULT_PAGE_SIZE,
  sort: DEFAULT_SORT,
  direction: DEFAULT_DIRECTION,
};
