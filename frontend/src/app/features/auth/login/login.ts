import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { apiErrorMessage, AuthApiService } from '../../../core/auth/auth-api.service';

type LoginStep = 'phone' | 'password';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly step = signal<LoginStep>('phone');
  protected readonly notice = signal('');
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly showRegister = signal(false);
  protected readonly phone = signal('');
  protected readonly password = signal('');

  readonly phoneForm = this.formBuilder.nonNullable.group({
    phone: ['', [Validators.required, Validators.pattern(/^0\d{9}$/)]],
  });

  readonly passwordForm = this.formBuilder.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(72)]],
  });

  protected submitPhone(): void {
    this.notice.set('');
    this.error.set('');
    this.showRegister.set(false);
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
            this.showRegister.set(true);
          }
        },
        error: (response) => this.showError(response),
      });
  }

  protected submitPassword(): void {
    this.notice.set('');
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
        next: (session) => this.router.navigateByUrl(session.mustChangePassword ? '/change-password?required=1' : '/dashboard'),
        error: (response) => this.showError(response),
      });
  }

  protected backToPhone(): void {
    this.step.set('phone');
    this.passwordForm.reset();
    this.showRegister.set(false);
    this.notice.set('');
    this.error.set('');
  }

  protected showNotReadyMessage(action: string): void {
    this.notice.set(`${action} sẽ được bổ sung trong phiên bản tiếp theo.`);
  }

  private showError(response: { error?: { message?: string; detail?: string; title?: string } | string; message?: string; detail?: string }): void {
    this.error.set(apiErrorMessage(response));
  }
}
