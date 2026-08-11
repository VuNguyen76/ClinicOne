import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule, FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import {
  ApiErrorResponse,
  AuthApiService,
  DoctorExaminationRequest,
  DoctorExaminationResponse,
  DiagnosisSuggestionResponse,
  MedicationSuggestionResponse,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';
import { auditTime } from 'rxjs';

type PrescriptionLineForm = FormGroup<{
  medicationId: FormControl<string | null>;
  medicationName: FormControl<string | null>;
  dosage: FormControl<string | null>;
  quantity: FormControl<number | null>;
  instructions: FormControl<string | null>;
}>;

type ClinicalTextField = 'reason' | 'examinationNotes' | 'diagnosis' | 'conclusion' | 'treatmentPlan';
type PrescriptionTextField = 'medicationName' | 'dosage' | 'instructions';

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
  private readonly destroyRef = inject(DestroyRef);
  private draftDirty = false;
  private autosaveRetryCount = 0;
  private autosaveRetryTimer: ReturnType<typeof setTimeout> | null = null;
  private signRequestKey: string | null = null;

  protected readonly examination = signal<DoctorExaminationResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly signing = signal(false);
  protected readonly prescriptionEnabled = signal(false);
  protected readonly followUpEnabled = signal(false);
  protected readonly confirmingSign = signal(false);
  protected readonly medicationSuggestions = signal<Record<number, MedicationSuggestionResponse[]>>({});
  protected readonly diagnosisSuggestions = signal<DiagnosisSuggestionResponse[]>([]);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly form = this.fb.group({
    reason: ['', [Validators.maxLength(2000)]],
    examinationNotes: ['', [Validators.maxLength(2000)]],
    diagnosis: ['', [Validators.maxLength(2000)]],
    conclusion: ['', [Validators.maxLength(2000)]],
    treatmentPlan: ['', [Validators.maxLength(2000)]],
    prescriptionLines: this.fb.array<PrescriptionLineForm>([]),
    followUpDate: [''],
    followUpDays: [null as number | null, [Validators.min(1), Validators.max(365)]],
    followUpNote: ['', [Validators.maxLength(500)]],
  });

  protected get prescriptionLines(): FormArray<PrescriptionLineForm> {
    return this.form.controls.prescriptionLines;
  }

  ngOnInit(): void {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.markDraftChanged());
    this.form.valueChanges.pipe(auditTime(10_000), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.autosaveDraft());
    this.destroyRef.onDestroy(() => this.clearAutosaveRetry());
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
          followUpDate: value.followUpDate ?? '',
          followUpDays: value.followUpDays ?? null,
          followUpNote: value.followUpNote ?? '',
        }, { emitEvent: false });
        value.prescriptionLines?.forEach((line) => this.prescriptionLines.push(this.createPrescriptionLine(line)));
        this.prescriptionEnabled.set(this.prescriptionLines.length > 0);
        this.followUpEnabled.set(value.followUpDays != null || Boolean(value.followUpNote));
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
    this.persistDraft(true);
  }

  protected saveDraftOnBlur(): void {
    if (this.draftDirty) this.persistDraft(false);
  }

  private persistDraft(manual: boolean): void {
    const ticketId = this.examination()?.ticketId;
    if (!ticketId || this.saving() || this.signing() || this.examination()?.requiresMedicalRecord === false) return;
    if (!this.validatePrescriptionLines()) return;
    this.saving.set(true);
    this.error.set('');
    this.notice.set('');
    this.authApi.saveDoctorExaminationDraft(ticketId, this.request()).subscribe({
      next: (value) => {
        this.examination.set(value);
        this.saving.set(false);
        this.draftDirty = false;
        this.autosaveRetryCount = 0;
        this.clearAutosaveRetry();
        this.notice.set(manual ? 'Đã lưu bản nháp' : 'Đã tự lưu bản nháp');
      },
      error: (response) => {
        this.saving.set(false);
        this.handleError(response);
        if (!manual) this.scheduleAutosaveRetry();
      },
    });
  }

  private autosaveDraft(): void {
    if (!this.draftDirty || this.loading() || this.examination()?.signedAt || this.prescriptionLines.invalid) return;
    this.persistDraft(false);
  }

  protected requestSign(): void {
    const ticketId = this.examination()?.ticketId;
    if (!ticketId || this.saving() || this.signing() || this.examination()?.status === 'COMPLETED') return;
    const required = ['reason', 'examinationNotes', 'diagnosis', 'conclusion'] as const;
    if (this.examination()?.requiresMedicalRecord !== false) {
      required.forEach((name) => this.form.controls[name].markAsTouched());
      if (required.some((name) => !this.form.controls[name].value?.trim())) {
        this.error.set('Nhập đủ lý do khám, ghi nhận khám, chẩn đoán và kết luận trước khi ký.');
        return;
      }
      if (!this.validatePrescriptionLines()) return;
      if (!this.validateFollowUpBeforeSigning()) return;
      this.ensureSignRequestKey();
      this.confirmingSign.set(true);
      return;
    }
    this.ensureSignRequestKey();
    this.sign();
  }

  protected cancelSign(): void {
    this.confirmingSign.set(false);
    this.signRequestKey = null;
  }

  protected sign(): void {
    const ticketId = this.examination()?.ticketId;
    if (!ticketId || this.saving() || this.signing() || this.examination()?.status === 'COMPLETED') return;
    this.confirmingSign.set(false);
    this.signing.set(true);
    this.error.set('');
    this.notice.set('');
    const signRequestKey = this.ensureSignRequestKey();
    this.authApi.signDoctorExamination(ticketId, this.request(), signRequestKey).subscribe({
      next: (value) => {
        this.examination.set(value);
        this.form.disable();
        this.signing.set(false);
        this.signRequestKey = null;
        this.notice.set(value.requiresMedicalRecord !== false ? 'Đã ký phiếu khám' : 'Đã kết thúc lượt khám');
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

  protected addPrescriptionLine(): void {
    if (this.prescriptionLines.length >= 20 || this.examination()?.signedAt) return;
    this.prescriptionEnabled.set(true);
    this.prescriptionLines.push(this.createPrescriptionLine());
  }

  protected removePrescriptionLine(index: number): void {
    if (this.examination()?.signedAt) return;
    this.prescriptionLines.removeAt(index);
    this.prescriptionEnabled.set(this.prescriptionLines.length > 0);
  }

  protected toggleFollowUp(): void {
    if (this.examination()?.signedAt) return;
    const enabled = !this.followUpEnabled();
    this.followUpEnabled.set(enabled);
    if (!enabled) {
      this.form.patchValue({ followUpDays: null, followUpNote: '' });
    }
  }

  protected findMedicationSuggestions(index: number): void {
    const line = this.prescriptionLines.at(index);
    this.limitPrescriptionText(index, 'medicationName', 'Tên thuốc', 200);
    line.controls.medicationId.setValue(null, { emitEvent: false });
    const query = line.controls.medicationName.value?.trim() ?? '';
    if (query.length < 2) {
      this.setMedicationSuggestions(index, []);
      return;
    }
    this.authApi.getDoctorMedicationSuggestions(query).subscribe({
      next: (items) => this.setMedicationSuggestions(index, items),
      error: () => this.setMedicationSuggestions(index, []),
    });
  }

  protected findDiagnosisSuggestions(): void {
    this.limitClinicalText('diagnosis', 'Chẩn đoán');
    const query = this.form.controls.diagnosis.value?.trim() ?? '';
    if (query.length < 2) {
      this.diagnosisSuggestions.set([]);
      return;
    }
    this.authApi.getDoctorDiagnosisSuggestions(query).subscribe({
      next: (items) => this.diagnosisSuggestions.set(items),
      error: () => this.diagnosisSuggestions.set([]),
    });
  }

  protected selectDiagnosis(suggestion: DiagnosisSuggestionResponse): void {
    this.form.controls.diagnosis.setValue(suggestion.name);
    this.diagnosisSuggestions.set([]);
  }

  protected limitClinicalText(field: ClinicalTextField, label: string): void {
    this.limitText(this.form.controls[field], label, 2000);
  }

  protected limitPrescriptionText(index: number, field: PrescriptionTextField, label: string, limit: number): void {
    this.limitText(this.prescriptionLines.at(index).controls[field], label, limit);
  }

  protected limitPrescriptionQuantity(index: number): void {
    const control = this.prescriptionLines.at(index).controls.quantity;
    if ((control.value ?? 0) <= 999) return;
    control.setValue(999);
    this.notice.set('Số lượng thuốc tối đa 999.');
  }

  protected limitFollowUpDays(): void {
    const control = this.form.controls.followUpDays;
    if ((control.value ?? 0) <= 365) return;
    control.setValue(365);
    this.notice.set('Số ngày tái khám tối đa 365.');
  }

  protected limitFollowUpNote(): void {
    this.limitText(this.form.controls.followUpNote, 'Dặn dò tái khám', 500);
  }

  protected selectMedication(index: number, medication: MedicationSuggestionResponse): void {
    const line = this.prescriptionLines.at(index);
    line.patchValue({ medicationId: medication.id, medicationName: medication.name });
    this.setMedicationSuggestions(index, []);
  }

  protected formatDate(value: string | null | undefined): string {
    if (!value) return 'Chưa cập nhật';
    const parts = value.split('-');
    return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : value;
  }

  protected formatTime(value: string | null | undefined): string {
    return value?.slice(0, 5) ?? '';
  }

  protected formatDraftSavedAt(value: string | null | undefined): string {
    if (!value) return '';
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric',
    }).format(new Date(value));
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
      prescription: '',
      prescriptionLines: value.prescriptionLines.map((line) => ({
        ...(line.medicationId ? { medicationId: line.medicationId } : {}),
        medicationName: line.medicationName ?? '',
        dosage: line.dosage ?? '',
        quantity: Number(line.quantity),
        instructions: line.instructions ?? '',
      })),
      followUpDate: value.followUpDate || null,
      followUpDays: this.followUpEnabled() ? (value.followUpDays ?? null) : null,
      followUpNote: this.followUpEnabled() ? (value.followUpNote?.trim() || null) : null,
      recordVersion: this.examination()?.recordVersion ?? null,
    };
  }

  private createPrescriptionLine(line?: DoctorExaminationResponse['prescriptionLines'][number]): PrescriptionLineForm {
    return this.fb.group({
      medicationId: [line?.medicationId ?? null],
      medicationName: [line?.medicationName ?? '', [Validators.required, Validators.maxLength(200)]],
      dosage: [line?.dosage ?? '', [Validators.required, Validators.maxLength(100)]],
      quantity: [line?.quantity ?? 1, [Validators.required, Validators.min(1), Validators.max(999)]],
      instructions: [line?.instructions ?? '', [Validators.required, Validators.maxLength(500)]],
    }) as PrescriptionLineForm;
  }

  private validatePrescriptionLines(): boolean {
    if (this.prescriptionLines.valid) return true;
    this.prescriptionLines.markAllAsTouched();
    this.error.set('Mỗi thuốc cần có tên, liều dùng, số lượng và hướng dẫn sử dụng hợp lệ.');
    return false;
  }

  private validateFollowUpBeforeSigning(): boolean {
    if (!this.followUpEnabled()) return true;
    const days = this.form.controls.followUpDays.value;
    if (days != null && Number.isInteger(days) && days >= 1 && days <= 365) return true;
    this.form.controls.followUpDays.markAsTouched();
    this.error.set('Nhập số ngày tái khám từ 1 đến 365 trước khi ký.');
    return false;
  }

  private limitText(control: FormControl<string | null>, label: string, limit: number): void {
    const value = control.value ?? '';
    if (value.length <= limit) return;
    control.setValue(value.slice(0, limit));
    this.notice.set(`${label} tối đa ${limit.toLocaleString('vi-VN')} ký tự.`);
  }

  private setMedicationSuggestions(index: number, suggestions: MedicationSuggestionResponse[]): void {
    this.medicationSuggestions.update((current) => ({ ...current, [index]: suggestions }));
  }

  private markDraftChanged(): void {
    this.draftDirty = true;
    this.signRequestKey = null;
    this.autosaveRetryCount = 0;
    this.clearAutosaveRetry();
  }

  private ensureSignRequestKey(): string {
    if (!this.signRequestKey) {
      this.signRequestKey = globalThis.crypto?.randomUUID?.()
        ?? `sign-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    }
    return this.signRequestKey;
  }

  private scheduleAutosaveRetry(): void {
    if (!this.draftDirty || this.autosaveRetryCount >= 3 || this.autosaveRetryTimer != null) return;
    this.autosaveRetryCount += 1;
    this.autosaveRetryTimer = setTimeout(() => {
      this.autosaveRetryTimer = null;
      this.autosaveDraft();
    }, 10_000);
  }

  private clearAutosaveRetry(): void {
    if (this.autosaveRetryTimer != null) {
      clearTimeout(this.autosaveRetryTimer);
      this.autosaveRetryTimer = null;
    }
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401 || response.status === 403) {
      void this.router.navigateByUrl('/staff/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }
}
