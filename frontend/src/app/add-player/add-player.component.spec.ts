import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AddPlayerComponent } from './add-player.component';

describe('AddPlayerComponent', () => {
  let http: HttpTestingController;
  let el: HTMLElement;
  let fixture: ReturnType<typeof TestBed.createComponent<AddPlayerComponent>>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AddPlayerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(AddPlayerComponent);
    el = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function type(selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector);
    if (!input) throw new Error(`missing ${selector}`);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(): Promise<void> {
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('renders an optional birth date field', () => {
    const input = el.querySelector<HTMLInputElement>('#birthDate');
    expect(input?.type).toBe('date');
    expect(input?.required).toBe(false);
  });

  it('sends birthDate as an ISO date when provided', async () => {
    type('#firstName', 'Ann');
    type('#lastName', 'Lee');
    type('#birthDate', '1990-05-17');
    await submit();
    const req = http.expectOne({ method: 'POST', url: '/api/players' });
    expect(req.request.body).toEqual({
      firstName: 'Ann',
      lastName: 'Lee',
      rapidElo: 1200,
      blitzElo: 1200,
      bulletElo: 1200,
      classicalElo: 1200,
      birthDate: '1990-05-17',
    });
    req.flush({});
  });

  it('sends birthDate as null when left empty', async () => {
    type('#firstName', 'Ann');
    type('#lastName', 'Lee');
    await submit();
    const req = http.expectOne('/api/players');
    expect(req.request.body.birthDate).toBeNull();
    req.flush({});
  });

  it('rejects a birth date in the future', async () => {
    type('#firstName', 'Ann');
    type('#lastName', 'Lee');
    type('#birthDate', '2999-01-01');
    await submit();
    http.expectNone('/api/players');
    expect(el.querySelector('.field-error')?.textContent).toContain('future');
  });

  it('shows an error when saving fails', async () => {
    type('#firstName', 'Ann');
    type('#lastName', 'Lee');
    await submit();
    http.expectOne('/api/players').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(el.querySelector('.submit-error')?.textContent).toContain('Failed to add player');
  });
});
