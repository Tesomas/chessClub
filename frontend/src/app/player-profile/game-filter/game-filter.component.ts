import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, merge } from 'rxjs';
import { EMPTY_FILTER, MAX_OPPONENT_NAME_LENGTH, PlayerGameFilter } from '../../player-game.model';
import {
  filterErrors,
  filtersEqual,
  isFilterValid,
  normalizeOpponentName,
} from '../../player-game-query';

/** Delay before a typed opponent name is applied, so each keystroke doesn't fire a request. */
export const FILTER_DEBOUNCE_MS = 350;

@Component({
  selector: 'app-game-filter',
  imports: [ReactiveFormsModule],
  templateUrl: './game-filter.component.html',
  styleUrl: './game-filter.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GameFilterComponent {
  private readonly fb = inject(FormBuilder);

  /** Current filter (from the URL). The form follows it. */
  readonly filter = input.required<PlayerGameFilter>();
  /** Emits a complete, valid filter whenever the user changes something. */
  readonly filterChange = output<PlayerGameFilter>();

  protected readonly maxNameLength = MAX_OPPONENT_NAME_LENGTH;

  protected readonly form = this.fb.nonNullable.group({
    opponentName: '',
    from: '',
    to: '',
  });

  /** Normalized form state; drives the inline errors and the reset button. */
  private readonly formFilter = signal<PlayerGameFilter>(EMPTY_FILTER);
  protected readonly errors = computed(() => filterErrors(this.formFilter()));
  protected readonly isEmpty = computed(() => filtersEqual(this.formFilter(), EMPTY_FILTER));

  /** Last filter received from the parent or emitted to it; anything equal is an echo. */
  private current: PlayerGameFilter = EMPTY_FILTER;

  constructor() {
    effect(() => {
      const filter = this.filter();
      untracked(() => this.sync(filter));
    });

    this.form.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.formFilter.set(this.readForm()));

    const { opponentName, from, to } = this.form.controls;
    merge(
      from.valueChanges,
      to.valueChanges,
      opponentName.valueChanges.pipe(debounceTime(FILTER_DEBOUNCE_MS)),
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.emitIfChanged(this.readForm()));
  }

  protected reset(event: Event): void {
    event.preventDefault();
    this.writeForm(EMPTY_FILTER);
    this.emitIfChanged(EMPTY_FILTER);
  }

  /**
   * Apply a filter from the URL only when it differs from the last one we received or emitted.
   * Echoes (a sort/page navigation carrying the old filter, or a late round-trip of our own emit)
   * must not wipe text the user is still typing or an invalid range they haven't fixed yet.
   */
  private sync(filter: PlayerGameFilter): void {
    if (filtersEqual(filter, this.current)) return;
    this.current = filter;
    this.writeForm(filter);
  }

  private emitIfChanged(filter: PlayerGameFilter): void {
    if (!isFilterValid(filter) || filtersEqual(filter, this.current)) return;
    this.current = filter;
    this.filterChange.emit(filter);
  }

  /** Silent write: programmatic changes must not feed the (debounced) emit streams. */
  private writeForm(filter: PlayerGameFilter): void {
    this.form.setValue(
      { opponentName: filter.opponentName ?? '', from: filter.from ?? '', to: filter.to ?? '' },
      { emitEvent: false },
    );
    this.formFilter.set(this.readForm());
  }

  private readForm(): PlayerGameFilter {
    const { opponentName, from, to } = this.form.getRawValue();
    return {
      opponentName: normalizeOpponentName(opponentName),
      from: from || null,
      to: to || null,
    };
  }
}
