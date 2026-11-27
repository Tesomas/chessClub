import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FILTER_DEBOUNCE_MS, GameFilterComponent } from './game-filter.component';
import { EMPTY_FILTER, PlayerGameFilter } from '../../player-game.model';

describe('GameFilterComponent', () => {
  let fixture: ComponentFixture<GameFilterComponent>;
  let el: HTMLElement;
  let emitted: PlayerGameFilter[];

  async function create(filter: PlayerGameFilter = EMPTY_FILTER): Promise<void> {
    fixture = TestBed.createComponent(GameFilterComponent);
    fixture.componentRef.setInput('filter', filter);
    el = fixture.nativeElement;
    emitted = [];
    fixture.componentInstance.filterChange.subscribe((f) => emitted.push(f));
    await fixture.whenStable();
  }

  function query<T extends HTMLElement>(selector: string): T {
    const found = el.querySelector<T>(selector);
    if (!found) throw new Error(`missing ${selector}`);
    return found;
  }

  function setValue(selector: string, value: string): void {
    const input = query<HTMLInputElement>(selector);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [GameFilterComponent] });
  });

  afterEach(() => vi.useRealTimers());

  it('renders only the opponent name and date range fields', async () => {
    await create();
    const ids = Array.from(el.querySelectorAll('input, select')).map((i) => i.id);
    expect(ids).toEqual(['filter-opponent-name', 'filter-from', 'filter-to']);
    expect(query<HTMLInputElement>('#filter-opponent-name').maxLength).toBe(100);
  });

  it('debounces the opponent name and emits it trimmed', async () => {
    await create();
    vi.useFakeTimers();
    setValue('#filter-opponent-name', 'b');
    setValue('#filter-opponent-name', 'bo');
    setValue('#filter-opponent-name', '  bob ');
    vi.advanceTimersByTime(FILTER_DEBOUNCE_MS - 1);
    expect(emitted).toEqual([]);
    vi.advanceTimersByTime(1);
    expect(emitted).toEqual([{ ...EMPTY_FILTER, opponentName: 'bob' }]);
  });

  it('does not emit when only surrounding whitespace changes', async () => {
    await create({ ...EMPTY_FILTER, opponentName: 'bob' });
    vi.useFakeTimers();
    setValue('#filter-opponent-name', 'bob   ');
    vi.advanceTimersByTime(FILTER_DEBOUNCE_MS);
    expect(emitted).toEqual([]);
  });

  it('emits null when the opponent name is cleared', async () => {
    await create({ ...EMPTY_FILTER, opponentName: 'bob' });
    vi.useFakeTimers();
    setValue('#filter-opponent-name', '   ');
    vi.advanceTimersByTime(FILTER_DEBOUNCE_MS);
    expect(emitted).toEqual([EMPTY_FILTER]);
  });

  it('emits date changes immediately', async () => {
    await create();
    setValue('#filter-from', '2026-01-01');
    setValue('#filter-to', '2026-02-01');
    expect(emitted).toEqual([
      { ...EMPTY_FILTER, from: '2026-01-01' },
      { ...EMPTY_FILTER, from: '2026-01-01', to: '2026-02-01' },
    ]);
    setValue('#filter-from', '');
    expect(emitted.at(-1)).toEqual({ ...EMPTY_FILTER, to: '2026-02-01' });
  });

  it('does not emit an inverted date range and shows an inline error', async () => {
    await create();
    setValue('#filter-from', '2026-05-01');
    setValue('#filter-to', '2026-04-01');
    expect(emitted).toEqual([{ ...EMPTY_FILTER, from: '2026-05-01' }]);
    await fixture.whenStable();
    expect(query('[data-error="dateRange"]').textContent).toContain('before');
    expect(query('#filter-to').getAttribute('aria-invalid')).toBe('true');
  });

  it('emits once the invalid range is corrected', async () => {
    await create();
    setValue('#filter-from', '2026-05-01');
    setValue('#filter-to', '2026-04-01');
    setValue('#filter-to', '2026-06-01');
    expect(emitted.at(-1)).toEqual({ ...EMPTY_FILTER, from: '2026-05-01', to: '2026-06-01' });
    await fixture.whenStable();
    expect(el.querySelector('[data-error="dateRange"]')).toBeNull();
  });

  it('restores the form from the filter input without emitting', async () => {
    await create({ opponentName: 'Ann', from: '2026-02-03', to: '2026-03-04' });
    expect(query<HTMLInputElement>('#filter-opponent-name').value).toBe('Ann');
    expect(query<HTMLInputElement>('#filter-from').value).toBe('2026-02-03');
    expect(query<HTMLInputElement>('#filter-to').value).toBe('2026-03-04');
    expect(emitted).toEqual([]);
  });

  it('follows later filter input changes without echoing them back', async () => {
    await create();
    vi.useFakeTimers();
    fixture.componentRef.setInput('filter', { ...EMPTY_FILTER, opponentName: 'Cleo' });
    fixture.detectChanges();
    vi.advanceTimersByTime(FILTER_DEBOUNCE_MS);
    expect(query<HTMLInputElement>('#filter-opponent-name').value).toBe('Cleo');
    expect(emitted).toEqual([]);
  });

  it('reset clears every field and emits the empty filter', async () => {
    await create({ opponentName: 'bob', from: '2026-01-01', to: null });
    query<HTMLButtonElement>('button[type="reset"]').click();
    expect(emitted).toEqual([EMPTY_FILTER]);
    expect(query<HTMLInputElement>('#filter-opponent-name').value).toBe('');
    expect(query<HTMLInputElement>('#filter-from').value).toBe('');
  });

  it('reset cancels a pending debounced name change', async () => {
    await create();
    vi.useFakeTimers();
    setValue('#filter-opponent-name', 'bob');
    fixture.detectChanges(); // re-render so reset is enabled (whenStable can't run under fake timers)
    query<HTMLButtonElement>('button[type="reset"]').click();
    vi.advanceTimersByTime(FILTER_DEBOUNCE_MS);
    expect(emitted).toEqual([]);
  });

  it('disables reset when nothing is filtered', async () => {
    await create();
    expect(query<HTMLButtonElement>('button[type="reset"]').disabled).toBe(true);
    setValue('#filter-from', '2026-01-01');
    await fixture.whenStable();
    expect(query<HTMLButtonElement>('button[type="reset"]').disabled).toBe(false);
  });

  describe('when the parent re-sends a filter (navigation echo)', () => {
    it('keeps typed text if the same filter arrives during the debounce window', async () => {
      await create();
      vi.useFakeTimers();
      setValue('#filter-opponent-name', 'x');
      // e.g. a sort/page click navigates with the not-yet-emitted (old) filter
      fixture.componentRef.setInput('filter', { ...EMPTY_FILTER });
      fixture.detectChanges();
      expect(query<HTMLInputElement>('#filter-opponent-name').value).toBe('x');
      vi.advanceTimersByTime(FILTER_DEBOUNCE_MS);
      expect(emitted).toEqual([{ ...EMPTY_FILTER, opponentName: 'x' }]);
    });

    it('keeps an un-emitted invalid range and its error across a page change', async () => {
      await create();
      setValue('#filter-from', '2026-05-01');
      setValue('#filter-to', '2026-04-01');
      fixture.componentRef.setInput('filter', { ...EMPTY_FILTER, from: '2026-05-01' });
      await fixture.whenStable();
      expect(query<HTMLInputElement>('#filter-to').value).toBe('2026-04-01');
      expect(el.querySelector('[data-error="dateRange"]')).not.toBeNull();
      expect(emitted).toEqual([{ ...EMPTY_FILTER, from: '2026-05-01' }]);
    });

    it('ignores a late echo of its own earlier emit', async () => {
      await create();
      vi.useFakeTimers();
      setValue('#filter-opponent-name', 'ab');
      vi.advanceTimersByTime(FILTER_DEBOUNCE_MS);
      setValue('#filter-opponent-name', 'abc');
      // the slow router round-trip for "ab" lands after the user kept typing
      fixture.componentRef.setInput('filter', { ...EMPTY_FILTER, opponentName: 'ab' });
      fixture.detectChanges();
      expect(query<HTMLInputElement>('#filter-opponent-name').value).toBe('abc');
      vi.advanceTimersByTime(FILTER_DEBOUNCE_MS);
      expect(emitted.map((f) => f.opponentName)).toEqual(['ab', 'abc']);
    });

    it('still applies a genuinely different filter, e.g. from the back button', async () => {
      await create();
      vi.useFakeTimers();
      setValue('#filter-opponent-name', 'x');
      fixture.componentRef.setInput('filter', { ...EMPTY_FILTER, opponentName: 'Cleo' });
      fixture.detectChanges();
      expect(query<HTMLInputElement>('#filter-opponent-name').value).toBe('Cleo');
      vi.advanceTimersByTime(FILTER_DEBOUNCE_MS);
      expect(emitted).toEqual([]);
    });
  });

  it('only references the date error from aria-describedby while it is shown', async () => {
    await create();
    expect(query('#filter-from').hasAttribute('aria-describedby')).toBe(false);
    setValue('#filter-from', '2026-05-01');
    setValue('#filter-to', '2026-04-01');
    await fixture.whenStable();
    expect(query('#filter-to').getAttribute('aria-describedby')).toBe('filter-date-error');
    expect(el.querySelector('#filter-date-error')).not.toBeNull();
  });
});
