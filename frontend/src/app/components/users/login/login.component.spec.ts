import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideRouter, Router, Routes } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/auth/auth.service';
import { SessionSocketService } from '../../../core/auth/session-socket.service';
import { PreferencesService } from '../../../core/services/preferences.service';
import { ThemeService } from '../../../core/theme/theme.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: any;
  let preferencesService: { applyDefaultAccountAtSignIn: ReturnType<typeof vi.fn> };
  let router: Router;
  let navigateByUrlSpy: ReturnType<typeof vi.spyOn>;

  const testRoutes: Routes = [
    { path: 'sign-in', component: LoginComponent },
    { path: 'dashboard', component: LoginComponent }, // Dummy route for testing
    { path: '', component: LoginComponent }
  ];

  beforeEach(async () => {
    vi.useRealTimers();
    sessionStorage.clear();

    authService = {
      login: vi.fn(),
      getToken: vi.fn().mockReturnValue('test-token'),
      isAuthenticated: vi.fn().mockReturnValue(true),
      isAdmin: vi.fn().mockReturnValue(false),
      mustChangePassword: vi.fn().mockReturnValue(false)
    };

    preferencesService = { applyDefaultAccountAtSignIn: vi.fn().mockReturnValue(of(undefined)) };

    const sessionSocketMock = {
      connect: vi.fn()
    };

    const themeMock = {
      isDarkMode: vi.fn().mockReturnValue(false),
      toggleTheme: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent, ReactiveFormsModule],
      providers: [
        provideRouter(testRoutes),
        { provide: AuthService, useValue: authService },
        { provide: SessionSocketService, useValue: sessionSocketMock },
        { provide: PreferencesService, useValue: preferencesService },
        { provide: ThemeService, useValue: themeMock }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    navigateByUrlSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.useRealTimers();
  });

  describe('Form Validation', () => {
    it('should disable submit button with empty fields', () => {
      fixture.detectChanges();
      expect(component.canSubmit()).toBe(false);
    });

    it('should validate username field', () => {
      const usernameControl = component.signInForm.get('username');
      usernameControl?.setValue('');
      usernameControl?.markAsTouched();
      expect(component.usernameError()).toBe('Username is required');
    });

    it('should validate username minimum length', () => {
      const usernameControl = component.signInForm.get('username');
      usernameControl?.setValue('ab');
      usernameControl?.markAsTouched();
      expect(component.usernameError()).toBe('Username must be at least 3 characters');
    });

    it('should validate password field', () => {
      const passwordControl = component.signInForm.get('password');
      passwordControl?.setValue('');
      passwordControl?.markAsTouched();
      expect(component.passwordError()).toBe('Password is required');
    });

    it('should validate password minimum length', () => {
      const passwordControl = component.signInForm.get('password');
      passwordControl?.setValue('12345');
      passwordControl?.markAsTouched();
      expect(component.passwordError()).toBe('Password must be at least 6 characters');
    });

    it('should enable submit button with valid credentials', () => {
      component.signInForm.patchValue({
        username: 'testuser',
        password: 'password123'
      });
      expect(component.canSubmit()).toBe(true);
    });

    it('should reject username with unicode or unsupported special characters', () => {
      const usernameControl = component.signInForm.get('username');
      usernameControl?.setValue('teστ$user');
      usernameControl?.markAsTouched();
      expect(component.usernameError()).toContain('letters, numbers');
    });

    it('should reject password with leading or trailing spaces', () => {
      const passwordControl = component.signInForm.get('password');
      passwordControl?.setValue(' password123 ');
      passwordControl?.markAsTouched();
      expect(component.passwordError()).toContain('cannot start or end with spaces');
    });
  });

  describe('Form Submission', () => {
    it('should not submit form with invalid credentials', () => {
      component.signInForm.patchValue({
        username: '',
        password: ''
      });
      component.onSubmit();
      expect(authService.login).not.toHaveBeenCalled();
    });

    it('should submit form with valid credentials', async () => {
      const loginResponse = {
        token: 'test-token-123',
        user: {
          id: '1',
          username: 'testuser',
          email: 'test@example.com'
        }
      };

      authService.login.mockReturnValue(of(loginResponse));

      component.signInForm.patchValue({
        username: 'testuser',
        password: 'password123'
      });

      component.onSubmit();

      expect(authService.login).toHaveBeenCalledWith({
        username: 'testuser',
        password: 'password123'
      });

      await fixture.whenStable();

      expect(navigateByUrlSpy).toHaveBeenCalledWith('/dashboard', { replaceUrl: true });
      expect(component.signInForm.value).toEqual({ username: null, password: null });
    });

    it('applies the stored default account before it navigates', async () => {
      const order: string[] = [];
      preferencesService.applyDefaultAccountAtSignIn.mockImplementation(() => {
        order.push('default-account');
        return of(undefined);
      });
      navigateByUrlSpy.mockImplementation(() => {
        order.push('navigate');
        return Promise.resolve(true);
      });
      authService.login.mockReturnValue(of({ accessToken: 't' }));
      component.signInForm.patchValue({ username: 'testuser', password: 'password123' });

      component.onSubmit();
      await fixture.whenStable();

      expect(order).toEqual(['default-account', 'navigate']);
    });

    it('waits for the stored default before navigating, then goes where it was going', async () => {
      const pending = new Subject<void>();
      preferencesService.applyDefaultAccountAtSignIn.mockReturnValue(pending);
      authService.login.mockReturnValue(of({ accessToken: 't' }));
      component.signInForm.patchValue({ username: 'testuser', password: 'password123' });

      component.onSubmit();
      expect(navigateByUrlSpy).not.toHaveBeenCalled();

      pending.next();
      pending.complete();
      expect(navigateByUrlSpy).toHaveBeenCalledWith('/dashboard', { replaceUrl: true });
    });

    it('should trim and sanitize username before submit', async () => {
      const loginResponse = {
        token: 'test-token-123',
        user: {
          id: '1',
          username: 'test.user',
          email: 'test@example.com'
        }
      };

      authService.login.mockReturnValue(of(loginResponse));

      component.signInForm.patchValue({
        username: '  test.user  ',
        password: 'password123'
      });

      component.onSubmit();

      expect(authService.login).toHaveBeenCalledWith({
        username: 'test.user',
        password: 'password123'
      });
    });

    it('should display error message on failed login', async () => {
      const error = new Error('Invalid username or password. Please try again.');
      authService.login.mockReturnValue(throwError(() => error));

      component.signInForm.patchValue({
        username: 'testuser',
        password: 'wrongpassword'
      });

      component.onSubmit();

      await fixture.whenStable();

      expect(component.errorMessage()).toBe('Invalid username or password. Please try again.');
      expect(component.isSubmitting()).toBe(false);
    });

    it('should disable submit button during submission', async () => {
      const loginResponse = {
        token: 'test-token-123',
        user: {
          id: '1',
          username: 'testuser',
          email: 'test@example.com'
        }
      };

      const loginSubject = new Subject<any>();
      authService.login.mockReturnValue(loginSubject.asObservable());

      component.signInForm.patchValue({
        username: 'testuser',
        password: 'password123'
      });

      expect(component.isSubmitting()).toBe(false);
      component.onSubmit();
      expect(component.isSubmitting()).toBe(true);

      loginSubject.next(loginResponse);
      loginSubject.complete();

      await fixture.whenStable();
      expect(component.isSubmitting()).toBe(false);
    });

    it('should ignore duplicate submit clicks while request is in flight', () => {
      const loginSubject = new Subject<any>();
      authService.login.mockReturnValue(loginSubject.asObservable());

      component.signInForm.patchValue({
        username: 'testuser',
        password: 'password123'
      });

      component.onSubmit();
      component.onSubmit();

      expect(authService.login).toHaveBeenCalledTimes(1);
    });

    it('should clear previous error on new submission attempt', () => {
      component.errorMessage.set('Previous error');
      
      component.signInForm.patchValue({
        username: '',
        password: ''
      });

      component.onSubmit();

      expect(component.errorMessage()).toBeNull();
    });

    it('should lock sign-in after 5 failed attempts with 429 style message', () => {
      authService.login.mockReturnValue(throwError(() => new Error('Invalid username or password. Please try again.')));

      component.signInForm.patchValue({
        username: 'testuser',
        password: 'password123'
      });

      for (let i = 0; i < 5; i += 1) {
        component.onSubmit();
      }

      expect(component.errorMessage()).toContain('Too many attempts');
      expect(component.canSubmit()).toBe(false);

      component.onSubmit();
      expect(authService.login).toHaveBeenCalledTimes(5);
    });

    it('should allow sign-in again after lockout timeout', () => {
      vi.useFakeTimers();
      authService.login.mockReturnValue(throwError(() => new Error('Invalid username or password. Please try again.')));

      component.signInForm.patchValue({
        username: 'testuser',
        password: 'password123'
      });

      for (let i = 0; i < 5; i += 1) {
        component.onSubmit();
      }

      expect(component.canSubmit()).toBe(false);
      expect(authService.login).toHaveBeenCalledTimes(5);

      vi.advanceTimersByTime(60_001);
      authService.login.mockClear();
      authService.login.mockReturnValue(of({ token: 'new-token' }));

      component.onSubmit();

      expect(authService.login).toHaveBeenCalledTimes(1);
      expect(component.failedAttempts()).toBe(0);
    });
  });

  describe('Test Identifiers', () => {
    it('should have stable test identifier for username input', () => {
      fixture.detectChanges();
      const usernameInput = fixture.nativeElement.querySelector('[data-testid="username-input"]');
      expect(usernameInput).toBeTruthy();
    });

    it('should have stable test identifier for password input', () => {
      fixture.detectChanges();
      const passwordInput = fixture.nativeElement.querySelector('[data-testid="password-input"]');
      expect(passwordInput).toBeTruthy();
    });

    it('should have stable test identifier for submit button', () => {
      fixture.detectChanges();
      const submitButton = fixture.nativeElement.querySelector('[data-testid="submit-button"]');
      expect(submitButton).toBeTruthy();
    });

    it('should have stable test identifier for error message', async () => {
      const error = new Error('Test error');
      authService.login.mockReturnValue(throwError(() => error));

      component.signInForm.patchValue({
        username: 'testuser',
        password: 'password123'
      });

      component.onSubmit();

      await fixture.whenStable();
      fixture.detectChanges();
      const errorBanner = fixture.nativeElement.querySelector('[data-testid="error-message"]');
      expect(errorBanner).toBeTruthy();
    });
  });
});