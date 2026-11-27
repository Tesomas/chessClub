import { convertToParamMap } from '@angular/router';
import {
  filterErrors,
  filtersEqual,
  isFilterValid,
  normalizeOpponentName,
  queryFromParamMap,
  queryToHttpParams,
  queryToRouteParams,
} from './player-game-query';
import { DEFAULT_QUERY, EMPTY_FILTER, PlayerGameQuery } from './player-game.model';

const fullQuery: PlayerGameQuery = {
  filter: { opponentName: 'bob', from: '2026-01-01', to: '2026-06-30' },
  page: 3,
  size: 50,
  sort: 'opponentName',
  direction: 'asc',
};

describe('normalizeOpponentName', () => {
  it('trims and keeps inner spaces', () => {
    expect(normalizeOpponentName('  Bob  Brooks ')).toBe('Bob  Brooks');
  });

  it('maps empty, blank and null to null', () => {
    expect(normalizeOpponentName('')).toBeNull();
    expect(normalizeOpponentName('   ')).toBeNull();
    expect(normalizeOpponentName(null)).toBeNull();
  });

  it('caps the length at 100 characters', () => {
    expect(normalizeOpponentName('x'.repeat(150))).toBe('x'.repeat(100));
  });
});

describe('queryToHttpParams', () => {
  it('sends only paging and sort params for the default query', () => {
    expect(queryToHttpParams(DEFAULT_QUERY).toString()).toBe(
      'page=0&size=25&sort=date&direction=desc',
    );
  });

  it('includes every set filter', () => {
    expect(queryToHttpParams(fullQuery).toString()).toBe(
      'opponentName=bob&from=2026-01-01&to=2026-06-30&page=3&size=50&sort=opponentName&direction=asc',
    );
  });

  it('omits unset filters entirely', () => {
    const params = queryToHttpParams({
      ...DEFAULT_QUERY,
      filter: { ...EMPTY_FILTER, to: '2026-03-01' },
    });
    expect(params.keys()).toEqual(['to', 'page', 'size', 'sort', 'direction']);
  });

  it('encodes special characters in the opponent name', () => {
    const params = queryToHttpParams({
      ...DEFAULT_QUERY,
      filter: { ...EMPTY_FILTER, opponentName: "O'Neil & Co" },
    });
    expect(params.get('opponentName')).toBe("O'Neil & Co");
    expect(params.toString()).toContain("opponentName=O'Neil%20%26%20Co");
  });
});

describe('queryToRouteParams', () => {
  it('maps defaults and unset filters to null', () => {
    expect(queryToRouteParams(DEFAULT_QUERY)).toEqual({
      opponentName: null,
      from: null,
      to: null,
      page: null,
      size: null,
      sort: null,
      direction: null,
    });
  });

  it('serializes every non-default value', () => {
    expect(queryToRouteParams(fullQuery)).toEqual({
      opponentName: 'bob',
      from: '2026-01-01',
      to: '2026-06-30',
      page: 3,
      size: 50,
      sort: 'opponentName',
      direction: 'asc',
    });
  });
});

describe('queryFromParamMap', () => {
  it('returns the default query for no params', () => {
    expect(queryFromParamMap(convertToParamMap({}))).toEqual(DEFAULT_QUERY);
  });

  it('round-trips a full query through route params', () => {
    const asStrings = Object.fromEntries(
      Object.entries(queryToRouteParams(fullQuery)).map(([k, v]) => [k, String(v)]),
    );
    expect(queryFromParamMap(convertToParamMap(asStrings))).toEqual(fullQuery);
  });

  it('normalizes the opponent name from the URL', () => {
    expect(
      queryFromParamMap(convertToParamMap({ opponentName: '  ann ' })).filter.opponentName,
    ).toBe('ann');
    expect(
      queryFromParamMap(convertToParamMap({ opponentName: '   ' })).filter.opponentName,
    ).toBeNull();
  });

  it('ignores garbage values instead of failing', () => {
    const query = queryFromParamMap(
      convertToParamMap({
        from: '01/02/2026',
        to: 'tomorrow',
        page: '-2',
        size: '500',
        sort: 'playerElo',
        direction: 'sideways',
      }),
    );
    expect(query).toEqual({ ...DEFAULT_QUERY, size: 100 });
  });

  it('falls back to the default size for a zero or non-numeric size', () => {
    expect(queryFromParamMap(convertToParamMap({ size: '0' })).size).toBe(25);
    expect(queryFromParamMap(convertToParamMap({ size: 'ten' })).size).toBe(25);
  });

  it('falls back to page 0 for a non-numeric page', () => {
    expect(queryFromParamMap(convertToParamMap({ page: '1.5' })).page).toBe(0);
  });
});

describe('filterErrors', () => {
  it('has no errors for an empty filter or a single bound', () => {
    expect(filterErrors(EMPTY_FILTER)).toEqual({});
    expect(filterErrors({ ...EMPTY_FILTER, from: '2026-05-01' })).toEqual({});
  });

  it('accepts equal dates', () => {
    expect(filterErrors({ ...EMPTY_FILTER, from: '2026-01-01', to: '2026-01-01' })).toEqual({});
  });

  it('flags an inverted date range', () => {
    const filter = { ...EMPTY_FILTER, from: '2026-02-01', to: '2026-01-01' };
    expect(filterErrors(filter)).toEqual({ dateRange: true });
    expect(isFilterValid(filter)).toBe(false);
  });
});

describe('filtersEqual', () => {
  it('compares every field', () => {
    expect(filtersEqual(EMPTY_FILTER, { ...EMPTY_FILTER })).toBe(true);
    expect(filtersEqual(EMPTY_FILTER, { ...EMPTY_FILTER, opponentName: 'a' })).toBe(false);
    expect(filtersEqual(EMPTY_FILTER, { ...EMPTY_FILTER, from: '2026-01-01' })).toBe(false);
    expect(filtersEqual(EMPTY_FILTER, { ...EMPTY_FILTER, to: '2026-01-01' })).toBe(false);
  });
});
