import { HttpParams } from '@angular/common/http';
import { ParamMap, Params } from '@angular/router';
import {
  DEFAULT_QUERY,
  MAX_OPPONENT_NAME_LENGTH,
  MAX_PAGE_SIZE,
  PLAYER_GAME_SORTS,
  PlayerGameFilter,
  PlayerGameQuery,
  SORT_DIRECTIONS,
} from './player-game.model';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const INTEGER = /^-?\d+$/;

export interface FilterErrors {
  dateRange?: true;
}

/** Trimmed, length-capped opponent name; null when blank. */
export function normalizeOpponentName(value: string | null): string | null {
  const trimmed = (value ?? '').trim().slice(0, MAX_OPPONENT_NAME_LENGTH).trim();
  return trimmed || null;
}

/** Filter entries in API/URL order. */
function filterEntries(filter: PlayerGameFilter): [string, string | null][] {
  return [
    ['opponentName', filter.opponentName],
    ['from', filter.from],
    ['to', filter.to],
  ];
}

/** HTTP params for `GET /api/players/{id}/games`; unset filters are omitted. */
export function queryToHttpParams(query: PlayerGameQuery): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of filterEntries(query.filter)) {
    if (value !== null) params = params.set(key, value);
  }
  return params
    .set('page', query.page)
    .set('size', query.size)
    .set('sort', query.sort)
    .set('direction', query.direction);
}

/** Router query params for a query. Unset filters and defaults map to `null` (left out of the URL). */
export function queryToRouteParams(query: PlayerGameQuery): Params {
  const orNull = <T>(value: T, fallback: T): T | null => (value === fallback ? null : value);
  return {
    ...Object.fromEntries(filterEntries(query.filter)),
    page: orNull(query.page, DEFAULT_QUERY.page),
    size: orNull(query.size, DEFAULT_QUERY.size),
    sort: orNull(query.sort, DEFAULT_QUERY.sort),
    direction: orNull(query.direction, DEFAULT_QUERY.direction),
  };
}

function oneOf<T extends string>(allowed: readonly T[], value: string | null): T | null {
  return allowed.find((candidate) => candidate === value) ?? null;
}

function parseInteger(value: string | null): number | null {
  return value !== null && INTEGER.test(value) ? Number(value) : null;
}

function parseDate(value: string | null): string | null {
  return value !== null && ISO_DATE.test(value) ? value : null;
}

function parseSize(value: string | null): number {
  const size = parseInteger(value);
  if (size === null || size < 1) return DEFAULT_QUERY.size;
  return Math.min(size, MAX_PAGE_SIZE);
}

/** Lenient parse of URL query params: unknown or malformed values fall back to defaults. */
export function queryFromParamMap(params: ParamMap): PlayerGameQuery {
  const page = parseInteger(params.get('page'));
  return {
    filter: {
      opponentName: normalizeOpponentName(params.get('opponentName')),
      from: parseDate(params.get('from')),
      to: parseDate(params.get('to')),
    },
    page: page !== null && page > 0 ? page : 0,
    size: parseSize(params.get('size')),
    sort: oneOf(PLAYER_GAME_SORTS, params.get('sort')) ?? DEFAULT_QUERY.sort,
    direction: oneOf(SORT_DIRECTIONS, params.get('direction')) ?? DEFAULT_QUERY.direction,
  };
}

/** Problems the backend would reject with 400; empty object when valid. */
export function filterErrors(filter: PlayerGameFilter): FilterErrors {
  return filter.from && filter.to && filter.from > filter.to ? { dateRange: true } : {};
}

export function isFilterValid(filter: PlayerGameFilter): boolean {
  return Object.keys(filterErrors(filter)).length === 0;
}

export function filtersEqual(a: PlayerGameFilter, b: PlayerGameFilter): boolean {
  return a.opponentName === b.opponentName && a.from === b.from && a.to === b.to;
}
