import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { apiErrorMessage, AuthApiService, PatientProfileResponse } from '../../core/auth/auth-api.service';
import { VietnamAddressService, VietnamAddressUnit } from '../../core/address/vietnam-address.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';
import { AccountNav } from '../../shared/account-nav/account-nav';
import { clinicTodayIso } from '../../core/time/clinic-time';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu, AccountNav],
  templateUrl: './account.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Account implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly addressApi = inject(VietnamAddressService);

  protected readonly profile = signal<PatientProfileResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly today = clinicTodayIso();
  protected readonly provinces = signal<VietnamAddressUnit[]>([]);
  protected readonly districts = signal<VietnamAddressUnit[]>([]);
  protected readonly wards = signal<VietnamAddressUnit[]>([]);
  protected readonly addressLoading = signal(false);

  readonly profileForm = this.formBuilder.nonNullable.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    dateOfBirth: ['', [Validators.required]],
    gender: ['', [Validators.required]],
    address: ['', [Validators.maxLength(500)]],
    identityNumber: ['', [Validators.pattern(/^(|\d{9}|\d{12})$/)]],
    nationality: ['Việt Nam', [Validators.maxLength(100)]],
    ethnicity: ['Kinh', [Validators.maxLength(100)]],
    provinceCode: [''],
    provinceName: [''],
    districtCode: [''],
    districtName: [''],
    wardCode: [''],
    wardName: [''],
    streetAddress: ['', [Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.loadProvinces();
    this.loadProfile();
  }

  protected saveProfile(): void {
    this.clearMessages();
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.busy.set(true);
    const values = this.profileForm.getRawValue();
    const address = this.composeAddress(values.streetAddress, values.wardName, values.districtName,
      values.provinceName, values.address);
    this.authApi.updateProfile(
      values.fullName.trim(), values.dateOfBirth || null, values.gender || null, address,
      values.identityNumber.trim(), values.nationality.trim(), values.ethnicity.trim(), values.provinceCode,
      values.provinceName, values.districtCode, values.districtName, values.wardCode, values.wardName,
      values.streetAddress.trim(),
    )
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (profile) => {
          const normalizedProfile = this.withComposedAddress(profile);
          this.profile.set(normalizedProfile);
          this.setProfileForm(normalizedProfile);
          this.notice.set('Thông tin cá nhân đã được cập nhật.');
        },
        error: (response) => this.showError(response),
      });
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

  protected provinceChanged(): void {
    const provinceCode = this.profileForm.controls.provinceCode.value;
    const province = this.provinces().find((item) => String(item.code) === provinceCode);
    this.profileForm.patchValue({ provinceName: province?.name ?? '', districtCode: '', districtName: '', wardCode: '', wardName: '' });
    this.districts.set([]);
    this.wards.set([]);
    if (provinceCode) {
      this.addressLoading.set(true);
      this.addressApi.getDistricts(provinceCode).pipe(finalize(() => this.addressLoading.set(false))).subscribe({
        next: (items) => this.districts.set(items),
        error: () => this.error.set('Không tải được danh sách quận/huyện. Vui lòng thử lại.'),
      });
    }
  }

  protected districtChanged(): void {
    const districtCode = this.profileForm.controls.districtCode.value;
    const district = this.districts().find((item) => String(item.code) === districtCode);
    this.profileForm.patchValue({ districtName: district?.name ?? '', wardCode: '', wardName: '' });
    this.wards.set([]);
    if (districtCode) {
      this.addressLoading.set(true);
      this.addressApi.getWards(districtCode).pipe(finalize(() => this.addressLoading.set(false))).subscribe({
        next: (items) => this.wards.set(items),
        error: () => this.error.set('Không tải được danh sách xã/phường. Vui lòng thử lại.'),
      });
    }
  }

  protected wardChanged(): void {
    const wardCode = this.profileForm.controls.wardCode.value;
    const ward = this.wards().find((item) => String(item.code) === wardCode);
    this.profileForm.controls.wardName.setValue(ward?.name ?? '');
  }

  private loadProfile(): void {
    this.authApi.getProfile()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (profile) => {
          const normalizedProfile = this.withComposedAddress(profile);
          this.profile.set(normalizedProfile);
          this.setProfileForm(normalizedProfile);
        },
        error: (response) => this.showError(response),
      });
  }

  private loadProvinces(): void {
    this.addressApi.getProvinces().subscribe({
      next: (items) => this.provinces.set(items),
      error: () => this.error.set('Không tải được danh sách tỉnh/thành. Bạn vẫn có thể cập nhật thông tin khác.'),
    });
  }

  private clearMessages(): void {
    this.error.set('');
    this.notice.set('');
  }

  private setProfileForm(profile: PatientProfileResponse): void {
    const address = this.composeAddress(profile.streetAddress, profile.wardName, profile.districtName,
      profile.provinceName, profile.address);
    this.profileForm.setValue({
      fullName: profile.fullName,
      dateOfBirth: profile.dateOfBirth ?? '',
      gender: profile.gender ?? '',
      address,
      identityNumber: profile.identityNumber ?? '',
      nationality: profile.nationality ?? 'Việt Nam',
      ethnicity: profile.ethnicity ?? 'Kinh',
      provinceCode: profile.provinceCode ?? '',
      provinceName: profile.provinceName ?? '',
      districtCode: profile.districtCode ?? '',
      districtName: profile.districtName ?? '',
      wardCode: profile.wardCode ?? '',
      wardName: profile.wardName ?? '',
      streetAddress: profile.streetAddress ?? '',
    });
    if (profile.provinceCode) {
      this.addressApi.getDistricts(profile.provinceCode).subscribe((items) => this.districts.set(items));
    }
    if (profile.districtCode) {
      this.addressApi.getWards(profile.districtCode).subscribe((items) => this.wards.set(items));
    }
  }

  private composeAddress(streetAddress: string | null | undefined, wardName: string | null | undefined,
                          districtName: string | null | undefined, provinceName: string | null | undefined,
                          fallback: string | null | undefined = ''): string {
    const parts = [streetAddress, wardName, districtName, provinceName]
      .map((value) => value?.trim() ?? '')
      .filter(Boolean);
    return parts.length ? parts.join(', ') : (fallback?.trim() ?? '');
  }

  private withComposedAddress(profile: PatientProfileResponse): PatientProfileResponse {
    return { ...profile, address: this.composeAddress(profile.streetAddress, profile.wardName,
      profile.districtName, profile.provinceName, profile.address) };
  }

  private showError(response: { status?: number; error?: { message?: string; detail?: string; title?: string } | string; message?: string; detail?: string }): void {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOnePatientName');
      sessionStorage.removeItem('clinicOneSessionType');
      void this.router.navigateByUrl('/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }
}
