import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthApiService } from '../../../core/auth/auth-api.service';

type LoginStep = 'phone' | 'otp';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
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
  protected readonly phone = signal('');
  protected readonly cooldown = signal(0);

  readonly phoneForm = this.formBuilder.nonNullable.group({
    phone: ['', [Validators.required, Validators.pattern(/^0\d{9}$/)]],
  });

  readonly otpForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  protected submitPhone(): void {
    this.notice.set('');
    this.error.set('');
    if (this.phoneForm.invalid) {
      this.phoneForm.markAllAsTouched();
      return;
    }

    const phone = this.phoneForm.controls.phone.value;
    this.busy.set(true);
    this.authApi
      .requestSmsOtp(phone, 'LOGIN')
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (response) => {
          this.phone.set(phone);
          this.step.set('otp');
          this.cooldown.set(response.retryAfterSeconds);
          this.notice.set('Mã OTP đã được gửi đến số điện thoại của bạn.');
        },
        error: (response) => this.showError(response),
      });
  }

  protected submitOtp(): void {
    this.notice.set('');
    this.error.set('');
    if (this.otpForm.invalid) {
      this.otpForm.markAllAsTouched();
      return;
    }

    this.busy.set(true);
    this.authApi
      .loginBySmsOtp(this.phone(), this.otpForm.controls.code.value)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.router.navigateByUrl('/dashboard'),
        error: (response) => this.showError(response),
      });
  }

  protected backToPhone(): void {
    this.step.set('phone');
    this.otpForm.reset();
    this.notice.set('');
    this.error.set('');
  }

  protected showNotReadyMessage(action: string): void {
    this.notice.set(`${action} sẽ được bổ sung trong phiên bản tiếp theo.`);
  }

  private showError(response: { error?: { message?: string } }): void {
    this.error.set(response.error?.message ?? 'Không thể xử lý yêu cầu. Vui lòng thử lại.');
  }
}
