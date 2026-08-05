import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AuthApiService, apiErrorMessage, PatientProfileItem, PatientProfileRequest } from '../../core/auth/auth-api.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';
import { VietnamAddressService, VietnamAddressUnit } from '../../core/address/vietnam-address.service';

@Component({
  selector: 'app-patient-profiles',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './patient-profiles.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientProfiles implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly addressApi = inject(VietnamAddressService);

  protected readonly profiles = signal<PatientProfileItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly provinces = signal<VietnamAddressUnit[]>([]);
  protected readonly districts = signal<VietnamAddressUnit[]>([]);
  protected readonly wards = signal<VietnamAddressUnit[]>([]);
  protected readonly addressLoading = signal(false);
  protected readonly form = this.formBuilder.nonNullable.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    relationship: ['Người thân', [Validators.required, Validators.maxLength(50)]],
    dateOfBirth: ['', [Validators.required]],
    gender: ['', [Validators.required, Validators.maxLength(20)]],
    phone: ['', [Validators.pattern(/^$|^0\d{9}$/)]],
    identityNumber: ['', [Validators.pattern(/^$|^\d{9}$|^\d{12}$/)]],
    nationality: ['Việt Nam', [Validators.maxLength(100)]],
    ethnicity: ['Kinh', [Validators.maxLength(100)]],
    address: ['', [Validators.maxLength(500)]],
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
    this.load();
  }

  protected load(): void {
    this.authApi.getPatientProfiles().subscribe({
      next: (profiles) => { this.profiles.set(profiles); this.loading.set(false); },
      error: (response) => this.handleError(response),
    });
  }

  protected startAdd(): void {
    this.editingId.set(null);
    this.form.reset({ fullName: '', relationship: 'Người thân', dateOfBirth: '', gender: '', phone: '', identityNumber: '', nationality: 'Việt Nam', ethnicity: 'Kinh', address: '', provinceCode: '', provinceName: '', districtCode: '', districtName: '', wardCode: '', wardName: '', streetAddress: '' });
    this.districts.set([]);
    this.wards.set([]);
    this.formOpen.set(true);
    this.error.set('');
  }

  protected edit(profile: PatientProfileItem): void {
    this.editingId.set(profile.id);
    this.form.reset({ fullName: profile.fullName, relationship: profile.relationship, dateOfBirth: profile.dateOfBirth, gender: profile.gender, phone: profile.phone ?? '', identityNumber: profile.identityNumber ?? '', nationality: profile.nationality ?? '', ethnicity: profile.ethnicity ?? '', address: profile.address ?? '', provinceCode: profile.provinceCode ?? '', provinceName: profile.provinceName ?? '', districtCode: profile.districtCode ?? '', districtName: profile.districtName ?? '', wardCode: profile.wardCode ?? '', wardName: profile.wardName ?? '', streetAddress: profile.streetAddress ?? '' });
    if (profile.provinceCode) this.addressApi.getDistricts(profile.provinceCode).subscribe((items) => this.districts.set(items));
    if (profile.districtCode) this.addressApi.getWards(profile.districtCode).subscribe((items) => this.wards.set(items));
    this.formOpen.set(true);
    this.error.set('');
  }

  protected cancelEdit(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
  }

  protected save(): void {
    this.error.set('');
    this.notice.set('');
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.busy.set(true);
    const request = this.form.getRawValue() as PatientProfileRequest;
    const operation = this.editingId() ? this.authApi.updatePatientProfile(this.editingId()!, request) : this.authApi.createPatientProfile(request);
    operation.subscribe({
      next: () => { this.busy.set(false); this.formOpen.set(false); this.notice.set(this.editingId() ? 'Đã cập nhật hồ sơ.' : 'Đã thêm hồ sơ.'); this.editingId.set(null); this.load(); },
      error: (response) => { this.busy.set(false); this.handleError(response); },
    });
  }

  protected remove(profile: PatientProfileItem): void {
    if (profile.primaryProfile || this.busy()) return;
    this.busy.set(true);
    this.authApi.deletePatientProfile(profile.id).subscribe({
      next: () => { this.busy.set(false); this.notice.set('Đã xóa hồ sơ.'); this.load(); },
      error: (response) => { this.busy.set(false); this.handleError(response); },
    });
  }

  protected formatDate(value: string): string {
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN').format(new Date(year, month - 1, day));
  }

  protected provinceChanged(): void {
    const provinceCode = this.form.controls.provinceCode.value;
    const province = this.provinces().find((item) => String(item.code) === provinceCode);
    this.form.patchValue({ provinceName: province?.name ?? '', districtCode: '', districtName: '', wardCode: '', wardName: '' });
    this.districts.set([]);
    this.wards.set([]);
    if (provinceCode) {
      this.addressLoading.set(true);
      this.addressApi.getDistricts(provinceCode).subscribe({
        next: (items) => this.districts.set(items),
        complete: () => this.addressLoading.set(false),
        error: () => { this.addressLoading.set(false); this.error.set('Không tải được danh sách quận/huyện.'); },
      });
    }
  }

  protected districtChanged(): void {
    const districtCode = this.form.controls.districtCode.value;
    const district = this.districts().find((item) => String(item.code) === districtCode);
    this.form.patchValue({ districtName: district?.name ?? '', wardCode: '', wardName: '' });
    this.wards.set([]);
    if (districtCode) {
      this.addressLoading.set(true);
      this.addressApi.getWards(districtCode).subscribe({
        next: (items) => this.wards.set(items),
        complete: () => this.addressLoading.set(false),
        error: () => { this.addressLoading.set(false); this.error.set('Không tải được danh sách xã/phường.'); },
      });
    }
  }

  protected wardChanged(): void {
    const ward = this.wards().find((item) => String(item.code) === this.form.controls.wardCode.value);
    this.form.controls.wardName.setValue(ward?.name ?? '');
  }

  private loadProvinces(): void {
    this.addressApi.getProvinces().subscribe({
      next: (items) => this.provinces.set(items),
      error: () => this.error.set('Không tải được danh sách tỉnh/thành.'),
    });
  }

  private handleError(response: ApiErrorResponse & { status: number }): void {
    this.loading.set(false);
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOnePatientName');
      void this.router.navigateByUrl('/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }
}
