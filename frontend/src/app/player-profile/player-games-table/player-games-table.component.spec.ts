import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PlayerGamesTableComponent, SortChange } from './player-games-table.component';
import { Page, PlayerGameView } from '../../player-game.model';

function game(overrides: Partial<PlayerGameView> = {}): PlayerGameView {
  return {
    gameId: 'g1',
    date: '2026-10-01',
    gameType: 'BLITZ',
    time: '3+2',
    color: 'WHITE',
    outcome: 'WIN',
    playerRating: 1500,
    playerRatingChange: 8,
    opponentId: 'p2',
    opponentName: 'Bob Brooks',
    opponentRating: 1480,
    opponentRatingChange: -8,
    ...overrides,
  };
}

function page(content: PlayerGameView[], overrides: Partial<Page<PlayerGameView>> = {}) {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1, ...overrides };
}

describe('PlayerGamesTableComponent', () => {
  let fixture: ComponentFixture<PlayerGamesTableComponent>;
  let el: HTMLElement;
  let sorts: SortChange[];
  let pages: number[];

  async function render(
    data: Page<PlayerGameView>,
    sort: SortChange = { sort: 'date', direction: 'desc' },
  ): Promise<void> {
    fixture.componentRef.setInput('page', data);
    fixture.componentRef.setInput('sort', sort.sort);
    fixture.componentRef.setInput('direction', sort.direction);
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PlayerGamesTableComponent],
      providers: [provideRouter([])],
    });
    fixture = TestBed.createComponent(PlayerGamesTableComponent);
    el = fixture.nativeElement;
    sorts = [];
    pages = [];
    fixture.componentInstance.sortChange.subscribe((s) => sorts.push(s));
    fixture.componentInstance.pageChange.subscribe((p) => pages.push(p));
  });

  const rows = () => Array.from(el.querySelectorAll('tbody tr'));
  const button = (selector: string) => {
    const found = el.querySelector<HTMLButtonElement>(selector);
    if (!found) throw new Error(`missing ${selector}`);
    return found;
  };

  it('renders one row per game with every column', async () => {
    await render(page([game()]));
    const cells = Array.from(rows()[0].querySelectorAll('td')).map((td) =>
      td.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(cells).toEqual([
      '2026-10-01',
      'Blitz',
      'White',
      'Bob Brooks',
      '1480 −8',
      '1500 +8',
      'W',
      '3+2',
    ]);
  });

  it('colours the rating changes: gain green, loss red', async () => {
    await render(page([game({ playerRatingChange: 8, opponentRatingChange: -8 })]));
    const [opp, mine] = Array.from(rows()[0].querySelectorAll('.rating-change'));
    expect(mine.classList).toContain('gain');
    expect(opp.classList).toContain('loss');
  });

  it('renders semantic outcome badges', async () => {
    await render(
      page([
        game({ gameId: 'a', outcome: 'WIN' }),
        game({ gameId: 'b', outcome: 'LOSS' }),
        game({ gameId: 'c', outcome: 'DRAW' }),
      ]),
    );
    const badges = Array.from(el.querySelectorAll('.badge'));
    expect(badges.map((b) => b.textContent?.trim())).toEqual(['W', 'L', 'D']);
    expect(badges.map((b) => b.className)).toEqual([
      'badge badge-win',
      'badge badge-loss',
      'badge badge-draw',
    ]);
    expect(badges.map((b) => b.getAttribute('title'))).toEqual(['Win', 'Loss', 'Draw']);
  });

  it('links the opponent to their profile', async () => {
    await render(page([game({ opponentId: 'p9', opponentName: 'Zed Zhou' })]));
    const link = el.querySelector<HTMLAnchorElement>('td a.opponent');
    expect(link?.textContent?.trim()).toBe('Zed Zhou');
    expect(link?.getAttribute('href')).toBe('/players/p9');
  });

  it('shows the total count and page position', async () => {
    await render(page([game()], { page: 1, totalElements: 140, totalPages: 6 }));
    expect(el.querySelector('.pager-status')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Page 2 of 6 · 140 games',
    );
  });

  it('uses singular wording for one game', async () => {
    await render(page([game()]));
    expect(el.querySelector('.pager-status')?.textContent).toContain('1 game');
    expect(el.querySelector('.pager-status')?.textContent).not.toContain('1 games');
  });

  it('pages forward and back', async () => {
    await render(page([game()], { page: 1, totalElements: 60, totalPages: 3 }));
    button('.pager-next').click();
    button('.pager-prev').click();
    expect(pages).toEqual([2, 0]);
  });

  it('marks the end buttons aria-disabled (not disabled, so focus is kept) and ignores them', async () => {
    await render(page([game()], { page: 0, totalElements: 60, totalPages: 3 }));
    expect(button('.pager-prev').getAttribute('aria-disabled')).toBe('true');
    expect(button('.pager-prev').disabled).toBe(false);
    expect(button('.pager-next').getAttribute('aria-disabled')).toBe('false');
    button('.pager-prev').click();
    expect(pages).toEqual([]);

    await render(page([game()], { page: 2, totalElements: 60, totalPages: 3 }));
    expect(button('.pager-next').getAttribute('aria-disabled')).toBe('true');
    button('.pager-next').click();
    expect(pages).toEqual([]);
  });

  it('keeps focus on Next when it becomes the last page', async () => {
    await render(page([game()], { page: 1, totalElements: 60, totalPages: 3 }));
    const next = button('.pager-next');
    next.focus();
    next.click();
    await render(page([game()], { page: 2, totalElements: 60, totalPages: 3 }));
    expect(document.activeElement).toBe(next);
  });

  it('reuses the same aria-live status element across page changes', async () => {
    await render(page([game()], { page: 0, totalElements: 60, totalPages: 3 }));
    const status = el.querySelector('.pager-status');
    expect(status?.getAttribute('aria-live')).toBe('polite');
    await render(page([], { page: 1, totalElements: 60, totalPages: 3 }));
    await render(page([game({ gameId: 'g2' })], { page: 2, totalElements: 60, totalPages: 3 }));
    expect(el.querySelector('.pager-status')).toBe(status);
    expect(status?.textContent).toContain('Page 3 of 3');
  });

  it('marks the active sort column and toggles its direction', async () => {
    await render(page([game()]), { sort: 'date', direction: 'desc' });
    expect(el.querySelector('th[data-sort="date"]')?.getAttribute('aria-sort')).toBe('descending');
    expect(el.querySelector('th[data-sort="opponentName"]')?.getAttribute('aria-sort')).toBe(
      'none',
    );
    button('th[data-sort="date"] button').click();
    expect(sorts).toEqual([{ sort: 'date', direction: 'asc' }]);
  });

  it('toggles an ascending opponent sort to descending', async () => {
    await render(page([game()]), { sort: 'opponentName', direction: 'asc' });
    expect(el.querySelector('th[data-sort="opponentName"]')?.getAttribute('aria-sort')).toBe(
      'ascending',
    );
    button('th[data-sort="opponentName"] button').click();
    expect(sorts).toEqual([{ sort: 'opponentName', direction: 'desc' }]);
  });

  it('starts opponent A-Z and date newest-first when switching columns', async () => {
    await render(page([game()]), { sort: 'date', direction: 'asc' });
    button('th[data-sort="opponentName"] button').click();
    await render(page([game()]), { sort: 'opponentName', direction: 'asc' });
    button('th[data-sort="date"] button').click();
    expect(sorts).toEqual([
      { sort: 'opponentName', direction: 'asc' },
      { sort: 'date', direction: 'desc' },
    ]);
  });

  it('only offers sorting on date and opponent', async () => {
    await render(page([game()]));
    const sortable = Array.from(el.querySelectorAll('th[data-sort]')).map((th) =>
      th.getAttribute('data-sort'),
    );
    expect(sortable).toEqual(['date', 'opponentName']);
  });

  it('shows an empty state when no games match', async () => {
    await render(page([], { totalElements: 0, totalPages: 0 }));
    expect(el.querySelector('table')).toBeNull();
    expect(el.querySelector('.empty')?.textContent).toContain('No games match');
  });

  it('offers a way back when the page is past the end', async () => {
    await render(page([], { page: 9, totalElements: 30, totalPages: 2 }));
    expect(el.querySelector('.empty')?.textContent).toContain('past the last page');
    button('.empty button').click();
    expect(pages).toEqual([0]);
  });
});
