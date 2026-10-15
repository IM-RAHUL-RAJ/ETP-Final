import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ChangePasswordComponent } from './change-password.component';
import { AuthService } from '../../../../core/auth/auth.service';

describe('ChangePasswordComponent', () => {
  const changePassword = vi.fn();
  const mustChange = signal(false);

  beforeEach(async () => {
    changePassword.mockReset();
    mustChange.set(false);

    await TestBed.configureTestingModule({
      imports: [ChangePasswordComponent],
      providers: [{ provide: AuthService, useValue: { changePassword, mustChangePassword: mustChange } }]
    }).compileComponents();
  });

  function create() {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    return fixture;
  }

  function fill(fixture: ReturnType<typeof create>, current: string, next: string, confirm: string) {
    const root = fixture.nativeElement as HTMLElement;
    const set = (testid: string, value: string) => {
      const input = root.querySelector<HTMLInputElement>(`[data-testid="${testid}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    set('cp-current', current);
    set('cp-new', next);
    set('cp-confirm', confirm);
    fixture.detectChanges();
  }

  function submit(fixture: ReturnType<typeof create>) {
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="cp-submit"]')!.click();
    fixture.detectChanges();
  }

  it('sends the current and new password, then confirms and clears the form', () => {
    changePassword.mockReturnValue(of(undefined));
    const fixture = create();

    fill(fixture, 'Temp-Password-123', 'A-brand-new-password', 'A-brand-new-password');
    submit(fixture);

    expect(changePassword).toHaveBeenCalledWith('Temp-Password-123', 'A-brand-new-password');
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="cp-success"]')).not.toBeNull();
    expect((root.querySelector('[data-testid="cp-new"]') as HTMLInputElement).value).toBe('');
  });

  it('does not send a short password or a mismatched confirmation', () => {
    const fixture = create();

    fill(fixture, 'Temp-Password-123', 'short', 'short');
    submit(fixture);
    fill(fixture, 'Temp-Password-123', 'A-brand-new-password', 'A-different-password');
    submit(fixture);

    expect(changePassword).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('do not match');
  });

  it('refuses a new password equal to the current one without calling the service', () => {
    const fixture = create();

    fill(fixture, 'The-same-password-1', 'The-same-password-1', 'The-same-password-1');
    submit(fixture);

    expect(changePassword).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="cp-error"]')?.textContent).toContain(
      'different'
    );
  });

  it('shows the service wording when the current password is wrong', () => {
    changePassword.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { errorCode: 'AUTH-400', message: 'Current password is incorrect' }
          })
      )
    );
    const fixture = create();

    fill(fixture, 'wrong-password-here', 'A-brand-new-password', 'A-brand-new-password');
    submit(fixture);

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="cp-error"]')?.textContent).toContain(
      'Current password is incorrect'
    );
  });

  it('flags the section as urgent while the customer is on the temporary password', () => {
    mustChange.set(true);
    const fixture = create();

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="cp-urgent"]')).not.toBeNull();
  });
});
