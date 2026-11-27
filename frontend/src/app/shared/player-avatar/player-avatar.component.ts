import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** First letter of the first and last word, upper-cased; '?' for a blank name. */
export function initialsOf(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  const first = words[0].charAt(0);
  const last = words.length > 1 ? (words.at(-1) ?? '').charAt(0) : '';
  return `${first}${last}`.toUpperCase();
}

/** Placeholder profile picture: the player's initials in a gold-ringed circle. */
@Component({
  selector: 'app-player-avatar',
  template: `<div
    class="avatar"
    [attr.role]="decorative() ? null : 'img'"
    [attr.aria-label]="decorative() ? null : label()"
    [attr.aria-hidden]="decorative() ? 'true' : null"
  >
    {{ initials() }}
  </div>`,
  styleUrl: './player-avatar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[style.--avatar-size]': 'size() ? size() + "px" : null' },
})
export class PlayerAvatarComponent {
  readonly name = input.required<string>();
  /** Diameter in pixels. Unset = 96px, which pages may override with `--avatar-size`. */
  readonly size = input<number | null>(null);
  /** Hide from assistive tech when the name is already right next to it (e.g. a linked name). */
  readonly decorative = input(false);

  protected readonly initials = computed(() => initialsOf(this.name()));
  protected readonly label = computed(() => `Profile picture placeholder for ${this.name()}`);
}
