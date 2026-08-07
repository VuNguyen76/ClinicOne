import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import {
  ApiErrorResponse,
  AuthApiService,
  DoctorExaminationRequest,
  DoctorExaminationResponse,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';

@Component({
  selector: 'app-doctor-examination',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './doctor-examination.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DoctorExamination implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authApi = inject(AuthApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly examination = signal<DoctorExaminationResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly signing = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly form = this.fb.group({
    reason: ['', [Validators.maxLength(500)]],
    examinationNotes: ['', [Validators.maxLength(4000)]],
    diagnosis: ['', [Validators.maxLength(1000)]],
    conclusion: ['', [Validators.maxLength(2000)]],
    treatmentPlan: ['', [Validators.maxLength(2000)]],
    prescription: ['', [Validators.maxLength(4000)]],
    followUpDate: [''],
  });

  ngOnInit(): void {
    const ticketId = this.route.snapshot.paramMap.get('ticketId');
    if (!ticketId) {
      this.error.set('Không tìm thấy lượt khám.');
      this.loading.set(false);
      return;
    }
    this.authApi.getDoctorExamination(ticketId).subscribe({
      next: (value) => {
        this.examination.set(value);
        this.form.patchValue({
          reason: value.reason ?? '',
          examinationNotes: value.examinationNotes ?? '',
          diagnosis: value.diagnosis ?? '',
          conclusion: value.conclusion ?? '',
          treatmentPlan: value.treatmentPlan ?? '',
          prescription: value.prescription ?? '',
          followUpDate: value.followUpDate ?? '',
        });
        if (value.signedAt) this.form.disable();
        this.loading.set(false);
      },
      error: (response) => {
        this.loading.set(false);
        this.handleError(response);
      },
    });
  }

  protected saveDraft(): void {
    const ticketId = this.examination()?.ticketId;
    if (!ticketId || this.saving() || this.signing()) return;
    this.saving.set(true);
    this.error.set('');
    this.notice.set('');
    this.authApi.saveDoctorExaminationDraft(ticketId, this.request()).subscribe({
      next: (value) => {
        this.examination.set(value);
        this.saving.set(false);
        this.notice.set('Đã lưu bản nháp');
      },
      error: (response) => {
        this.saving.set(false);
        this.handleError(response);
      },
    });
  }

  protected sign(): void {
    const ticketId = this.examination()?.ticketId;
    if (!ticketId || this.saving() || this.signing()) return;
    const required = ['reason', 'examinationNotes', 'diagnosis', 'conclusion'] as const;
    required.forEach((name) => this.form.controls[name].markAsTouched());
    if (required.some((name) => !this.form.controls[name].value?.trim())) {
      this.error.set('Nhập đủ lý do khám, ghi nhận khám, chẩn đoán và kết luận trước khi ký.');
      return;
    }
    this.signing.set(true);
    this.error.set('');
    this.notice.set('');
    this.authApi.signDoctorExamination(ticketId, this.request()).subscribe({
      next: (value) => {
        this.examination.set(value);
        this.form.disable();
        this.signing.set(false);
        this.notice.set('Đã ký phiếu khám');
      },
      error: (response) => {
        this.signing.set(false);
        this.handleError(response);
      },
    });
  }

  protected back(): void {
    void this.router.navigateByUrl('/doctor');
  }

  protected formatDate(value: string | null | undefined): string {
    if (!value) return 'Chưa cập nhật';
    const parts = value.split('-');
    return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : value;
  }

  protected formatTime(value: string | null | undefined): string {
    return value?.slice(0, 5) ?? '';
  }

  protected fieldInvalid(name: 'reason' | 'examinationNotes' | 'diagnosis' | 'conclusion'): boolean {
    const control = this.form.controls[name];
    return control.touched && !control.value?.trim();
  }

  private request(): DoctorExaminationRequest {
    const value = this.form.getRawValue();
    return {
      reason: value.reason ?? '',
      examinationNotes: value.examinationNotes ?? '',
      diagnosis: value.diagnosis ?? '',
      conclusion: value.conclusion ?? '',
      treatmentPlan: value.treatmentPlan ?? '',
      prescription: value.prescription ?? '',
      followUpDate: value.followUpDate || null,
    };
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401 || response.status === 403) {
      void this.router.navigateByUrl('/staff/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }
}
