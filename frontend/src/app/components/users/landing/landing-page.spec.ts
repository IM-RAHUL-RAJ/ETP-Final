import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LandingPage } from './landing-page';

describe('LandingPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LandingPage],
      providers: [provideRouter([])]
    }).compileComponents();
  });

  it('shows the headline, three features, the dancing companion and the sign-in button', async () => {
    const fixture = TestBed.createComponent(LandingPage);
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toContain('Trade with clarity');
    expect(page.querySelectorAll('.feature').length).toBe(3);
    expect(page.querySelector('app-mascot')).not.toBeNull();
    expect(page.querySelector('[data-testid="sign-in-button"]')?.getAttribute('href')).toBe('/sign-in');
  });
});
