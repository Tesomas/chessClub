import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** An ELO delta: "+8" in green, "−8" in red, "±0" muted. */
@Component({
  selector: 'app-rating-change',
  template: `<span
    class="rating-change"
    [class.gain]="change() > 0"
    [class.loss]="change() < 0"
    [attr.title]="title()"
    >{{ text() }}</span
  >`,
  styles: `
    .rating-change {
      color: var(--color-text-muted);
      font-size: 0.8rem;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }
    .gain {
      color: var(--color-gain);
    }
    .loss {
      color: var(--color-loss);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RatingChangeComponent {
  readonly change = input.required<number>();

  protected readonly text = computed(() => {
    const c = this.change();
    if (c > 0) return `+${c}`;
    if (c < 0) return `−${Math.abs(c)}`;
    return '±0';
  });

  protected readonly title = computed(() => {
    const c = this.change();
    if (c === 0) return 'No rating change';
    return `${c > 0 ? 'Gained' : 'Lost'} ${Math.abs(c)} rating ${Math.abs(c) === 1 ? 'point' : 'points'}`;
  });
}
