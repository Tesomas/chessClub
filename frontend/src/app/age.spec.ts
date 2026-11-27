import { ageFrom, toIsoDate } from './age';

describe('ageFrom', () => {
  const on = (iso: string) => {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d);
  };

  it('returns null when birth date is null', () => {
    expect(ageFrom(null, on('2026-11-26'))).toBeNull();
  });

  it('returns null for an empty or malformed date', () => {
    expect(ageFrom('', on('2026-11-26'))).toBeNull();
    expect(ageFrom('not-a-date', on('2026-11-26'))).toBeNull();
    expect(ageFrom('2000-13-40', on('2026-11-26'))).toBeNull();
  });

  it('counts the birthday itself as a completed year', () => {
    expect(ageFrom('1990-11-26', on('2026-11-26'))).toBe(36);
  });

  it('has not yet had the birthday the day before', () => {
    expect(ageFrom('1990-11-27', on('2026-11-26'))).toBe(35);
  });

  it('handles a birthday later in the year', () => {
    expect(ageFrom('1990-12-01', on('2026-01-15'))).toBe(35);
  });

  it('handles a birthday earlier in the year', () => {
    expect(ageFrom('1990-02-01', on('2026-11-26'))).toBe(36);
  });

  it('treats a Feb 29 birthday as not reached on Feb 28 of a non-leap year', () => {
    expect(ageFrom('2000-02-29', on('2025-02-28'))).toBe(24);
  });

  it('treats a Feb 29 birthday as reached on Mar 1 of a non-leap year', () => {
    expect(ageFrom('2000-02-29', on('2025-03-01'))).toBe(25);
  });

  it('treats a Feb 29 birthday as reached on Feb 29 of a leap year', () => {
    expect(ageFrom('2000-02-29', on('2024-02-29'))).toBe(24);
    expect(ageFrom('2000-02-29', on('2024-02-28'))).toBe(23);
  });

  it('returns 0 for a baby born today', () => {
    expect(ageFrom('2026-11-26', on('2026-11-26'))).toBe(0);
  });

  it('returns null for a birth date in the future', () => {
    expect(ageFrom('2026-11-27', on('2026-11-26'))).toBeNull();
  });
});

describe('toIsoDate', () => {
  it('formats the local calendar date with zero padding', () => {
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(toIsoDate(new Date(2026, 10, 26, 23, 59))).toBe('2026-11-26');
  });
});
