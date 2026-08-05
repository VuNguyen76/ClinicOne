import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AuthApiService, apiErrorMessage, PatientProfileItem, PatientProfileRequest } from '../../core/auth/auth-api.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';

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

  protected readonly profiles = signal<PatientProfileItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
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
  });

  ngOnInit(): void {
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
    this.form.reset({ fullName: '', relationship: 'Người thân', dateOfBirth: '', gender: '', phone: '', identityNumber: '', nationality: 'Việt Nam', ethnicity: 'Kinh', address: '' });
    this.formOpen.set(true);
    this.error.set('');
  }

  protected edit(profile: PatientProfileItem): void {
    this.editingId.set(profile.id);
    this.form.reset({ fullName: profile.fullName, relationship: profile.relationship, dateOfBirth: profile.dateOfBirth, gender: profile.gender, phone: profile.phone ?? '', identityNumber: profile.identityNumber ?? '', nationality: profile.nationality ?? '', ethnicity: profile.ethnicity ?? '', address: profile.address ?? '' });
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
