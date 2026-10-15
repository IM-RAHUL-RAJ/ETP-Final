import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/auth/auth.service';

export const MIN_PASSWORD_LENGTH = 12;

function matchesNewPassword(control: AbstractControl): ValidationErrors | null {
  const group = control.parent;
  return group && control.value !== group.get('newPassword')?.value ? { mismatch: true } : null;
}

/** Choose your own password. Used on the Account tab, and on the admin page for the admin's own login. */
@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChangePasswordComponent {
  @Output() passwordChanged = new EventEmitter<void>();

  private readonly formBuilder = inject(FormBuilder);
  protected readonly auth = inject(AuthService);

  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPasswords = signal(false);

  protected readonly form = this.formBuilder.nonNullable.group({
    currentPassword: ['', [Validators.required, Validators.maxLength(128)]],
    newPassword: ['', [Validators.required, Validators.minLength(MIN_PASSWORD_LENGTH), Validators.maxLength(128)]],
    confirmPassword: ['', [Validators.required, matchesNewPassword]]
  });

  protected readonly minLength = MIN_PASSWORD_LENGTH;
  protected readonly newLength = signal(0);
  protected readonly strength = computed(() => {
    const length = this.newLength();
    return length === 0 ? 0 : length < MIN_PASSWORD_LENGTH ? 1 : length < 16 ? 2 : 3;
  });

  constructor() {
    this.form.controls.newPassword.valueChanges.subscribe((value) => {
      this.newLength.set(value.length);
      // The confirmation depends on it.
      this.form.controls.confirmPassword.updateValueAndValidity({ emitEvent: false });
    });
  }

  protected toggleVisibility(): void {
    this.showPasswords.update((value) => !value);
  }

  protected newPasswordError(): string | null {
    const control = this.form.controls.newPassword;
    if (!control.touched || control.valid) {
      return null;
    }
    return `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
  }

  protected confirmError(): string | null {
    const control = this.form.controls.confirmPassword;
    return control.touched && control.hasError('mismatch') ? 'The two passwords do not match.' : null;
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    this.done.set(false);
    this.error.set(null);

    const { currentPassword, newPassword } = this.form.getRawValue();
    if (this.form.invalid || this.submitting()) {
      return;
    }
    if (currentPassword === newPassword) {
      this.error.set('Your new password must be different from the current one.');
      return;
    }

    this.submitting.set(true);
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.form.reset({ currentPassword: '', newPassword: '', confirmPassword: '' });
        this.newLength.set(0);
        this.submitting.set(false);
        this.done.set(true);
        this.passwordChanged.emit();
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.error.set(this.messageFor(error));
      }
    });
  }

  /** The service's own wording for a wrong or unchanged password; a plain line for everything else. */
  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const code = error.error?.errorCode;
      if (error.status === 400 && (code === 'AUTH-400' || code === 'AUTH-429') && typeof error.error?.message === 'string') {
        return error.error.message;
      }
      if (error.status === 422) {
        return `Use a new password of ${MIN_PASSWORD_LENGTH} to 128 characters.`;
      }
      if (error.status === 0) {
        return 'Could not reach the sign-in service. Try again.';
      }
    }
    return 'Your password could not be changed. Try again.';
  }
}
