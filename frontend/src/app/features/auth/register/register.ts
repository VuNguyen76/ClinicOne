import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { apiErrorMessage, AuthApiService } from '../../../core/auth/auth-api.service';
import { VietnamAddressService, VietnamAddressUnit } from '../../../core/address/vietnam-address.service';

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
export class Register implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly addressApi = inject(VietnamAddressService);

  protected readonly step = signal<RegisterStep>('phone');
  protected readonly phone = signal('');
  protected readonly notice = signal('');
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly provinces = signal<VietnamAddressUnit[]>([]);
  protected readonly districts = signal<VietnamAddressUnit[]>([]);
  protected readonly wards = signal<VietnamAddressUnit[]>([]);
  protected readonly addressLoading = signal(false);
  protected readonly today = new Date().toISOString().slice(0, 10);

  readonly phoneForm = this.formBuilder.nonNullable.group({
    phone: [this.route.snapshot.queryParamMap.get('phone') ?? '', [Validators.required, Validators.pattern(/^0\d{9}$/)]],
  });

  readonly otpForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  readonly profileForm = this.formBuilder.nonNullable.group(
    {
      fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      dateOfBirth: ['', [Validators.required]],
      gender: ['', [Validators.required]],
      address: ['', [Validators.maxLength(500)]],
      provinceCode: [''],
      provinceName: [''],
      districtCode: [''],
      districtName: [''],
      wardCode: [''],
      wardName: [''],
      streetAddress: ['', [Validators.maxLength(500)]],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );

  ngOnInit(): void {
    this.addressApi.getProvinces().subscribe({
      next: (items) => this.provinces.set(items),
      error: () => this.error.set('Không tải được danh sách tỉnh/thành. Bạn vẫn có thể đăng ký trước.'),
    });
  }

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

    const { fullName, password, dateOfBirth, gender, address, provinceCode, provinceName, districtCode, districtName,
      wardCode, wardName, streetAddress } = this.profileForm.getRawValue();
    this.busy.set(true);
    this.authApi
      .register(this.phone(), fullName.trim(), password, dateOfBirth, gender, address.trim(), provinceCode, provinceName,
        districtCode, districtName, wardCode, wardName, streetAddress.trim())
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
    const ward = this.wards().find((item) => String(item.code) === this.profileForm.controls.wardCode.value);
    this.profileForm.controls.wardName.setValue(ward?.name ?? '');
  }

  protected goToLogin(): void {
    void this.router.navigateByUrl('/login');
  }

  private clearMessages(): void {
    this.notice.set('');
    this.error.set('');
  }

  private showError(response: { error?: { message?: string; detail?: string; title?: string } | string; message?: string; detail?: string }): void {
    this.error.set(apiErrorMessage(response));
  }
}
