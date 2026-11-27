import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { PlayerOutcome } from '../../player-game.model';

interface BadgeStyle {
  letter: string;
  label: string;
  css: string;
}

const BADGE: Record<PlayerOutcome, BadgeStyle> = {
  WIN: { letter: 'W', label: 'Win', css: 'badge-win' },
  LOSS: { letter: 'L', label: 'Loss', css: 'badge-loss' },
  DRAW: { letter: 'D', label: 'Draw', css: 'badge-draw' },
};

/** W / L / D chip in the club's semantic win (green), loss (red), draw (grey) colors. */
@Component({
  selector: 'app-outcome-badge',
  template: `<span
    role="img"
    class="badge {{ badge().css }}"
    [attr.title]="badge().label"
    [attr.aria-label]="badge().label"
    >{{ badge().letter }}</span
  >`,
  styleUrl: './outcome-badge.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OutcomeBadgeComponent {
  readonly outcome = input.required<PlayerOutcome>();
  protected readonly badge = computed(() => BADGE[this.outcome()]);
}
