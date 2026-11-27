import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  InjectionToken,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { parsePgn } from '../../pgn';

export type ClipboardWriter = Pick<Clipboard, 'writeText'>;

/** The async clipboard API, or null where the browser doesn't offer it (e.g. insecure origins). */
export const CLIPBOARD = new InjectionToken<ClipboardWriter | null>('CLIPBOARD', {
  providedIn: 'root',
  factory: () => (typeof navigator !== 'undefined' && navigator.clipboard) || null,
});

type CopyState = 'idle' | 'copied' | 'failed';

/** How long the copy result stays on screen. */
export const COPY_FEEDBACK_MS = 2000;

const COPY_MESSAGE: Record<CopyState, string> = {
  idle: '',
  copied: 'Copied to clipboard',
  failed: 'Copy failed — select the text and copy it manually',
};

/** Shows a game's PGN: tag pairs, numbered move pairs, or the raw text if it can't be parsed. */
@Component({
  selector: 'app-pgn-panel',
  templateUrl: './pgn-panel.component.html',
  styleUrl: './pgn-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PgnPanelComponent {
  private readonly clipboard = inject(CLIPBOARD);

  readonly pgn = input.required<string | null>();

  protected readonly hasPgn = computed(() => (this.pgn() ?? '').trim().length > 0);
  protected readonly parsed = computed(() => {
    const pgn = this.pgn();
    return pgn && this.hasPgn() ? parsePgn(pgn) : null;
  });

  /** Width of the move-number column in `ch`: digits of the largest number plus the dot. */
  protected readonly moveNumberWidth = computed(() => {
    const moves = this.parsed()?.moves ?? [];
    const widest = moves.reduce((max, move) => Math.max(max, move.number), 1);
    return String(widest).length + 1;
  });

  /** Resets to idle whenever a different PGN is shown. */
  private readonly copyState = linkedSignal<string | null, CopyState>({
    source: this.pgn,
    computation: () => 'idle',
  });
  /** Bumped per copy result; alternating a trailing NBSP makes live regions re-announce. */
  private readonly copyCount = signal(0);
  protected readonly copyMessage = computed(() => {
    const message = COPY_MESSAGE[this.copyState()];
    return message && this.copyCount() % 2 === 0 ? `${message}\u00a0` : message;
  });

  private resetTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.clearResetTimer());
  }

  protected async copy(): Promise<void> {
    const text = this.pgn();
    if (!text) return;
    let state: CopyState = 'failed';
    try {
      if (this.clipboard) {
        await this.clipboard.writeText(text);
        state = 'copied';
      }
    } catch {
      state = 'failed';
    }
    // Ignore a late result if another game's PGN is showing by now.
    if (this.pgn() === text) this.showCopyResult(state);
  }

  private showCopyResult(state: CopyState): void {
    this.copyState.set(state);
    this.copyCount.update((count) => count + 1);
    this.clearResetTimer();
    this.resetTimer = setTimeout(() => {
      this.resetTimer = null;
      this.copyState.set('idle');
    }, COPY_FEEDBACK_MS);
  }

  private clearResetTimer(): void {
    if (this.resetTimer !== null) clearTimeout(this.resetTimer);
    this.resetTimer = null;
  }
}
