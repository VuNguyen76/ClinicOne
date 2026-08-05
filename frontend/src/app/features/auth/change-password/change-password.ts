import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { apiErrorMessage, AuthApiService } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const values = control.value as { newPassword?: string; confirmPassword?: string };
  return values.newPassword === values.confirmPassword ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-change-password',
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './change-password.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangePassword {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly required = signal(this.route.snapshot.queryParamMap.get('required') === '1');

  readonly passwordForm = this.formBuilder.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirmPassword: ['', [Validators.required]],
  }, { validators: passwordsMatch });

  protected savePassword(): void {
    this.error.set('');
    this.notice.set('');
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.busy.set(true);
    this.authApi.changePassword(currentPassword, newPassword)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.passwordForm.reset();
          if (this.required()) {
            void this.router.navigateByUrl('/dashboard');
            return;
          }
          this.notice.set('Mật khẩu đã được đổi.');
        },
        error: (response) => this.showError(response),
      });
  }

  private showError(response: { status?: number; error?: { message?: string; detail?: string; title?: string } | string; message?: string; detail?: string }): void {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOnePatientName');
      void this.router.navigateByUrl('/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }
}
