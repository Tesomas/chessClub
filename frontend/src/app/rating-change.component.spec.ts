import { TestBed } from '@angular/core/testing';
import { RatingChangeComponent } from './rating-change.component';

describe('RatingChangeComponent', () => {
  function render(change: number): HTMLElement {
    const fixture = TestBed.createComponent(RatingChangeComponent);
    fixture.componentRef.setInput('change', change);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('.rating-change') as HTMLElement;
  }

  it('shows a gain with a plus sign in the gain style', () => {
    const el = render(8);
    expect(el.textContent?.trim()).toBe('+8');
    expect(el.classList).toContain('gain');
    expect(el.classList).not.toContain('loss');
    expect(el.title).toBe('Gained 8 rating points');
  });

  it('shows a loss with a minus sign in the loss style', () => {
    const el = render(-1);
    expect(el.textContent?.trim()).toBe('−1');
    expect(el.classList).toContain('loss');
    expect(el.classList).not.toContain('gain');
    expect(el.title).toBe('Lost 1 rating point');
  });

  it('shows no change as ±0 in neither style', () => {
    const el = render(0);
    expect(el.textContent?.trim()).toBe('±0');
    expect(el.classList).not.toContain('gain');
    expect(el.classList).not.toContain('loss');
  });
});
