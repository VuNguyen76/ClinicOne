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
  MedicationSuggestionResponse,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';
import { debounceTime } from 'rxjs';

type PrescriptionLineForm = FormGroup<{
  medicationId: FormControl<string | null>;
  medicationName: FormControl<string | null>;
  dosage: FormControl<string | null>;
  quantity: FormControl<number | null>;
  instructions: FormControl<string | null>;
}>;

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

  protected readonly examination = signal<DoctorExaminationResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly signing = signal(false);
  protected readonly prescriptionEnabled = signal(false);
  protected readonly medicationSuggestions = signal<Record<number, MedicationSuggestionResponse[]>>({});
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly form = this.fb.group({
    reason: ['', [Validators.maxLength(500)]],
    examinationNotes: ['', [Validators.maxLength(4000)]],
    diagnosis: ['', [Validators.maxLength(1000)]],
    conclusion: ['', [Validators.maxLength(2000)]],
    treatmentPlan: ['', [Validators.maxLength(2000)]],
    prescriptionLines: this.fb.array<PrescriptionLineForm>([]),
    followUpDate: [''],
  });

  protected get prescriptionLines(): FormArray<PrescriptionLineForm> {
    return this.form.controls.prescriptionLines;
  }

  ngOnInit(): void {
    this.form.valueChanges.pipe(debounceTime(1200), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.autosaveDraft());
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
        }, { emitEvent: false });
        value.prescriptionLines?.forEach((line) => this.prescriptionLines.push(this.createPrescriptionLine(line)));
        this.prescriptionEnabled.set(this.prescriptionLines.length > 0);
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
    if (!ticketId || this.saving() || this.signing() || this.examination()?.requiresMedicalRecord === false) return;
    if (!this.validatePrescriptionLines()) return;
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

  private autosaveDraft(): void {
    const ticketId = this.examination()?.ticketId;
    if (!ticketId || this.loading() || this.saving() || this.signing() || this.examination()?.signedAt
      || this.examination()?.requiresMedicalRecord === false || this.prescriptionLines.invalid) return;
    this.saving.set(true);
    this.error.set('');
    this.authApi.saveDoctorExaminationDraft(ticketId, this.request()).subscribe({
      next: (value) => {
        this.examination.set(value);
        this.saving.set(false);
        this.notice.set('Đã tự lưu bản nháp');
      },
      error: (response) => {
        this.saving.set(false);
        this.handleError(response);
      },
    });
  }

  protected sign(): void {
    const ticketId = this.examination()?.ticketId;
    if (!ticketId || this.saving() || this.signing() || this.examination()?.status === 'COMPLETED') return;
    const required = ['reason', 'examinationNotes', 'diagnosis', 'conclusion'] as const;
    if (this.examination()?.requiresMedicalRecord !== false) {
      required.forEach((name) => this.form.controls[name].markAsTouched());
    }
    if (this.examination()?.requiresMedicalRecord !== false && required.some((name) => !this.form.controls[name].value?.trim())) {
      this.error.set('Nhập đủ lý do khám, ghi nhận khám, chẩn đoán và kết luận trước khi ký.');
      return;
    }
    if (!this.validatePrescriptionLines()) return;
    this.signing.set(true);
    this.error.set('');
    this.notice.set('');
    this.authApi.signDoctorExamination(ticketId, this.request()).subscribe({
      next: (value) => {
        this.examination.set(value);
        this.form.disable();
        this.signing.set(false);
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

  protected findMedicationSuggestions(index: number): void {
    const line = this.prescriptionLines.at(index);
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

  private setMedicationSuggestions(index: number, suggestions: MedicationSuggestionResponse[]): void {
    this.medicationSuggestions.update((current) => ({ ...current, [index]: suggestions }));
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401 || response.status === 403) {
      void this.router.navigateByUrl('/staff/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }
}
