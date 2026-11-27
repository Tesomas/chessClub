import { TestBed } from '@angular/core/testing';
import { outcomeFor } from './game-outcome';
import { OutcomeBadgeComponent } from './outcome-badge/outcome-badge.component';
import { initialsOf, PlayerAvatarComponent } from './player-avatar/player-avatar.component';

describe('outcomeFor', () => {
  it.each([
    ['WHITE_WINS', 'WHITE', 'WIN'],
    ['WHITE_WINS', 'BLACK', 'LOSS'],
    ['BLACK_WINS', 'WHITE', 'LOSS'],
    ['BLACK_WINS', 'BLACK', 'WIN'],
    ['DRAW', 'WHITE', 'DRAW'],
    ['DRAW', 'BLACK', 'DRAW'],
  ] as const)('%s is a %s %s', (result, color, outcome) => {
    expect(outcomeFor(result, color)).toBe(outcome);
  });
});

describe('OutcomeBadgeComponent', () => {
  it.each([
    ['WIN', 'W', 'Win', 'badge-win'],
    ['LOSS', 'L', 'Loss', 'badge-loss'],
    ['DRAW', 'D', 'Draw', 'badge-draw'],
  ] as const)('renders %s', async (outcome, letter, label, css) => {
    const fixture = TestBed.createComponent(OutcomeBadgeComponent);
    fixture.componentRef.setInput('outcome', outcome);
    await fixture.whenStable();
    const badge = (fixture.nativeElement as HTMLElement).querySelector('.badge');
    expect(badge?.textContent?.trim()).toBe(letter);
    expect(badge?.className).toBe(`badge ${css}`);
    expect(badge?.getAttribute('title')).toBe(label);
    expect(badge?.getAttribute('aria-label')).toBe(label);
  });
});

describe('initialsOf', () => {
  it.each([
    ['Ann Lee', 'AL'],
    ['  ann   van der lee ', 'AL'],
    ['Cher', 'C'],
    ['', '?'],
    ['   ', '?'],
  ])('%j -> %s', (name, initials) => {
    expect(initialsOf(name)).toBe(initials);
  });
});

describe('PlayerAvatarComponent', () => {
  async function render(name: string, size?: number, decorative?: boolean): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(PlayerAvatarComponent);
    fixture.componentRef.setInput('name', name);
    if (size !== undefined) fixture.componentRef.setInput('size', size);
    if (decorative !== undefined) fixture.componentRef.setInput('decorative', decorative);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders initials as a labelled placeholder image', async () => {
    const host = await render('Ann Lee');
    const avatar = host.querySelector('.avatar');
    expect(avatar?.textContent?.trim()).toBe('AL');
    expect(avatar?.getAttribute('role')).toBe('img');
    expect(avatar?.getAttribute('aria-label')).toBe('Profile picture placeholder for Ann Lee');
  });

  it('is hidden from assistive tech when decorative', async () => {
    const avatar = (await render('Ann Lee', undefined, true)).querySelector('.avatar');
    expect(avatar?.textContent?.trim()).toBe('AL');
    expect(avatar?.getAttribute('aria-hidden')).toBe('true');
    expect(avatar?.hasAttribute('role')).toBe(false);
    expect(avatar?.hasAttribute('aria-label')).toBe(false);
  });

  it('is labelled, not hidden, by default', async () => {
    const avatar = (await render('Ann Lee')).querySelector('.avatar');
    expect(avatar?.hasAttribute('aria-hidden')).toBe(false);
  });

  it('leaves the default size to CSS and accepts a custom size', async () => {
    expect((await render('Ann Lee')).style.getPropertyValue('--avatar-size')).toBe('');
    expect((await render('Ann Lee', 56)).style.getPropertyValue('--avatar-size')).toBe('56px');
  });
});
