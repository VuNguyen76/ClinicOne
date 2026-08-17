import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AuthApiService, MedicalRecordTemplate, apiErrorMessage } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-medical-record-template-management',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, RouterLink],
  templateUrl: './medical-record-template-management.html',
})
export class MedicalRecordTemplateManagement implements OnInit {
  private readonly api = inject(AuthApiService);
  protected readonly items = signal<MedicalRecordTemplate[]>([]);
  protected readonly code = signal('');
  protected readonly name = signal('');
  protected readonly specialty = signal('');
  protected readonly fieldDefinition = signal('reason|Lý do khám|required');
  protected readonly description = signal('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  ngOnInit(): void { this.load(); }

  protected load(): void {
    this.loading.set(true);
    this.api.getMedicalRecordTemplates().subscribe({
      next: (items) => { this.items.set(items); this.loading.set(false); },
      error: (response) => { this.error.set(apiErrorMessage(response)); this.loading.set(false); },
    });
  }

  protected create(): void {
    if (!this.code().trim() || !this.name().trim() || !this.specialty().trim() || !this.fieldDefinition().trim()) {
      this.error.set('Nhập mã, tên, chuyên khoa và các trường của mẫu.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.api.createMedicalRecordTemplate({ code: this.code(), name: this.name(), specialty: this.specialty(),
      clinicServiceId: null, description: this.description(), fieldDefinition: this.fieldDefinition() }).subscribe({
      next: () => { this.code.set(''); this.name.set(''); this.description.set(''); this.notice.set('Đã thêm mẫu phiếu.'); this.saving.set(false); this.load(); },
      error: (response) => { this.error.set(apiErrorMessage(response)); this.saving.set(false); },
    });
  }

  protected deactivate(item: MedicalRecordTemplate): void {
    if (!confirm(`Ngưng sử dụng mẫu ${item.name}?`)) return;
    this.api.deactivateMedicalRecordTemplate(item.id).subscribe({
      next: () => { this.notice.set('Đã ngưng sử dụng mẫu phiếu.'); this.load(); },
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }
}
