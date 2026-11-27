const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Whole years between an ISO `yyyy-MM-dd` birth date and `today` (local calendar date).
 * Feb 29 birthdays are reached on Mar 1 in non-leap years.
 * Returns null for a missing, malformed or future birth date.
 */
export function ageFrom(birthDate: string | null, today: Date): number | null {
  const match = birthDate ? ISO_DATE.exec(birthDate) : null;
  if (!match) return null;

  const [year, month, day] = match.slice(1).map(Number);
  const parsed = new Date(year, month - 1, day);
  const isRealDate =
    parsed.getFullYear() === year && parsed.getMonth() === month - 1 && parsed.getDate() === day;
  if (!isRealDate) return null;

  const todayMonth = today.getMonth() + 1;
  const todayDay = today.getDate();
  const hadBirthday = todayMonth > month || (todayMonth === month && todayDay >= day);
  const age = today.getFullYear() - year - (hadBirthday ? 0 : 1);
  return age < 0 ? null : age;
}

/** Local calendar date as ISO `yyyy-MM-dd` (unlike toISOString, which is UTC). */
export function toIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
