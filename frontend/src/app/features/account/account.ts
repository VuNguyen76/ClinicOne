import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { apiErrorMessage, AuthApiService, PatientProfileResponse } from '../../core/auth/auth-api.service';

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
  private readonly router = inject(Router);

  protected readonly profile = signal<PatientProfileResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly passwordOpen = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly today = new Date().toISOString().slice(0, 10);

  readonly profileForm = this.formBuilder.nonNullable.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    dateOfBirth: ['', [Validators.required]],
    gender: ['', [Validators.required]],
    address: ['', [Validators.maxLength(500)]],
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
    const { fullName, dateOfBirth, gender, address } = this.profileForm.getRawValue();
    this.authApi.updateProfile(fullName.trim(), dateOfBirth || null, gender || null, address.trim())
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.setProfileForm(profile);
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

  protected maskedPhone(): string {
    const phone = this.profile()?.phone ?? '';
    return phone.length > 6 ? `${phone.slice(0, 3)}****${phone.slice(-3)}` : phone;
  }

  protected formatDateOfBirth(value: string | null | undefined): string {
    if (!value) {
      return 'Chưa cập nhật';
    }
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN').format(new Date(year, month - 1, day));
  }

  private loadProfile(): void {
    this.authApi.getProfile()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (profile) => {
          this.profile.set(profile);
          this.setProfileForm(profile);
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

  private setProfileForm(profile: PatientProfileResponse): void {
    this.profileForm.setValue({
      fullName: profile.fullName,
      dateOfBirth: profile.dateOfBirth ?? '',
      gender: profile.gender ?? '',
      address: profile.address ?? '',
    });
  }

  private showError(response: { status?: number; error?: { message?: string; detail?: string; title?: string } | string; message?: string; detail?: string }): void {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem('clinicOneAccessToken');
      void this.router.navigateByUrl('/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }
}
