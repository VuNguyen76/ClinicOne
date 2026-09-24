import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule, FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import {
  ApiErrorResponse,
  AuthApiService,
  DoctorExaminationRequest,
  DoctorExaminationResponse,
  DiagnosisSuggestionResponse,
  MedicalRecordTemplate,
  MedicationSuggestionResponse,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { auditTime } from 'rxjs';
import { formatClinicDate, formatClinicTime } from '../../core/time/clinic-time';
import {
  MedicalRecordTemplateContent,
  parseMedicalRecordTemplateContent,
  TemplatePrescriptionLine,
} from '../../core/examination/medical-record-template-content';

type PrescriptionLineForm = FormGroup<{
  medicationId: FormControl<string | null>;
  medicationName: FormControl<string | null>;
  dosage: FormControl<string | null>;
  quantity: FormControl<number | null>;
  unit: FormControl<string | null>;
  instructions: FormControl<string | null>;
}>;

type ClinicalTextField = 'reason' | 'examinationNotes' | 'diagnosis' | 'conclusion' | 'treatmentPlan';
type PrescriptionTextField = 'medicationName' | 'dosage' | 'unit' | 'instructions';

const TEMPLATE_TEXT_FIELDS: Array<{ key: ClinicalTextField | 'followUpNote'; label: string }> = [
  { key: 'reason', label: 'Lý do khám' },
  { key: 'examinationNotes', label: 'Ghi nhận khám' },
  { key: 'diagnosis', label: 'Chẩn đoán' },
  { key: 'conclusion', label: 'Kết luận' },
  { key: 'treatmentPlan', label: 'Hướng xử trí' },
  { key: 'followUpNote', label: 'Dặn dò tái khám' },
];

export interface SpecialtyMedicationItem {
  id?: string;
  code: string;
  name: string;
  category: string;
  dosage: string;
  instructions: string;
  specialties?: string[];
  unit?: string;
}

@Component({
  selector: 'app-doctor-examination',
  standalone: true,
  imports: [ReactiveFormsModule, MatIconModule, StaffWorkspaceShell],
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
  protected readonly stopping = signal(false);
  protected readonly prescriptionEnabled = signal(false);
  protected readonly followUpEnabled = signal(false);
  protected readonly confirmingSign = signal(false);
  protected readonly confirmingStop = signal(false);
  protected readonly stopReason = signal('');
  protected readonly confirmingWrongProfile = signal(false);
  protected readonly wrongProfileReason = signal('');
  protected readonly returningWrongProfile = signal(false);
  protected readonly medicationSuggestions = signal<Record<number, MedicationSuggestionResponse[]>>({});
  protected readonly diagnosisSuggestions = signal<DiagnosisSuggestionResponse[]>([]);
  protected readonly medicalTemplates = signal<MedicalRecordTemplate[]>([]);
  protected readonly templatesOpen = signal(false);
  protected readonly templatesLoading = signal(false);
  protected readonly templatesLoaded = signal(false);
  protected readonly selectedTemplate = signal<MedicalRecordTemplate | null>(null);
  protected readonly confirmingTemplateOverwrite = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  protected readonly isReadonly = computed(() =>
    !this.examination() || Boolean(this.examination()?.signedAt) || this.examination()?.status === 'COMPLETED'
  );

  // Clinical Workspace Tabs
  protected readonly activeTab = signal<'exam' | 'diagnosis' | 'prescription'>('exam');
  protected selectTab(tab: 'exam' | 'diagnosis' | 'prescription'): void {
    this.activeTab.set(tab);
  }

  // Clinical Safety Alerts (Cảnh báo an toàn lâm sàng)
  protected readonly prescriptionRevision = signal(0);

  protected readonly duplicatePrescriptionWarnings = computed(() => {
    this.prescriptionRevision();
    const lines = this.prescriptionLines.controls;
    const names = new Map<string, number>();
    lines.forEach((ctrl) => {
      const name = ctrl.controls.medicationName.value?.trim().toLowerCase();
      if (name) {
        names.set(name, (names.get(name) ?? 0) + 1);
      }
    });
    const duplicates: string[] = [];
    names.forEach((count, name) => {
      if (count > 1) {
        const orig = lines.find((c) => c.controls.medicationName.value?.trim().toLowerCase() === name)?.controls.medicationName.value?.trim();
        if (orig && !duplicates.includes(orig)) duplicates.push(orig);
      }
    });
    return duplicates;
  });

  protected readonly allergyWarnings = computed(() => {
    this.prescriptionRevision();
    const formAllergy = this.form?.controls?.allergySummary?.value?.toLowerCase().trim() ?? '';
    let allergyText = formAllergy || (this.examination()?.allergySummary?.toLowerCase().trim() ?? '');
    if (!allergyText) return [];
    allergyText = allergyText.replace(/^(tiền sử dị ứng|dị ứng thuốc|dị ứng(\s+với)?|allergy)\s*[:\s]*/i, '');
    allergyText = allergyText.replace(/\(.*?\)/g, ' ');
    const lines = this.prescriptionLines.controls;
    const warnings: Array<{ medication: string; allergen: string }> = [];
    const rawAllergens = allergyText
      .split(/[,;\n]+/)
      .map((s) => s.replace(/^thuốc\s+/i, '').trim())
      .filter((s) => s.length >= 2);

    lines.forEach((ctrl) => {
      const medName = ctrl.controls.medicationName.value?.trim() ?? '';
      const lowerMed = medName.toLowerCase();
      if (!medName) return;

      for (const allergen of rawAllergens) {
        if (lowerMed.includes(allergen) || allergen.includes(lowerMed)) {
          if (!warnings.some((w) => w.medication === medName && w.allergen === allergen)) {
            warnings.push({ medication: medName, allergen });
          }
          break;
        }
      }
    });
    return warnings;
  });

  // Specialty Medication Catalog
  protected readonly medicationCatalogOpen = signal(false);
  protected readonly medicationCatalogLoading = signal(false);
  protected readonly medicationCatalogLoaded = signal(false);
  protected readonly medicationSearchQuery = signal('');
  protected readonly selectedMedicationCategory = signal('Khoa của tôi');
  protected readonly specialtyMedications = signal<SpecialtyMedicationItem[]>([]);

  protected readonly medicationCategories = computed(() => {
    const currentSpecialty = this.examination()?.specialty ?? '';
    const label = currentSpecialty ? `Khoa của tôi (${currentSpecialty})` : 'Khoa của tôi';
    const meds = this.specialtyMedications();
    const categoriesSet = new Set<string>();
    meds.forEach((m) => {
      if (m.category && m.category.trim()) categoriesSet.add(m.category.trim());
    });
    const standardOrder = [
      'Hạ sốt & Giảm đau',
      'Kháng sinh',
      'Tim mạch',
      'Hô hấp',
      'Tiêu hóa',
      'Nhi khoa',
      'Mắt & TMH',
      'Vitamin & Khoáng chất',
    ];
    const ordered: string[] = [];
    standardOrder.forEach((cat) => {
      if (categoriesSet.has(cat)) {
        ordered.push(cat);
        categoriesSet.delete(cat);
      }
    });
    categoriesSet.forEach((cat) => ordered.push(cat));

    return [label, ...(ordered.length > 0 ? ordered : standardOrder), 'Tất cả'];
  });

  protected readonly filteredCatalogMedications = computed(() => {
    const query = this.medicationSearchQuery().trim().toLowerCase();
    const activeCategory = this.selectedMedicationCategory();
    const currentSpecialty = this.examination()?.specialty ?? '';
    let items = this.specialtyMedications();
    if (activeCategory.startsWith('Khoa của tôi')) {
      items = items.filter((item) => this.isMatchSpecialty(item, currentSpecialty));
    } else if (activeCategory && activeCategory !== 'Tất cả') {
      items = items.filter((item) => item.category === activeCategory);
    }
    if (query) {
      items = items.filter((item) =>
        item.name.toLowerCase().includes(query) ||
        item.code.toLowerCase().includes(query) ||
        item.category.toLowerCase().includes(query) ||
        item.instructions.toLowerCase().includes(query) ||
        item.dosage.toLowerCase().includes(query)
      );
    }
    return items;
  });

  // Print Mode ('full' = Phiếu khám bệnh ngoại trú, 'prescription' = Đơn thuốc điện tử)
  protected readonly printMode = signal<'full' | 'prescription'>('full');

  protected readonly vitalsWeight = signal<number | null>(null);
  protected readonly vitalsHeight = signal<number | null>(null);
  protected readonly bmi = computed(() => {
    const w = this.vitalsWeight();
    const h = this.vitalsHeight();
    if (!w || !h || h <= 0) return null;
    const heightInMeters = h / 100;
    const bmiVal = w / (heightInMeters * heightInMeters);
    return Math.round(bmiVal * 10) / 10;
  });

  protected readonly form = this.fb.group({
    reason: ['', [Validators.maxLength(2000)]],
    examinationNotes: ['', [Validators.maxLength(2000)]],
    diagnosis: ['', [Validators.maxLength(2000)]],
    conclusion: ['', [Validators.maxLength(2000)]],
    treatmentPlan: ['', [Validators.maxLength(2000)]],
    bloodPressure: ['', [Validators.maxLength(30)]],
    heartRate: [null as number | null, [Validators.min(20), Validators.max(300)]],
    temperature: [null as number | null, [Validators.min(30), Validators.max(45)]],
    spO2: [null as number | null, [Validators.min(50), Validators.max(100)]],
    weight: [null as number | null, [Validators.min(1), Validators.max(500)]],
    height: [null as number | null, [Validators.min(30), Validators.max(300)]],
    allergySummary: ['', [Validators.maxLength(500)]],
    prescriptionLines: this.fb.array<PrescriptionLineForm>([]),
    followUpDate: [''],
    followUpDays: [null as number | null, [Validators.min(1), Validators.max(365)]],
    followUpNote: ['', [Validators.maxLength(500)]],
  });
  protected readonly selectedTemplateContent = computed(() => {
    const template = this.selectedTemplate();
    return template ? parseMedicalRecordTemplateContent(template.fieldDefinition) : {};
  });
  protected readonly templatePreviewItems = computed(() => {
    const content = this.selectedTemplateContent();
    const items = TEMPLATE_TEXT_FIELDS.flatMap(({ key, label }) => {
      const value = content[key];
      return typeof value === 'string' && value ? [{ label, value }] : [];
    });
    if (content.followUpDays) items.push({ label: 'Hẹn tái khám', value: `Sau ${content.followUpDays} ngày` });
    if (content.prescriptionLines && content.prescriptionLines.length > 0) {
      items.push({
        label: 'Đơn thuốc mẫu kèm theo',
        value: `${content.prescriptionLines.length} thuốc (${content.prescriptionLines.map((p) => p.medicationName).join(', ')})`,
      });
    }
    return items;
  });

  protected get prescriptionLines(): FormArray<PrescriptionLineForm> {
    return this.form.controls.prescriptionLines;
  }

  ngOnInit(): void {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.vitalsWeight.set(this.form.controls.weight.value);
      this.vitalsHeight.set(this.form.controls.height.value);
      this.prescriptionRevision.update((v) => v + 1);
      this.markDraftChanged();
    });
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
          bloodPressure: value.bloodPressure ?? '',
          heartRate: value.heartRate ?? null,
          temperature: value.temperature ?? null,
          spO2: value.spO2 ?? null,
          weight: value.weight ?? null,
          height: value.height ?? null,
          allergySummary: value.allergySummary ?? '',
          followUpDate: value.followUpDate ?? '',
          followUpDays: value.followUpDays ?? null,
          followUpNote: value.followUpNote ?? '',
        }, { emitEvent: false });
        this.vitalsWeight.set(value.weight ?? null);
        this.vitalsHeight.set(value.height ?? null);
        value.prescriptionLines?.forEach((line) => this.prescriptionLines.push(this.createPrescriptionLine(line)));
        this.prescriptionEnabled.set(this.prescriptionLines.length > 0);
        this.prescriptionRevision.update((v) => v + 1);
        this.followUpEnabled.set(value.followUpDays != null || Boolean(value.followUpNote));
        if (value.signedAt || value.status === 'COMPLETED') this.form.disable();
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

  protected openMedicalTemplates(): void {
    if (this.isReadonly()) return;
    this.templatesOpen.set(true);
    if (this.templatesLoaded() || this.templatesLoading()) return;
    this.templatesLoading.set(true);
    this.error.set('');
    this.authApi.getMedicalRecordTemplates(
      this.examination()?.specialty,
      this.examination()?.clinicServiceId ?? undefined,
    ).subscribe({
      next: (templates) => {
        this.medicalTemplates.set(templates);
        this.templatesLoaded.set(true);
        this.templatesLoading.set(false);
      },
      error: (response) => {
        this.templatesLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected closeMedicalTemplates(): void {
    this.templatesOpen.set(false);
    this.selectedTemplate.set(null);
    this.confirmingTemplateOverwrite.set(false);
  }

  protected previewMedicalTemplate(template: MedicalRecordTemplate): void {
    this.selectedTemplate.set(template);
    this.confirmingTemplateOverwrite.set(false);
  }

  protected requestApplyMedicalTemplate(): void {
    const content = this.selectedTemplateContent();
    if (!this.selectedTemplate() || this.templatePreviewItems().length === 0) {
      this.error.set('Mẫu phiếu chưa có nội dung điền sẵn.');
      return;
    }
    if (this.templateWouldOverwrite(content)) {
      this.confirmingTemplateOverwrite.set(true);
      return;
    }
    this.applyMedicalTemplate(content);
  }

  protected confirmApplyMedicalTemplate(): void {
    this.applyMedicalTemplate(this.selectedTemplateContent());
  }

  protected cancelTemplateOverwrite(): void {
    this.confirmingTemplateOverwrite.set(false);
  }

  protected saveDraftOnBlur(): void {
    if (this.draftDirty) this.persistDraft(false);
  }

  private persistDraft(manual: boolean): void {
    const ticketId = this.examination()?.ticketId;
    if (!ticketId || this.saving() || this.signing() || this.isReadonly() || this.examination()?.requiresMedicalRecord === false) return;
    if (manual && !this.validateVitalsBeforeSaving()) return;
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
        setTimeout(() => this.notice.set(''), 3000);
      },
      error: (response) => {
        this.saving.set(false);
        this.handleError(response);
        if (!manual) this.scheduleAutosaveRetry();
      },
    });
  }

  private autosaveDraft(): void {
    if (!this.draftDirty || this.loading() || this.isReadonly() || this.prescriptionLines.invalid || this.form.invalid) return;
    this.persistDraft(false);
  }

  protected validateVitalsBeforeSaving(): boolean {
    const invalidFields: string[] = [];
    if (this.form.controls.heartRate.invalid) invalidFields.push('Nhịp tim (20 - 300 lần/phút)');
    if (this.form.controls.temperature.invalid) invalidFields.push('Thân nhiệt (30 - 45°C)');
    if (this.form.controls.spO2.invalid) invalidFields.push('SpO2 (50 - 100%)');
    if (this.form.controls.weight.invalid) invalidFields.push('Cân nặng (1 - 500 kg)');
    if (this.form.controls.height.invalid) invalidFields.push('Chiều cao (30 - 300 cm)');
    if (this.form.controls.bloodPressure.invalid) invalidFields.push('Huyết áp (tối đa 30 ký tự)');

    if (invalidFields.length > 0) {
      this.selectTab('exam');
      this.error.set(`Chỉ số sinh hiệu không hợp lệ: ${invalidFields.join(', ')}.`);
      return false;
    }
    return true;
  }

  protected requestSign(): void {
    const ticketId = this.examination()?.ticketId;
    if (!ticketId || this.saving() || this.signing() || this.examination()?.status === 'COMPLETED') return;
    if (this.examination()?.requiresMedicalRecord !== false) {
      // 1. Kiểm tra Tab 1 (Lý do khám & ghi nhận lâm sàng)
      if (!this.form.controls.reason.value?.trim() || !this.form.controls.examinationNotes.value?.trim()) {
        this.form.controls.reason.markAsTouched();
        this.form.controls.examinationNotes.markAsTouched();
        this.selectTab('exam');
        this.error.set('Nhập đủ lý do khám, ghi nhận khám, chẩn đoán và kết luận trước khi ký.');
        return;
      }

      // 2. Kiểm tra chỉ số sinh hiệu
      if (!this.validateVitalsBeforeSaving()) return;

      // 3. Kiểm tra Tab 2 (Chẩn đoán & Kết luận)
      if (!this.form.controls.diagnosis.value?.trim() || !this.form.controls.conclusion.value?.trim()) {
        this.form.controls.diagnosis.markAsTouched();
        this.form.controls.conclusion.markAsTouched();
        this.selectTab('diagnosis');
        this.error.set('Nhập đủ lý do khám, ghi nhận khám, chẩn đoán và kết luận trước khi ký.');
        return;
      }

      // 4. Kiểm tra Tab 3 (Đơn thuốc điều trị)
      if (!this.validatePrescriptionLines()) {
        this.selectTab('prescription');
        return;
      }

      // 5. Kiểm tra Tab 3 (Hẹn tái khám nếu bật)
      if (!this.validateFollowUpBeforeSigning()) {
        this.selectTab('prescription');
        return;
      }

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

  protected requestStop(): void {
    if (this.saving() || this.signing() || this.stopping() || this.examination()?.status !== 'IN_PROGRESS') return;
    this.stopReason.set('');
    this.confirmingStop.set(true);
  }

  protected cancelStop(): void {
    this.confirmingStop.set(false);
    this.stopReason.set('');
  }

  protected stop(): void {
    const ticketId = this.examination()?.ticketId;
    const reason = this.stopReason().trim();
    if (!ticketId || this.stopping()) return;
    if (reason.length < 10 || reason.length > 500) {
      this.error.set('Lý do dừng lượt khám phải từ 10 đến 500 ký tự.');
      return;
    }
    this.stopping.set(true);
    this.error.set('');
    this.notice.set('');
    this.authApi.stopDoctorExamination(ticketId, reason).subscribe({
      next: (value) => {
        this.examination.set(value);
        this.form.disable();
        this.confirmingStop.set(false);
        this.stopping.set(false);
        this.notice.set('Đã dừng lượt khám.');
        setTimeout(() => this.notice.set(''), 4000);
      },
      error: (response) => {
        this.stopping.set(false);
        this.handleError(response);
      },
    });
  }

  protected requestWrongProfile(): void {
    if (this.saving() || this.signing() || this.stopping() || this.returningWrongProfile()
      || this.examination()?.status !== 'IN_PROGRESS') return;
    this.wrongProfileReason.set('');
    this.confirmingWrongProfile.set(true);
  }

  protected cancelWrongProfile(): void {
    this.confirmingWrongProfile.set(false);
    this.wrongProfileReason.set('');
  }

  protected confirmWrongProfile(): void {
    const ticketId = this.examination()?.ticketId;
    const reason = this.wrongProfileReason().trim();
    if (!ticketId || this.returningWrongProfile()) return;
    if (reason.length < 10 || reason.length > 500) {
      this.error.set('Lý do bắt đầu nhầm hồ sơ phải từ 10 đến 500 ký tự.');
      return;
    }
    this.returningWrongProfile.set(true);
    this.error.set('');
    this.authApi.markDoctorExaminationWrongProfile(ticketId, reason).subscribe({
      next: () => {
        this.confirmingWrongProfile.set(false);
        this.returningWrongProfile.set(false);
        void this.router.navigateByUrl('/doctor');
      },
      error: (response) => {
        this.returningWrongProfile.set(false);
        this.handleError(response);
      },
    });
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
        this.notice.set(value.requiresMedicalRecord !== false ? 'Đã ký phiếu khám thành công.' : 'Đã kết thúc lượt khám thành công.');
        setTimeout(() => this.notice.set(''), 4000);
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
    if (this.prescriptionLines.length >= 20 || this.isReadonly()) return;
    this.prescriptionEnabled.set(true);
    this.prescriptionLines.push(this.createPrescriptionLine());
    this.prescriptionRevision.update((v) => v + 1);
  }

  protected removePrescriptionLine(index: number): void {
    if (this.isReadonly()) return;
    this.prescriptionLines.removeAt(index);
    this.prescriptionEnabled.set(this.prescriptionLines.length > 0);
    this.prescriptionRevision.update((v) => v + 1);
  }

  protected toggleFollowUp(): void {
    if (this.isReadonly()) return;
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
    if (control.value === null || control.value === undefined) return;
    if (control.value < 1) {
      control.setValue(1);
      this.notice.set('Số lượng thuốc tối thiểu là 1.');
      return;
    }
    if (control.value > 999) {
      control.setValue(999);
      this.notice.set('Số lượng thuốc tối đa 999.');
    }
  }

  protected limitFollowUpDays(): void {
    const control = this.form.controls.followUpDays;
    if (control.value === null || control.value === undefined || control.value === 0) return;
    if (control.value < 1) {
      control.setValue(1);
      this.notice.set('Số ngày tái khám tối thiểu là 1.');
      return;
    }
    if (control.value > 365) {
      control.setValue(365);
      this.notice.set('Số ngày tái khám tối đa 365.');
    }
  }

  protected limitFollowUpNote(): void {
    this.limitText(this.form.controls.followUpNote, 'Dặn dò tái khám', 500);
  }

  protected selectMedication(index: number, medication: MedicationSuggestionResponse): void {
    const line = this.prescriptionLines.at(index);
    const curDosage = line.controls.dosage.value?.trim();
    const curUnit = line.controls.unit.value?.trim();
    const curQty = line.controls.quantity.value;
    const curInstructions = line.controls.instructions.value?.trim();

    line.patchValue({
      medicationId: medication.id,
      medicationName: medication.name,
      dosage: curDosage || medication.defaultDosage || '1 viên / lần',
      unit: medication.unit || curUnit || 'Viên',
      quantity: (curQty && curQty > 0) ? curQty : 1,
      instructions: curInstructions || medication.defaultInstructions || 'Dùng theo chỉ định của bác sĩ',
    });
    this.setMedicationSuggestions(index, []);
    this.prescriptionRevision.update((v) => v + 1);
  }

  protected formatDate(value: string | null | undefined): string {
    return formatClinicDate(value) || 'Chưa cập nhật';
  }

  protected patientBirthLabel(value: string | null | undefined): string {
    const formatted = this.formatDate(value);
    if (!value || formatted === 'Chưa cập nhật') return 'Chưa cập nhật';
    try {
      const birthDate = new Date(value);
      if (isNaN(birthDate.getTime())) return formatted;
      const today = new Date();
      let age = today.getFullYear() - birthDate.getFullYear();
      const m = today.getMonth() - birthDate.getMonth();
      if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
        age--;
      }
      return age >= 0 ? `${formatted} (${age} tuổi)` : formatted;
    } catch {
      return formatted;
    }
  }

  protected formatTime(value: string | null | undefined): string {
    return formatClinicTime(value);
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
      bloodPressure: value.bloodPressure?.trim() || null,
      heartRate: value.heartRate != null && (value.heartRate as any) !== '' ? Number(value.heartRate) : null,
      temperature: value.temperature != null && (value.temperature as any) !== '' ? Number(value.temperature) : null,
      spO2: value.spO2 != null && (value.spO2 as any) !== '' ? Number(value.spO2) : null,
      weight: value.weight != null && (value.weight as any) !== '' ? Number(value.weight) : null,
      height: value.height != null && (value.height as any) !== '' ? Number(value.height) : null,
      allergySummary: value.allergySummary?.trim() || null,
      prescription: '',
      prescriptionLines: value.prescriptionLines.map((line) => ({
        ...(line.medicationId ? { medicationId: line.medicationId } : {}),
        medicationName: line.medicationName ?? '',
        dosage: line.dosage ?? '',
        quantity: Number(line.quantity),
        unit: line.unit?.trim() || null,
        instructions: line.instructions ?? '',
      })),
      followUpDate: value.followUpDate || null,
      followUpDays: this.followUpEnabled() ? (value.followUpDays ?? null) : null,
      followUpNote: this.followUpEnabled() ? (value.followUpNote?.trim() || null) : null,
      recordVersion: this.examination()?.recordVersion ?? null,
    };
  }

  private templateWouldOverwrite(content: MedicalRecordTemplateContent): boolean {
    return TEMPLATE_TEXT_FIELDS.some(({ key }) => {
      const incoming = content[key];
      if (typeof incoming !== 'string' || !incoming) return false;
      const current = this.form.controls[key].value?.trim() ?? '';
      return Boolean(current && current !== incoming);
    }) || Boolean(content.followUpDays && this.form.controls.followUpDays.value
      && this.form.controls.followUpDays.value !== content.followUpDays);
  }

  private applyMedicalTemplate(content: MedicalRecordTemplateContent): void {
    const patch: Partial<Record<ClinicalTextField | 'followUpNote', string>> & { followUpDays?: number } = {};
    TEMPLATE_TEXT_FIELDS.forEach(({ key }) => {
      const value = content[key];
      if (typeof value === 'string' && value) patch[key] = value;
    });
    if (content.followUpDays) {
      patch.followUpDays = content.followUpDays;
      this.followUpEnabled.set(true);
    }
    this.form.patchValue(patch);

    if (content.prescriptionLines && content.prescriptionLines.length > 0) {
      const availableSlots = 20 - this.prescriptionLines.length;
      if (availableSlots > 0) {
        const linesToAdd = content.prescriptionLines.slice(0, availableSlots);
        linesToAdd.forEach((line) => {
          this.prescriptionLines.push(this.fb.group({
            medicationId: [null],
            medicationName: [line.medicationName, [Validators.required, Validators.maxLength(200)]],
            dosage: [line.dosage, [Validators.required, Validators.maxLength(100)]],
            quantity: [line.quantity, [Validators.required, Validators.min(1), Validators.max(999), Validators.pattern(/^[1-9]\d*$/)]],
            unit: [line.unit || 'Viên', [Validators.required, Validators.maxLength(50)]],
            instructions: [line.instructions, [Validators.required, Validators.maxLength(500)]],
          }) as PrescriptionLineForm);
        });
        this.prescriptionEnabled.set(true);
        this.prescriptionRevision.update((v) => v + 1);
      }
    }

    this.confirmingTemplateOverwrite.set(false);
    this.templatesOpen.set(false);
    const templateName = this.selectedTemplate()?.name;
    this.selectedTemplate.set(null);
    this.notice.set(templateName ? `Đã áp dụng mẫu ${templateName}.` : 'Đã áp dụng mẫu phiếu.');
  }

  protected lineControlInvalid(index: number, controlName: keyof PrescriptionLineForm['controls']): boolean {
    const line = this.prescriptionLines.at(index);
    if (!line) return false;
    const ctrl = line.get(controlName);
    return Boolean(ctrl && ctrl.invalid && (ctrl.touched || ctrl.dirty));
  }

  private createPrescriptionLine(line?: DoctorExaminationResponse['prescriptionLines'][number]): PrescriptionLineForm {
    return this.fb.group({
      medicationId: [line?.medicationId ?? null],
      medicationName: [line?.medicationName ?? '', [Validators.required, Validators.maxLength(200)]],
      dosage: [line?.dosage ?? '', [Validators.required, Validators.maxLength(100)]],
      quantity: [line?.quantity ?? 1, [Validators.required, Validators.min(1), Validators.max(999), Validators.pattern(/^[1-9]\d*$/)]],
      unit: [line?.unit ?? 'Viên', [Validators.required, Validators.maxLength(50)]],
      instructions: [line?.instructions ?? '', [Validators.required, Validators.maxLength(500)]],
    }) as PrescriptionLineForm;
  }

  private validatePrescriptionLines(): boolean {
    if (this.prescriptionLines.length === 0) return true;
    let valid = true;
    for (let i = 0; i < this.prescriptionLines.length; i++) {
      const line = this.prescriptionLines.at(i);
      line.markAllAsTouched();
      if (line.invalid) {
        valid = false;
      }
    }
    if (!valid) {
      this.selectTab('prescription');
      this.error.set('Đơn thuốc chưa hợp lệ. Vui lòng kiểm tra lại số lượng (1-999), liều dùng, ĐVT và hướng dẫn sử dụng của từng thuốc.');
      return false;
    }
    return true;
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
    if (response.status === 401) {
      void this.router.navigateByUrl('/staff/login');
      return;
    }
    if (response.status === 403) {
      this.error.set(apiErrorMessage(response));
      return;
    }
    this.error.set(apiErrorMessage(response));
  }

  protected openMedicationCatalog(): void {
    if (this.isReadonly()) return;
    this.medicationCatalogOpen.set(true);
    this.loadDoctorMedications();
  }

  protected closeMedicationCatalog(): void {
    this.medicationCatalogOpen.set(false);
  }

  protected selectMedicationCategory(category: string): void {
    this.selectedMedicationCategory.set(category);
  }

  protected updateMedicationSearchQuery(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.medicationSearchQuery.set(input.value);
  }

  protected clearMedicationSearch(): void {
    this.medicationSearchQuery.set('');
  }

  protected getPrescribedQuantity(item: SpecialtyMedicationItem): number {
    const ctrl = this.prescriptionLines.controls.find(
      (c) => (item.id && c.controls.medicationId.value === item.id) ||
             c.controls.medicationName.value?.trim().toLowerCase() === item.name.trim().toLowerCase()
    );
    return ctrl ? (Number(ctrl.controls.quantity.value) || 0) : 0;
  }

  protected prescribeFromCatalog(item: SpecialtyMedicationItem): void {
    if (this.isReadonly()) return;

    // Kiểm tra xem thuốc đã có trong đơn hay chưa
    const existingIndex = this.prescriptionLines.controls.findIndex(
      (ctrl) => (item.id && ctrl.controls.medicationId.value === item.id) ||
                ctrl.controls.medicationName.value?.trim().toLowerCase() === item.name.trim().toLowerCase()
    );

    if (existingIndex >= 0) {
      const existingLine = this.prescriptionLines.at(existingIndex);
      const currentQty = Number(existingLine.controls.quantity.value) || 0;
      if (currentQty < 999) {
        const newQty = currentQty + 1;
        existingLine.controls.quantity.setValue(newQty);
        this.prescriptionRevision.update((v) => v + 1);
        this.notice.set(`Thuốc "${item.name}" đã có trong đơn — đã tăng số lượng lên ${newQty}.`);
      } else {
        this.notice.set(`Thuốc "${item.name}" đã đạt số lượng tối đa (999).`);
      }
      setTimeout(() => {
        if (this.notice().includes(item.name)) this.notice.set('');
      }, 3000);
      return;
    }

    if (this.prescriptionLines.length >= 20) {
      this.error.set('Đơn thuốc đã đạt tối đa 20 loại thuốc.');
      setTimeout(() => {
        if (this.error().includes('tối đa 20')) this.error.set('');
      }, 3000);
      return;
    }

    this.prescriptionEnabled.set(true);
    const line = this.fb.group({
      medicationId: [item.id ?? null],
      medicationName: [item.name, [Validators.required, Validators.maxLength(200)]],
      dosage: [item.dosage || '1 viên / lần', [Validators.required, Validators.maxLength(100)]],
      quantity: [1, [Validators.required, Validators.min(1), Validators.max(999), Validators.pattern(/^[1-9]\d*$/)]],
      unit: [item.unit || 'Viên', [Validators.required, Validators.maxLength(50)]],
      instructions: [item.instructions || 'Dùng theo chỉ định của bác sĩ', [Validators.required, Validators.maxLength(500)]],
    }) as PrescriptionLineForm;
    this.prescriptionLines.push(line);
    this.prescriptionRevision.update((v) => v + 1);
    this.notice.set(`Đã thêm "${item.name}" vào đơn thuốc.`);
    setTimeout(() => {
      if (this.notice().includes(item.name)) this.notice.set('');
    }, 2500);
  }

  protected loadDoctorMedications(): void {
    if (this.medicationCatalogLoaded() || this.medicationCatalogLoading()) return;
    this.medicationCatalogLoading.set(true);
    this.authApi.getDoctorMedications().subscribe({
      next: (apiMeds) => {
        const items: SpecialtyMedicationItem[] = (apiMeds ?? []).map((med) => {
          const rawSpecs = med.specialties ? med.specialties.split(',').map((s) => s.trim()).filter(Boolean) : [];
          return {
            id: med.id,
            code: med.code,
            name: med.name,
            category: med.category || 'Khác',
            dosage: med.defaultDosage || '1 viên / lần',
            instructions: med.defaultInstructions || 'Dùng theo chỉ định của bác sĩ',
            specialties: rawSpecs,
            unit: med.unit || 'Viên',
          };
        });
        this.specialtyMedications.set(items);
        this.medicationCatalogLoaded.set(true);
        this.medicationCatalogLoading.set(false);
      },
      error: () => {
        this.medicationCatalogLoading.set(false);
      },
    });
  }

  private isMatchSpecialty(item: SpecialtyMedicationItem, currentSpecialty: string): boolean {
    if (!currentSpecialty) return true;
    const spec = currentSpecialty.toLowerCase().trim();
    if (item.specialties && item.specialties.length > 0) {
      return item.specialties.some((s) => {
        const itemSpec = s.toLowerCase();
        return itemSpec.includes(spec) || spec.includes(itemSpec);
      });
    }
    return true;
  }

  // Print Handlers
  protected printFullRecord(): void {
    this.printMode.set('full');
    setTimeout(() => window.print(), 50);
  }

  protected printPrescription(): void {
    if (this.prescriptionLines.length === 0) {
      this.notice.set('Chưa có thuốc trong đơn để in.');
      setTimeout(() => this.notice.set(''), 3000);
      return;
    }
    this.printMode.set('prescription');
    setTimeout(() => window.print(), 50);
  }

  protected printRecord(): void {
    this.printFullRecord();
  }
}
