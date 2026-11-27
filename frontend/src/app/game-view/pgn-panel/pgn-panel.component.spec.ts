import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CLIPBOARD, COPY_FEEDBACK_MS, PgnPanelComponent } from './pgn-panel.component';

type WriteText = (text: string) => Promise<void>;

describe('PgnPanelComponent copy feedback', () => {
  let fixture: ComponentFixture<PgnPanelComponent>;
  let el: HTMLElement;
  let writeText: ReturnType<typeof vi.fn<WriteText>>;

  beforeEach(async () => {
    writeText = vi.fn<WriteText>().mockResolvedValue(undefined);
    TestBed.configureTestingModule({
      imports: [PgnPanelComponent],
      providers: [{ provide: CLIPBOARD, useValue: { writeText } }],
    });
    fixture = TestBed.createComponent(PgnPanelComponent);
    fixture.componentRef.setInput('pgn', '1. e4 e5');
    el = fixture.nativeElement;
    await fixture.whenStable();
    vi.useFakeTimers();
  });

  afterEach(() => vi.useRealTimers());

  const status = () => el.querySelector('.copy-status')?.textContent ?? '';

  /** Click Copy, let the clipboard promise settle, then render. */
  async function clickCopy(): Promise<void> {
    el.querySelector<HTMLButtonElement>('.copy-btn')?.click();
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
  }

  it('clears the message after the feedback period', async () => {
    await clickCopy();
    expect(status().trim()).toBe('Copied to clipboard');
    await vi.advanceTimersByTimeAsync(COPY_FEEDBACK_MS - 1);
    fixture.detectChanges();
    expect(status().trim()).toBe('Copied to clipboard');
    await vi.advanceTimersByTimeAsync(1);
    fixture.detectChanges();
    expect(status()).toBe('');
  });

  it('changes the live text on a repeat click so it is announced again', async () => {
    await clickCopy();
    const first = status();
    await clickCopy();
    const second = status();
    expect(second.trim()).toBe('Copied to clipboard');
    expect(second).not.toBe(first);
    expect(writeText).toHaveBeenCalledTimes(2);
  });

  it('restarts the feedback period on a repeat click', async () => {
    await clickCopy();
    await vi.advanceTimersByTimeAsync(COPY_FEEDBACK_MS - 500);
    await clickCopy();
    await vi.advanceTimersByTimeAsync(COPY_FEEDBACK_MS - 500);
    fixture.detectChanges();
    expect(status().trim()).toBe('Copied to clipboard');
    await vi.advanceTimersByTimeAsync(500);
    fixture.detectChanges();
    expect(status()).toBe('');
    expect(vi.getTimerCount()).toBe(0);
  });

  it('also clears a failure message', async () => {
    writeText.mockRejectedValue(new Error('denied'));
    await clickCopy();
    expect(status().trim()).toContain('Copy failed');
    await vi.advanceTimersByTimeAsync(COPY_FEEDBACK_MS);
    fixture.detectChanges();
    expect(status()).toBe('');
  });

  it('cancels the pending reset when destroyed', async () => {
    await clickCopy();
    expect(vi.getTimerCount()).toBe(1);
    fixture.destroy();
    expect(vi.getTimerCount()).toBe(0);
  });
});

describe('PgnPanelComponent move list', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PgnPanelComponent] });
  });

  async function moveList(pgn: string): Promise<HTMLElement | null> {
    const fixture = TestBed.createComponent(PgnPanelComponent);
    fixture.componentRef.setInput('pgn', pgn);
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.moves');
  }

  it('sizes the move-number column for one-digit numbers plus the dot', async () => {
    const moves = await moveList('1. e4 e5 2. Nf3');
    expect(moves?.style.getPropertyValue('--move-no-ch')).toBe('2');
  });

  it('widens the move-number column for three-digit numbers', async () => {
    const moves = await moveList('118. Kg2 Kf7 119. Kf3 Ke6 120. Ke4');
    expect(moves?.style.getPropertyValue('--move-no-ch')).toBe('4');
    expect(moves?.querySelector('.move-no')?.textContent).toBe('118.');
  });
});
