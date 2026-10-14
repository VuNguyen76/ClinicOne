import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import {
  ApiErrorResponse,
  AuthApiService,
  ReasonCatalogResponse,
  SpecialtyOption,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';

export interface ClinicalReasonItem {
  id: string;
  specialtyCode: string;
  specialtyName: string;
  symptomName: string;
  sortOrder: number;
  active: boolean;
}

@Component({
  selector: 'app-reason-catalog-management',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './reason-catalog-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReasonCatalogManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);

  protected readonly activeTab = signal<'clinical' | 'cancellation'>('cancellation');

  // Clinical reasons state
  protected readonly clinicalReasons = signal<ClinicalReasonItem[]>([]);
  protected readonly specialties = signal<SpecialtyOption[]>([]);
  protected readonly selectedSpecialtyFilter = signal('');
  protected readonly clinicalSpecialtyCode = signal('');
  protected readonly clinicalSpecialtyName = signal('');
  protected readonly clinicalSymptomName = signal('');
  protected readonly clinicalSortOrder = signal(1);
  protected readonly clinicalEditingId = signal<string | null>(null);
  protected readonly clinicalModalOpen = signal(false);

  // Cancellation reasons state
  protected readonly reasons = signal<ReasonCatalogResponse[]>([]);
  protected readonly code = signal('');
  protected readonly label = signal('');
  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);

  // Shared state
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly searchTerm = signal('');

  protected readonly filteredClinicalReasons = computed(() => {
    const q = this.searchTerm().trim().toLowerCase();
    const spec = this.selectedSpecialtyFilter().trim();
    let list = this.clinicalReasons();
    if (spec) {
      list = list.filter((r) => r.specialtyName.toLowerCase() === spec.toLowerCase());
    }
    if (!q) return list;
    return list.filter((r) =>
      r.symptomName.toLowerCase().includes(q) ||
      r.specialtyName.toLowerCase().includes(q) ||
      r.specialtyCode.toLowerCase().includes(q)
    );
  });

  protected readonly filteredReasons = computed(() => {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.reasons();
    return this.reasons().filter((r) =>
      r.code.toLowerCase().includes(q) ||
      r.label.toLowerCase().includes(q)
    );
  });

  protected totalClinicalCount(): number {
    return this.clinicalReasons().length;
  }

  protected activeClinicalCount(): number {
    return this.clinicalReasons().filter((r) => r.active).length;
  }

  protected totalReasonsCount(): number {
    return this.reasons().length;
  }

  protected activeReasonsCount(): number {
    return this.reasons().filter((r) => r.active).length;
  }

  ngOnInit(): void {
    this.loadSpecialties();
    this.loadClinical();
    this.load();
  }

  protected switchTab(tab: 'clinical' | 'cancellation'): void {
    this.activeTab.set(tab);
    this.error.set('');
    this.notice.set('');
    this.searchTerm.set('');
  }

  // --- CLINICAL REASONS CRUD ---
  protected loadClinical(): void {
    if (!this.authApi.getClinicalReasons) return;
    this.authApi.getClinicalReasons(undefined, false).subscribe({
      next: (items) => {
        this.clinicalReasons.set(items);
        this.loading.set(false);
      },
      error: (res) => {
        this.error.set(apiErrorMessage(res));
        this.loading.set(false);
      },
    });
  }

  private loadSpecialties(): void {
    if (!this.authApi.getSpecialties) return;
    this.authApi.getSpecialties().subscribe({
      next: (items) => this.specialties.set(items),
      error: () => {},
    });
  }

  protected openCreateClinical(): void {
    this.error.set('');
    this.notice.set('');
    this.clinicalEditingId.set(null);
    const firstSpec = this.specialties()[0];
    this.clinicalSpecialtyCode.set(firstSpec ? firstSpec.code : 'NOI');
    this.clinicalSpecialtyName.set(firstSpec ? firstSpec.name : 'Khám Tổng Quát');
    this.clinicalSymptomName.set('');
    this.clinicalSortOrder.set(this.clinicalReasons().length + 1);
    this.clinicalModalOpen.set(true);
  }

  protected openEditClinical(item: ClinicalReasonItem): void {
    this.error.set('');
    this.notice.set('');
    this.clinicalEditingId.set(item.id);
    this.clinicalSpecialtyCode.set(item.specialtyCode);
    this.clinicalSpecialtyName.set(item.specialtyName);
    this.clinicalSymptomName.set(item.symptomName);
    this.clinicalSortOrder.set(item.sortOrder);
    this.clinicalModalOpen.set(true);
  }

  protected closeClinicalModal(): void {
    if (this.saving()) return;
    this.clinicalModalOpen.set(false);
    this.clinicalEditingId.set(null);
  }

  protected onSpecialtyChange(code: string): void {
    this.clinicalSpecialtyCode.set(code);
    const spec = this.specialties().find((s) => s.code === code);
    if (spec) {
      this.clinicalSpecialtyName.set(spec.name);
    }
  }

  protected saveClinical(): void {
    const symptom = this.clinicalSymptomName().trim();
    if (symptom.length < 3) {
      this.error.set('Lý do khám / triệu chứng phải từ 3 ký tự trở lên.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.notice.set('');

    const payload = {
      specialtyCode: this.clinicalSpecialtyCode().trim(),
      specialtyName: this.clinicalSpecialtyName().trim(),
      symptomName: symptom,
      sortOrder: Number(this.clinicalSortOrder()) || 0,
    };

    const editingId = this.clinicalEditingId();
    const req = editingId
      ? this.authApi.updateClinicalReason(editingId, payload)
      : this.authApi.createClinicalReason(payload);

    req.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.clinicalModalOpen.set(false);
        this.notice.set(editingId ? 'Đã cập nhật lý do khám lâm sàng.' : 'Đã thêm lý do khám lâm sàng mới.');
        this.loadClinical();
        setTimeout(() => this.notice.set(''), 4000);
      },
      error: (res) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(res));
      },
    });
  }

  protected toggleClinicalActive(item: ClinicalReasonItem): void {
    this.authApi.toggleClinicalReasonActive(item.id).subscribe({
      next: (updated) => {
        this.clinicalReasons.update((items) =>
          items.map((r) => (r.id === updated.id ? { ...r, active: updated.active } : r))
        );
        this.notice.set(updated.active ? 'Đã kích hoạt lý do khám.' : 'Đã tạm ngưng lý do khám.');
        setTimeout(() => this.notice.set(''), 4000);
      },
      error: (res) => this.error.set(apiErrorMessage(res)),
    });
  }

  protected deleteClinical(item: ClinicalReasonItem): void {
    if (!confirm(`Xóa hoàn toàn lý do "${item.symptomName}"?`)) return;
    this.authApi.deleteClinicalReason(item.id).subscribe({
      next: () => {
        this.clinicalReasons.update((items) => items.filter((r) => r.id !== item.id));
        this.notice.set('Đã xóa lý do khám.');
        setTimeout(() => this.notice.set(''), 4000);
      },
      error: (res) => this.error.set(apiErrorMessage(res)),
    });
  }

  // --- CANCELLATION REASONS CRUD (PRESERVED) ---
  protected openCreate(): void {
    this.error.set('');
    this.notice.set('');
    this.editingId.set(null);
    this.code.set('');
    this.label.set('');
    this.modalOpen.set(true);
  }

  protected openEdit(item: ReasonCatalogResponse): void {
    this.error.set('');
    this.notice.set('');
    this.editingId.set(item.id);
    this.code.set(item.code);
    this.label.set(item.label);
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    if (this.saving()) return;
    this.modalOpen.set(false);
    this.editingId.set(null);
  }

  protected save(): void {
    const code = this.code().trim().toUpperCase();
    const label = this.label().trim();
    if (!/^[A-Z0-9_]{2,50}$/.test(code) || !label) {
      this.error.set('Nhập mã (chữ in hoa, số, gạch dưới) và tên lý do.');
      return;
    }
    this.error.set('');
    this.notice.set('');
    this.saving.set(true);
    const editingId = this.editingId();
    const request = editingId
      ? this.authApi.updateCancellationReason(editingId, code, label)
      : this.authApi.createCancellationReason(code, label);
    request.subscribe({
      next: (item) => {
        this.reasons.update((items) => {
          const next = editingId ? items.map((current) => (current.id === item.id ? item : current)) : [...items, item];
          return next.sort((a, b) => a.label.localeCompare(b.label));
        });
        this.code.set('');
        this.label.set('');
        this.editingId.set(null);
        this.notice.set(editingId ? 'Đã cập nhật lý do hủy lịch.' : 'Đã thêm lý do hủy lịch.');
        this.saving.set(false);
        this.modalOpen.set(false);
      },
      error: (response: ApiErrorResponse) => {
        this.error.set(apiErrorMessage(response));
        this.saving.set(false);
      },
    });
  }

  protected toggle(item: ReasonCatalogResponse): void {
    this.error.set('');
    this.notice.set('');
    this.authApi.setCancellationReasonActive(item.id, !item.active).subscribe({
      next: (updated) => {
        this.reasons.update((items) => items.map((current) => (current.id === updated.id ? updated : current)));
        this.notice.set(updated.active ? 'Đã bật lý do.' : 'Đã tạm ngưng lý do.');
      },
      error: (response: ApiErrorResponse) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected load(): void {
    this.authApi.getAdminCancellationReasons().subscribe({
      next: (items) => {
        this.reasons.set(items);
        this.loading.set(false);
      },
      error: (response: ApiErrorResponse) => {
        this.error.set(apiErrorMessage(response));
        this.loading.set(false);
      },
    });
  }
}
