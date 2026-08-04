import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthApiService } from '../../../core/auth/auth-api.service';

type RegisterStep = 'phone' | 'otp' | 'profile' | 'done';

function passwordsMatch(control: { value: { password: string; confirmPassword: string } }): ValidationErrors | null {
  return control.value.password === control.value.confirmPassword ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Register {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly step = signal<RegisterStep>('phone');
  protected readonly phone = signal('');
  protected readonly notice = signal('');
  protected readonly error = signal('');
  protected readonly busy = signal(false);

  readonly phoneForm = this.formBuilder.nonNullable.group({
    phone: [this.route.snapshot.queryParamMap.get('phone') ?? '', [Validators.required, Validators.pattern(/^0\d{9}$/)]],
  });

  readonly otpForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  readonly profileForm = this.formBuilder.nonNullable.group(
    {
      fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(200)]],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  protected submitPhone(): void {
    this.clearMessages();
    if (this.phoneForm.invalid) {
      this.phoneForm.markAllAsTouched();
      return;
    }

    const phone = this.phoneForm.controls.phone.value;
    this.busy.set(true);
    this.authApi
      .requestSmsOtp(phone, 'REGISTRATION')
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.phone.set(phone);
          this.step.set('otp');
          this.notice.set('Mã OTP đã được gửi đến số điện thoại của bạn.');
        },
        error: (response) => this.showError(response),
      });
  }

  protected submitOtp(): void {
    this.clearMessages();
    if (this.otpForm.invalid) {
      this.otpForm.markAllAsTouched();
      return;
    }

    this.busy.set(true);
    this.authApi
      .verifySmsOtp(this.phone(), 'REGISTRATION', this.otpForm.controls.code.value)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.step.set('profile');
          this.notice.set('Số điện thoại đã được xác thực.');
        },
        error: (response) => this.showError(response),
      });
  }

  protected submitProfile(): void {
    this.clearMessages();
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }

    const { fullName, password } = this.profileForm.getRawValue();
    this.busy.set(true);
    this.authApi
      .register(this.phone(), fullName.trim(), password)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.step.set('done');
          this.notice.set('Tạo tài khoản thành công.');
        },
        error: (response) => this.showError(response),
      });
  }

  protected backToPhone(): void {
    this.step.set('phone');
    this.otpForm.reset();
    this.profileForm.reset();
    this.clearMessages();
  }

  protected backToOtp(): void {
    this.step.set('otp');
    this.clearMessages();
  }

  protected goToLogin(): void {
    void this.router.navigateByUrl('/login');
  }

  private clearMessages(): void {
    this.notice.set('');
    this.error.set('');
  }

  private showError(response: { error?: { message?: string } }): void {
    this.error.set(response.error?.message ?? 'Không thể xử lý yêu cầu. Vui lòng thử lại.');
  }
}
