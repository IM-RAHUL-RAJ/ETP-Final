import { Component, inject, signal, computed, ChangeDetectionStrategy, ChangeDetectorRef, ApplicationRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService, LoginRequest } from '../../../core/auth/auth.service';
import { BrandLogoComponent } from '../shared/brand-logo/brand-logo.component';
import { IconComponent } from '../shared/icon/icon.component';
import { MascotComponent } from '../shared/mascot/mascot.component';
import { AnimationName } from '../../../core/mascot/pixel-mascot';
import { ThemeService } from '../../../core/theme/theme.service';
import { SessionSocketService } from '../../../core/auth/session-socket.service';
import { PreferencesService } from '../../../core/services/preferences.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, BrandLogoComponent, IconComponent, MascotComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent {
  private readonly maxInputLength = 128;
  private readonly usernamePattern = /^[a-zA-Z0-9._-]+$/;
  private readonly maxSignInAttempts = 5;
  private readonly lockoutDurationMs = 60_000;
  private readonly lockoutStorageKey = 'signin_lockout_until';

  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private themeService = inject(ThemeService);
  private sessionSocket = inject(SessionSocketService);
  private preferences = inject(PreferencesService);
  private cdr = inject(ChangeDetectorRef);
  private appRef = inject(ApplicationRef);


  // Form group for sign-in with username and password validation
  
  signInForm: FormGroup;

  // Track submission state
  isSubmitting = signal(false);

  // Error message from failed login attempt
  errorMessage = signal<string | null>(null);

  /** The companion reacts: sad at a refusal, thinking while it checks, waving otherwise. */
  protected readonly mascotAction = computed<AnimationName>(() => (this.errorMessage() ? 'sad' : this.isSubmitting() ? 'think' : 'wave'));

  // Track current theme for login page icon toggle
  currentTheme = signal<'light' | 'dark'>('light');

  // Toggle password visibility for login field
  showPassword = signal(false);

  // Failed sign-in attempts in this page/session.
  failedAttempts = signal(0);

  // Trigger signal to force re-evaluation of countdown computed signals.
  private countdownTrigger = signal(0);

  // Lockout end timestamp in epoch milliseconds.
  lockoutUntil = signal<number | null>(this.readLockoutUntil());

  // Compute remaining seconds in lockout for display (updates via timer).
  lockoutSecondsRemaining = computed(() => {
    this.countdownTrigger(); // Depend on trigger to force re-evaluation every tick
    const until = this.lockoutUntil();
    if (!until || until <= Date.now()) {
      return 0;
    }
    return Math.max(1, Math.ceil((until - Date.now()) / 1000));
  });

  // Compute button text for lockout countdown.
  lockoutButtonText = computed(() => {
    const secs = this.lockoutSecondsRemaining();
    if (secs > 0) {
      return `Try again in ${secs}s`;
    }
    return 'Sign in';
  });

  // Track if lockout countdown interval is active.
  private lockoutCountdownInterval: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.currentTheme.set(this.themeService.isDarkMode() ? 'dark' : 'light');

    this.restoreLockoutState();

    // Why the user was sent here: a rejected or tampered token, or a session opened elsewhere.
    const sessionMessage = this.route.snapshot.queryParamMap.get('sessionMessage');
    if (sessionMessage && !this.isCurrentlyLockedOut()) {
      this.errorMessage.set(sessionMessage.slice(0, 200));
    }

    this.signInForm = this.fb.group({
      username: [
        '',
        [
          Validators.required,
          Validators.minLength(3),
          Validators.maxLength(64),
          Validators.pattern(this.usernamePattern)
        ]
      ],
      password: [
        '',
        [
          Validators.required,
          Validators.minLength(6),
          Validators.maxLength(this.maxInputLength),
          this.noLeadingOrTrailingSpacesValidator()
        ]
      ]
    });
  }

  private readLockoutUntil(): number | null {
    const raw = sessionStorage.getItem(this.lockoutStorageKey);
    if (!raw) {
      return null;
    }
    const parsed = Number(raw);
    if (!Number.isFinite(parsed) || parsed <= Date.now()) {
      sessionStorage.removeItem(this.lockoutStorageKey);
      return null;
    }
    return parsed;
  }

  private restoreLockoutState(): void {
    const until = this.lockoutUntil();
    if (until && until > Date.now()) {
      this.errorMessage.set(this.lockoutMessage(until));
      this.startCountdownUpdater();
      return;
    }
    this.clearLockout();
  }

  private startCountdownUpdater(): void {
    if (this.lockoutCountdownInterval) {
      clearInterval(this.lockoutCountdownInterval);
    }
    this.lockoutCountdownInterval = setInterval(() => {
      this.countdownTrigger.update(v => v + 1); // Signal update triggers change detection automatically
      const until = this.lockoutUntil();
      if (!until || until <= Date.now()) {
        this.clearLockout();
        if (this.lockoutCountdownInterval) {
          clearInterval(this.lockoutCountdownInterval);
          this.lockoutCountdownInterval = null;
        }
      } else {
        this.errorMessage.set(this.lockoutMessage(until));
      }
    }, 500);
  }

  private lockoutMessage(until: number): string {
    const seconds = Math.max(1, Math.ceil((until - Date.now()) / 1000));
    return ` Too many attempts sign in. Try again in ${seconds}s.`;
  }

  private setLockout(): void {
    const until = Date.now() + this.lockoutDurationMs;
    this.lockoutUntil.set(until);
    sessionStorage.setItem(this.lockoutStorageKey, String(until));
    this.errorMessage.set(this.lockoutMessage(until));
    this.appRef.tick();
    this.startCountdownUpdater();
  }

  private clearLockout(): void {
    this.lockoutUntil.set(null);
    sessionStorage.removeItem(this.lockoutStorageKey);
    this.failedAttempts.set(0);
    this.errorMessage.set(null);
    this.countdownTrigger.set(0);
    this.appRef.tick();
    if (this.lockoutCountdownInterval) {
      clearInterval(this.lockoutCountdownInterval);
      this.lockoutCountdownInterval = null;
    }
  }

  private isCurrentlyLockedOut(): boolean {
    const until = this.lockoutUntil();
    if (!until) {
      return false;
    }
    if (until <= Date.now()) {
      this.clearLockout();
      return false;
    }
    return true;
  }

  private handleLockout(until: number): void {
    this.errorMessage.set(this.lockoutMessage(until));
    this.appRef.tick();
  }

  private registerFailedAttempt(): void {
    const next = this.failedAttempts() + 1;
    this.failedAttempts.set(next);
    if (next >= this.maxSignInAttempts) {
      this.setLockout();
    }
  }

  private noLeadingOrTrailingSpacesValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value = `${control.value ?? ''}`;
      if (!value) {
        return null;
      }
      return value.trim() !== value ? { spaces: true } : null;
    };
  }

  private sanitizeUsername(raw: string): string {
    const trimmed = raw.trim();
    return trimmed.replace(/[^a-zA-Z0-9._-]/g, '').slice(0, 64);
  }

  private sanitizePassword(raw: string): string {
    return raw.slice(0, this.maxInputLength);
  }

  // Toggle light/dark theme from login screen
  toggleTheme(): void {
    this.themeService.toggleTheme();
    this.currentTheme.set(this.themeService.isDarkMode() ? 'dark' : 'light');
  }

  // Toggle whether password text is visible
  togglePasswordVisibility(): void {
    this.showPassword.update(value => !value);
  }

  // Handle form submission

  onSubmit(): void {
    if (this.isCurrentlyLockedOut()) {
      return;
    }

    if (this.isSubmitting()) {
      return;
    }

    // Clear previous error
    this.errorMessage.set(null);

    const username = this.sanitizeUsername(this.signInForm.value.username ?? '');
    const password = this.sanitizePassword(this.signInForm.value.password ?? '');

    this.signInForm.patchValue({ username, password }, { emitEvent: false });

    // Validate form
    if (this.signInForm.invalid) {
      // Mark all fields as touched to show validation errors
      Object.keys(this.signInForm.controls).forEach(key => {
        this.signInForm.get(key)?.markAsTouched();
      });
      return;
    }

    this.isSubmitting.set(true);

    const credentials: LoginRequest = { username, password };

    this.authService.login(credentials).subscribe({
      next: (response: any) => {
        this.isSubmitting.set(false);
        this.clearLockout();
        // Clear form on success
        this.signInForm.reset();
        
        // Connect to session WebSocket for real-time multi-device logout
        this.sessionSocket.connect();
        
        // Navigate to validated returnUrl if present, else dashboard.
        const requested = this.route.snapshot.queryParamMap.get('returnUrl');
        // Admins land on the admin dashboard; a customer still on the emailed default password
        // lands on the Account tab, where the change-password section is.
        const home = this.authService.isAdmin()
          ? '/admin'
          : this.authService.mustChangePassword()
            ? '/profile'
            : '/dashboard';
        const target = requested && requested.startsWith('/') && !requested.startsWith('//')
          ? requested
          : home;

        // The customer's stored default account becomes the one the application opens on. This never
        // fails and never waits long: if it cannot be read they simply land on their own account.
        this.preferences.applyDefaultAccountAtSignIn().subscribe({
          complete: () => this.router.navigateByUrl(target, { replaceUrl: true })
        });
      },
      error: (error: any) => {
        this.isSubmitting.set(false);
        this.registerFailedAttempt();

        if (this.isCurrentlyLockedOut()) {
          const until = this.lockoutUntil();
          if (until) {
            this.handleLockout(until);
          }
          return;
        }

        // Display error message to user
        this.errorMessage.set(error.message || 'Sign-in failed. Please try again.');
        this.appRef.tick();
      }
    });
  }

  /**
   * Get username form control
   */
  get username() {
    return this.signInForm.get('username');
  }

  /**
   * Get password form control
   */
  get password() {
    return this.signInForm.get('password');
  }

  // Check if username field has validation error and is touched  
  usernameError(): string | null {
    const control = this.username;
    if (control?.hasError('required') && control?.touched) {
      return 'Username is required';
    }
    if (control?.hasError('minlength') && control?.touched) {
      return 'Username must be at least 3 characters';
    }
    if (control?.hasError('maxlength') && control?.touched) {
      return 'Username is too long';
    }
    if (control?.hasError('pattern') && control?.touched) {
      return 'Username can use letters, numbers, dot, underscore, and hyphen only';
    }
    return null;
  }

  // Check if password field has validation error and is touched
  passwordError(): string | null {
    const control = this.password;
    if (control?.hasError('required') && control?.touched) {
      return 'Password is required';
    }
    if (control?.hasError('minlength') && control?.touched) {
      return 'Password must be at least 6 characters';
    }
    if (control?.hasError('maxlength') && control?.touched) {
      return 'Password is too long';
    }
    if (control?.hasError('spaces') && control?.touched) {
      return 'Password cannot start or end with spaces';
    }
    return null;
  }

  // Check if form can be submitted
  canSubmit(): boolean {
    return this.signInForm.valid && !this.isSubmitting() && !this.isCurrentlyLockedOut();
  }
}