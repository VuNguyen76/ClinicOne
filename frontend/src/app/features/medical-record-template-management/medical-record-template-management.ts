import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import {
  AuthApiService,
  ClinicServiceResponse,
  MedicalRecordTemplate,
  SpecialtyOption,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import {
  MedicalRecordTemplateContent,
  parseMedicalRecordTemplateContent,
  serializeMedicalRecordTemplateContent,
} from '../../core/examination/medical-record-template-content';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-medical-record-template-management',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './medical-record-template-management.html',
})
export class MedicalRecordTemplateManagement implements OnInit {
  private readonly api = inject(AuthApiService);

  protected readonly items = signal<MedicalRecordTemplate[]>([]);
  protected readonly specialties = signal<SpecialtyOption[]>([]);
  protected readonly services = signal<ClinicServiceResponse[]>([]);
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
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly activeTab = signal<'list' | 'form'>('list');

  protected readonly filteredServices = computed(() => this.services()
    .filter((service) => !this.specialty() || service.specialty === this.specialty()));
  protected readonly formTitle = computed(() => this.editingId() ? 'Cập nhật mẫu phiếu' : 'Thêm mẫu phiếu');

  ngOnInit(): void {
    this.loadInitialData();
  }

  protected save(): void {
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

  protected edit(item: MedicalRecordTemplate): void {
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
    this.clearMessages();
    this.activeTab.set('form');
  }

  protected cancelEdit(): void {
    this.resetForm();
    this.clearMessages();
    this.activeTab.set('list');
  }

  protected startCreate(): void {
    this.resetForm();
    this.clearMessages();
    this.activeTab.set('form');
  }

  protected selectSpecialty(value: string): void {
    this.specialty.set(value);
    if (!this.filteredServices().some((service) => service.id === this.clinicServiceId())) {
      this.clinicServiceId.set('');
    }
  }

  protected deactivate(item: MedicalRecordTemplate): void {
    if (!confirm(`Ngưng sử dụng mẫu ${item.name}?`)) return;
    this.clearMessages();
    this.api.deactivateMedicalRecordTemplate(item.id).subscribe({
      next: () => {
        this.notice.set('Đã ngưng sử dụng mẫu phiếu.');
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
    return [content.reason, content.examinationNotes, content.diagnosis, content.conclusion, content.treatmentPlan]
      .filter(Boolean).slice(0, 2).join(' · ') || 'Chưa có nội dung';
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
      },
      error: (response) => {
        this.error.set(apiErrorMessage(response));
        this.loading.set(false);
      },
    });
  }

  private loadTemplates(): void {
    this.api.getMedicalRecordTemplates().subscribe({
      next: (items) => this.items.set(items),
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  private formContent(): MedicalRecordTemplateContent {
    return {
      reason: this.reason(),
      examinationNotes: this.examinationNotes(),
      diagnosis: this.diagnosis(),
      conclusion: this.conclusion(),
      treatmentPlan: this.treatmentPlan(),
      followUpDays: this.followUpDays() ?? undefined,
      followUpNote: this.followUpNote(),
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
  }

  private clearMessages(): void {
    this.error.set('');
    this.notice.set('');
  }
}
