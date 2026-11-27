import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { OutcomeBadgeComponent } from '../../shared/outcome-badge/outcome-badge.component';
import { Page, PlayerGameSort, PlayerGameView, SortDirection } from '../../player-game.model';

export interface SortChange {
  sort: PlayerGameSort;
  direction: SortDirection;
}

/** Newest games first; opponents A-Z. */
const INITIAL_DIRECTION: Record<PlayerGameSort, SortDirection> = {
  date: 'desc',
  opponentName: 'asc',
};

@Component({
  selector: 'app-player-games-table',
  imports: [RouterLink, TitleCasePipe, OutcomeBadgeComponent],
  templateUrl: './player-games-table.component.html',
  styleUrl: './player-games-table.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayerGamesTableComponent {
  readonly page = input.required<Page<PlayerGameView>>();
  readonly sort = input.required<PlayerGameSort>();
  readonly direction = input.required<SortDirection>();

  readonly sortChange = output<SortChange>();
  readonly pageChange = output<number>();

  protected readonly isFirst = computed(() => this.page().page <= 0);
  protected readonly isLast = computed(() => this.page().page >= this.page().totalPages - 1);
  protected readonly isPastEnd = computed(
    () => this.page().content.length === 0 && this.page().totalElements > 0,
  );

  protected ariaSort(field: PlayerGameSort): 'ascending' | 'descending' | 'none' {
    if (this.sort() !== field) return 'none';
    return this.direction() === 'asc' ? 'ascending' : 'descending';
  }

  protected arrow(field: PlayerGameSort): string {
    if (this.sort() !== field) return '↕';
    return this.direction() === 'asc' ? '▲' : '▼';
  }

  /** Clicking the active column flips it; a new column starts in its natural order. */
  protected toggleSort(field: PlayerGameSort): void {
    const flipped: SortDirection = this.direction() === 'asc' ? 'desc' : 'asc';
    const direction = this.sort() === field ? flipped : INITIAL_DIRECTION[field];
    this.sortChange.emit({ sort: field, direction });
  }

  // Prev/Next use aria-disabled rather than disabled: a disabled button drops keyboard focus
  // to <body> the moment the last page loads. At the ends they are no-ops instead.
  protected previous(): void {
    if (!this.isFirst()) this.goTo(this.page().page - 1);
  }

  protected next(): void {
    if (!this.isLast()) this.goTo(this.page().page + 1);
  }

  protected goTo(page: number): void {
    this.pageChange.emit(page);
  }
}
