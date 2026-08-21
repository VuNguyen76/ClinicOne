import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { apiErrorMessage, AuthApiService } from '../../../core/auth/auth-api.service';

type RecoveryStep = 'phone' | 'otp' | 'password' | 'done';

function passwordsMatch(control: { value: { newPassword: string; confirmPassword: string } }): ValidationErrors | null {
  return control.value.newPassword === control.value.confirmPassword ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-recover-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule],
  templateUrl: './recover-password.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecoverPassword {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);

  protected readonly step = signal<RecoveryStep>('phone');
  protected readonly phone = signal('');
  protected readonly busy = signal(false);
  protected readonly notice = signal('');
  protected readonly error = signal('');
  protected readonly showNewPassword = signal(false);
  protected readonly showConfirmPassword = signal(false);

  readonly phoneForm = this.formBuilder.nonNullable.group({
    phone: ['', [Validators.required, Validators.pattern(/^0\d{9}$/)]],
  });

  readonly otpForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  readonly passwordForm = this.formBuilder.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirmPassword: ['', [Validators.required]],
  }, { validators: passwordsMatch });

  protected requestOtp(): void {
    if (this.busy()) {
      return;
    }
    this.clearMessages();
    if (this.phoneForm.invalid) {
      this.phoneForm.markAllAsTouched();
      return;
    }
    const phone = this.phoneForm.controls.phone.value;
    this.phone.set(phone);
    this.busy.set(true);
    this.authApi.requestSmsOtp(phone, 'RECOVERY').pipe(finalize(() => this.busy.set(false))).subscribe({
      next: () => {
        this.step.set('otp');
        this.notice.set('Mã OTP đã được gửi.');
      },
      error: (response) => this.showError(response),
    });
  }

  protected verifyOtp(): void {
    if (this.busy()) {
      return;
    }
    this.clearMessages();
    if (this.otpForm.invalid) {
      this.otpForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.authApi.verifySmsOtp(this.phone(), 'RECOVERY', this.otpForm.controls.code.value)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.step.set('password');
          this.notice.set('Số điện thoại đã được xác thực.');
        },
        error: (response) => this.showError(response),
      });
  }

  protected resetPassword(): void {
    if (this.busy()) {
      return;
    }
    this.clearMessages();
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const { newPassword, confirmPassword } = this.passwordForm.getRawValue();
    this.busy.set(true);
    this.authApi.recoverPassword(this.phone(), newPassword, confirmPassword)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.step.set('done');
          this.notice.set('Mật khẩu đã được đổi.');
        },
        error: (response) => this.showError(response),
      });
  }

  protected back(step: RecoveryStep): void {
    this.clearMessages();
    this.step.set(step);
  }

  private clearMessages(): void {
    this.notice.set('');
    this.error.set('');
  }

  private showError(response: { error?: { message?: string; detail?: string; title?: string } | string; message?: string; detail?: string }): void {
    this.error.set(apiErrorMessage(response));
  }
}
