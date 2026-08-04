import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, PatientProfileResponse } from '../../core/auth/auth-api.service';

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const group = control as AbstractControl & { value: { newPassword?: string; confirmPassword?: string } };
  return group.value.newPassword === group.value.confirmPassword ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule],
  templateUrl: './account.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Account implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly profile = signal<PatientProfileResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly passwordOpen = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  readonly profileForm = this.formBuilder.nonNullable.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(200)]],
  });

  readonly passwordForm = this.formBuilder.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirmPassword: ['', [Validators.required]],
  }, { validators: passwordsMatch });

  ngOnInit(): void {
    this.passwordOpen.set(this.route.snapshot.queryParamMap.get('changePassword') === '1');
    this.loadProfile();
  }

  protected saveProfile(): void {
    this.clearMessages();
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.busy.set(true);
    this.authApi.updateProfile(this.profileForm.controls.fullName.value.trim())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.profileForm.controls.fullName.setValue(profile.fullName);
          this.notice.set('Thông tin cá nhân đã được cập nhật.');
        },
        error: (response) => this.showError(response),
      });
  }

  protected savePassword(): void {
    this.clearMessages();
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
          this.passwordOpen.set(false);
          const currentProfile = this.profile();
          if (currentProfile) {
            this.profile.set({ ...currentProfile, mustChangePassword: false });
          }
          this.notice.set('Mật khẩu đã được đổi thành công.');
        },
        error: (response) => this.showError(response),
      });
  }

  protected togglePassword(): void {
    this.passwordOpen.update((open) => !open);
    this.clearMessages();
  }

  private loadProfile(): void {
    this.authApi.getProfile()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.profileForm.controls.fullName.setValue(profile.fullName);
          if (profile.mustChangePassword) {
            this.passwordOpen.set(true);
          }
        },
        error: (response) => this.showError(response),
      });
  }

  private clearMessages(): void {
    this.error.set('');
    this.notice.set('');
  }

  private showError(response: { error?: { message?: string } }): void {
    this.error.set(response.error?.message ?? 'Không thể xử lý yêu cầu. Vui lòng thử lại.');
  }
}
