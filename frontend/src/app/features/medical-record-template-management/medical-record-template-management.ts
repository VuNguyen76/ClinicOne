import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import {
  AuthApiService,
  ClinicServiceResponse,
  DiagnosisSuggestionResponse,
  MedicationSuggestionResponse,
  MedicalRecordTemplate,
  SpecialtyOption,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import {
  MedicalRecordTemplateContent,
  parseMedicalRecordTemplateContent,
  serializeMedicalRecordTemplateContent,
  TemplatePrescriptionLine,
} from '../../core/examination/medical-record-template-content';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { hasStaffRole } from '../../core/auth/auth.guard';
import { DiagnosisCatalogManagement } from '../diagnosis-catalog-management/diagnosis-catalog-management';

@Component({
  selector: 'app-medical-record-template-management',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, StaffWorkspaceShell, DiagnosisCatalogManagement],
  templateUrl: './medical-record-template-management.html',
})
export class MedicalRecordTemplateManagement implements OnInit {
  private readonly api = inject(AuthApiService);

  protected isDoctorRole(): boolean {
    return hasStaffRole('DOCTOR') && !hasStaffRole('COORDINATOR') && !hasStaffRole('ADMIN');
  }

  protected canManageTemplates(): boolean {
    return hasStaffRole('COORDINATOR');
  }

  protected canAccessDiagnosisCatalog(): boolean {
    return this.canManageTemplates() || hasStaffRole('ADMIN') || this.isDoctorRole();
  }

  protected readonly items = signal<MedicalRecordTemplate[]>([]);
  protected readonly viewingTemplate = signal<MedicalRecordTemplate | null>(null);
  protected readonly specialties = signal<SpecialtyOption[]>([]);
  protected readonly services = signal<ClinicServiceResponse[]>([]);
  protected readonly diagnoses = signal<DiagnosisSuggestionResponse[]>([]);
  protected readonly medications = signal<MedicationSuggestionResponse[]>([]);
  protected readonly catalogError = signal('');
  protected readonly editingId = signal<string | null>(null);
  protected readonly code = signal('');
  protected readonly name = signal('');
  protected readonly specialty = signal('');
  protected readonly clinicServiceId = signal('');
  protected readonly description = signal('');
  protected readonly reason = signal('');
  protected readonly examinationNotes = signal('');
  protected readonly diagnosis = signal('');
  protected readonly conclusion = signal('');
  protected readonly treatmentPlan = signal('');
  protected readonly followUpDays = signal<number | null>(null);
  protected readonly followUpNote = signal('');
  protected readonly prescriptionLines = signal<TemplatePrescriptionLine[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly activeTab = signal<'list' | 'form' | 'diagnoses'>('list');
  protected readonly activeSetupStep = signal<1 | 2 | 3>(1);
  protected readonly setupSteps = [
    { number: 1 as const, title: 'Thông tin mẫu', icon: 'badge' },
    { number: 2 as const, title: 'Nội dung khám', icon: 'clinical_notes' },
    { number: 3 as const, title: 'Đơn thuốc', icon: 'medication' },
  ];
  protected readonly searchTerm = signal('');

  protected readonly filteredServices = computed(() => this.services()
    .filter((service) => !this.specialty() || service.specialty === this.specialty()));
  protected readonly formTitle = computed(() => this.editingId() ? 'Cập nhật mẫu phiếu' : 'Thêm mẫu phiếu');

  protected filteredItems(): MedicalRecordTemplate[] {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.items();
    return this.items().filter((t) =>
      t.code.toLowerCase().includes(q) ||
      t.name.toLowerCase().includes(q) ||
      t.specialty.toLowerCase().includes(q) ||
      (t.description && t.description.toLowerCase().includes(q))
    );
  }

  protected totalTemplatesCount(): number {
    return this.items().length;
  }

  protected specialtiesCount(): number {
    return new Set(this.items().map((t) => t.specialty)).size;
  }

  protected servicesLinkedCount(): number {
    return this.items().filter((t) => t.clinicServiceId).length;
  }

  ngOnInit(): void {
    this.loadInitialData();
  }

  protected viewTemplate(item: MedicalRecordTemplate): void {
    this.viewingTemplate.set(item);
  }

  protected goToSetupStep(step: 1 | 2 | 3): void {
    this.activeSetupStep.set(step);
  }

  protected nextSetupStep(): void {
    const current = this.activeSetupStep();
    if (current < 3) this.activeSetupStep.set((current + 1) as 2 | 3);
  }

  protected previousSetupStep(): void {
    const current = this.activeSetupStep();
    if (current > 1) this.activeSetupStep.set((current - 1) as 1 | 2);
  }

  protected closeViewTemplate(): void {
    this.viewingTemplate.set(null);
  }

  protected readonly viewingTemplateContent = computed(() => {
    const item = this.viewingTemplate();
    return item ? parseMedicalRecordTemplateContent(item.fieldDefinition) : null;
  });

  protected save(): void {
    if (!this.canManageTemplates()) return;
    this.clearMessages();
    if (!this.code().trim() || !this.name().trim() || !this.specialty()) {
      this.error.set('Vui lòng nhập mã, tên và chọn chuyên khoa.');
      return;
    }
    const fieldDefinition = serializeMedicalRecordTemplateContent(this.formContent());
    if (fieldDefinition === '{}') {
      this.error.set('Vui lòng nhập ít nhất một nội dung cho mẫu phiếu.');
      return;
    }
    const request = {
      code: this.code().trim(),
      name: this.name().trim(),
      specialty: this.specialty(),
      clinicServiceId: this.clinicServiceId() || null,
      description: this.description().trim(),
      fieldDefinition,
    };
    const operation = this.editingId()
      ? this.api.updateMedicalRecordTemplate(this.editingId()!, request)
      : this.api.createMedicalRecordTemplate(request);

    this.saving.set(true);
    operation.subscribe({
      next: () => {
        this.notice.set(this.editingId() ? 'Đã cập nhật mẫu phiếu.' : 'Đã thêm mẫu phiếu.');
        setTimeout(() => this.notice.set(''), 4000);
        this.saving.set(false);
        this.resetForm();
        this.activeTab.set('list');
        this.loadTemplates();
      },
      error: (response) => {
        this.error.set(apiErrorMessage(response));
        this.saving.set(false);
      },
    });
  }

  protected startEdit(item: MedicalRecordTemplate): void {
    this.edit(item);
  }

  protected delete(item: MedicalRecordTemplate): void {
    this.deactivate(item);
  }

  protected edit(item: MedicalRecordTemplate): void {
    if (!this.canManageTemplates()) return;
    const content = parseMedicalRecordTemplateContent(item.fieldDefinition);
    this.editingId.set(item.id);
    this.code.set(item.code);
    this.name.set(item.name);
    this.specialty.set(item.specialty);
    this.clinicServiceId.set(item.clinicServiceId ?? '');
    this.description.set(item.description ?? '');
    this.reason.set(content.reason ?? '');
    this.examinationNotes.set(content.examinationNotes ?? '');
    this.diagnosis.set(content.diagnosis ?? '');
    this.conclusion.set(content.conclusion ?? '');
    this.treatmentPlan.set(content.treatmentPlan ?? '');
    this.followUpDays.set(content.followUpDays ?? null);
    this.followUpNote.set(content.followUpNote ?? '');
    this.prescriptionLines.set(content.prescriptionLines ? [...content.prescriptionLines] : []);
    this.clearMessages();
    this.activeSetupStep.set(1);
    this.activeTab.set('form');
  }

  protected cancelEdit(): void {
    this.resetForm();
    this.clearMessages();
    this.activeTab.set('list');
  }

  protected startCreate(): void {
    if (!this.canManageTemplates()) return;
    this.resetForm();
    this.clearMessages();
    this.activeSetupStep.set(1);
    this.activeTab.set('form');
  }

  protected addPrescriptionLine(): void {
    if (this.prescriptionLines().length >= 20) return;
    this.prescriptionLines.update((lines) => [
      ...lines,
      {
        medicationName: '',
        dosage: '',
        quantity: 1,
        unit: 'Viên',
        instructions: '',
      },
    ]);
  }

  protected removePrescriptionLine(index: number): void {
    this.prescriptionLines.update((lines) => lines.filter((_, i) => i !== index));
  }

  protected updateMedicationLine(index: number, value: string): void {
    const medicationName = value.trim();
    const medication = this.medications().find((item) =>
      item.name.toLocaleLowerCase('vi-VN') === medicationName.toLocaleLowerCase('vi-VN'));

    this.prescriptionLines.update((lines) => lines.map((line, i) => {
      if (i !== index) return line;
      return {
        ...line,
        medicationId: medication?.id ?? null,
        medicationName,
        dosage: medication ? medication.defaultDosage ?? '' : line.dosage,
        quantity: medication ? 1 : line.quantity,
        unit: medication ? medication.unit ?? '' : line.unit,
        instructions: medication ? medication.defaultInstructions ?? '' : line.instructions,
      };
    }));
  }

  protected updateLineField(
    index: number,
    field: Exclude<keyof TemplatePrescriptionLine, 'medicationName' | 'medicationId'>,
    value: string | number,
  ): void {
    this.prescriptionLines.update((lines) =>
      lines.map((line, i) => (i === index ? { ...line, [field]: value } : line))
    );
  }

  protected selectSpecialty(value: string): void {
    this.specialty.set(value);
    if (!this.filteredServices().some((service) => service.id === this.clinicServiceId())) {
      this.clinicServiceId.set('');
    }
  }

  protected deactivate(item: MedicalRecordTemplate): void {
    if (!this.canManageTemplates()) return;
    if (!confirm(`Ngưng sử dụng mẫu ${item.name}?`)) return;
    this.clearMessages();
    this.api.deactivateMedicalRecordTemplate(item.id).subscribe({
      next: () => {
        this.notice.set('Đã ngưng sử dụng mẫu phiếu.');
        setTimeout(() => this.notice.set(''), 4000);
        if (this.editingId() === item.id) this.resetForm();
        this.loadTemplates();
      },
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected serviceName(item: MedicalRecordTemplate): string {
    if (!item.clinicServiceId) return 'Áp dụng toàn chuyên khoa';
    return this.services().find((service) => service.id === item.clinicServiceId)?.name ?? 'Dịch vụ đã ngưng dùng';
  }

  protected contentSummary(item: MedicalRecordTemplate): string {
    const content = parseMedicalRecordTemplateContent(item.fieldDefinition);
    const summary = [content.reason, content.examinationNotes, content.diagnosis, content.conclusion, content.treatmentPlan]
      .filter(Boolean).slice(0, 2).join(' · ') || 'Chưa có nội dung';
    if (content.prescriptionLines?.length) {
      return `${summary} (+ ${content.prescriptionLines.length} thuốc)`;
    }
    return summary;
  }

  private loadInitialData(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({
      templates: this.api.getMedicalRecordTemplates(),
      specialties: this.api.getSpecialties(),
      services: this.api.getActiveClinicServices(),
    }).subscribe({
      next: ({ templates, specialties, services }) => {
        this.items.set(templates);
        this.specialties.set(specialties);
        this.services.set(services);
        this.loading.set(false);
        if (this.canManageTemplates()) this.loadClinicalCatalogs();
      },
      error: (response) => {
        this.error.set(apiErrorMessage(response));
        this.loading.set(false);
      },
    });
  }

  private loadClinicalCatalogs(): void {
    this.catalogError.set('');
    this.api.getAdminDiagnoses(true).subscribe({
      next: (items) => this.diagnoses.set(items.filter((item) => item.active)),
      error: (response) => this.catalogError.set(apiErrorMessage(response)),
    });
    this.api.getAdminMedications(true).subscribe({
      next: (items) => this.medications.set(items.filter((item) => item.active)),
      error: (response) => this.catalogError.set(apiErrorMessage(response)),
    });
  }

  private loadTemplates(): void {
    this.api.getMedicalRecordTemplates().subscribe({
      next: (items) => this.items.set(items),
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  private formContent(): MedicalRecordTemplateContent {
    const lines = this.prescriptionLines().filter((l) => l.medicationName.trim());
    return {
      reason: this.reason(),
      examinationNotes: this.examinationNotes(),
      diagnosis: this.diagnosis(),
      conclusion: this.conclusion(),
      treatmentPlan: this.treatmentPlan(),
      followUpDays: this.followUpDays() ?? undefined,
      followUpNote: this.followUpNote(),
      prescriptionLines: lines.length > 0 ? lines : undefined,
    };
  }

  private resetForm(): void {
    this.editingId.set(null);
    this.code.set('');
    this.name.set('');
    this.specialty.set('');
    this.clinicServiceId.set('');
    this.description.set('');
    this.reason.set('');
    this.examinationNotes.set('');
    this.diagnosis.set('');
    this.conclusion.set('');
    this.treatmentPlan.set('');
    this.followUpDays.set(null);
    this.followUpNote.set('');
    this.prescriptionLines.set([]);
    this.catalogError.set('');
  }

  private clearMessages(): void {
    this.error.set('');
    this.notice.set('');
  }
}
