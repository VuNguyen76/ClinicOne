import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthApiService } from '../../../core/auth/auth-api.service';

type LoginStep = 'credentials' | 'otp';

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

  protected readonly step = signal<LoginStep>('credentials');
  protected readonly notice = signal('');
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly sendingOtp = signal(false);
  protected readonly phone = signal('');
  protected readonly password = signal('');
  protected readonly cooldown = signal(0);

  readonly phoneForm = this.formBuilder.nonNullable.group({
    phone: ['', [Validators.required, Validators.pattern(/^0\d{9}$/)]],
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(72)]],
  });

  readonly otpForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  protected submitCredentials(): void {
    this.notice.set('');
    this.error.set('');
    if (this.phoneForm.invalid) {
      this.phoneForm.markAllAsTouched();
      return;
    }

    const { phone, password } = this.phoneForm.getRawValue();
    this.phone.set(phone);
    this.password.set(password);
    this.step.set('otp');
    this.notice.set('Đang gửi mã OTP. Bạn có thể nhập mã ngay khi nhận được.');
    this.sendingOtp.set(true);
    this.authApi
      .requestSmsOtp(phone, 'LOGIN')
      .pipe(finalize(() => this.sendingOtp.set(false)))
      .subscribe({
        next: (response) => {
          this.cooldown.set(response.retryAfterSeconds);
          this.notice.set('Mã OTP đã được gửi đến số điện thoại của bạn.');
        },
        error: (response) => this.showError(response),
      });
  }

  protected resendOtp(): void {
    this.notice.set('');
    this.error.set('');
    if (this.sendingOtp()) {
      this.notice.set('Mã OTP đang được gửi, vui lòng chờ trong giây lát.');
      return;
    }
    this.sendingOtp.set(true);
    this.authApi
      .requestSmsOtp(this.phone(), 'LOGIN')
      .pipe(finalize(() => this.sendingOtp.set(false)))
      .subscribe({
        next: (response) => {
          this.cooldown.set(response.retryAfterSeconds);
          this.notice.set('Mã OTP mới đã được gửi.');
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
      .loginBySmsOtp(this.phone(), this.password(), this.otpForm.controls.code.value)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => this.router.navigateByUrl('/dashboard'),
        error: (response) => this.showError(response),
      });
  }

  protected backToPhone(): void {
    this.step.set('credentials');
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
