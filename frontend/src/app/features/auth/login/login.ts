import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { apiErrorMessage, AuthApiService } from '../../../core/auth/auth-api.service';

type LoginStep = 'phone' | 'password';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatIconModule],
  templateUrl: './login.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly step = signal<LoginStep>('phone');
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly phone = signal('');
  protected readonly password = signal('');
  protected readonly showPassword = signal(false);

  readonly phoneForm = this.formBuilder.nonNullable.group({
    phone: ['', [Validators.required, Validators.pattern(/^0\d{9}$/)]],
  });

  readonly passwordForm = this.formBuilder.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(72)]],
  });

  protected submitPhone(): void {
    this.error.set('');
    if (this.phoneForm.invalid) {
      this.phoneForm.markAllAsTouched();
      return;
    }

    const { phone } = this.phoneForm.getRawValue();
    this.phone.set(phone);
    this.busy.set(true);
    this.authApi
      .checkPhone(phone)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (response) => {
          if (response.accountExists) {
            this.step.set('password');
          } else {
            const returnUrl = this.safeReturnUrl();
            void this.router.navigate(['/register'], {
              queryParams: returnUrl ? { phone, returnUrl } : { phone },
            });
          }
        },
        error: (response) => this.showError(response),
      });
  }

  protected submitPassword(): void {
    this.error.set('');
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    const password = this.passwordForm.controls.password.value;
    this.password.set(password);
    this.busy.set(true);
    this.authApi
      .login(this.phone(), password)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (session) => {
          const returnUrl = this.safeReturnUrl();
          if (session.mustChangePassword) {
            void this.router.navigate(['/change-password'], {
              queryParams: returnUrl ? { required: '1', returnUrl } : { required: '1' },
            });
            return;
          }
          void this.router.navigateByUrl(returnUrl || '/dashboard');
        },
        error: (response) => this.showError(response),
      });
  }

  protected backToPhone(): void {
    this.step.set('phone');
    this.passwordForm.reset();
    this.error.set('');
  }

  private safeReturnUrl(): string | null {
    const value = this.route.snapshot.queryParamMap.get('returnUrl');
    return value && value.startsWith('/') && !value.startsWith('//') ? value : null;
  }

  private showError(response: { error?: { message?: string; detail?: string; title?: string } | string; message?: string; detail?: string }): void {
    this.error.set(apiErrorMessage(response));
  }
}
