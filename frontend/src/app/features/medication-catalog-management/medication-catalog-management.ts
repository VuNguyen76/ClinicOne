import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { ApiErrorResponse, AuthApiService, MedicationSuggestionResponse, apiErrorMessage } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-medication-catalog-management',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './medication-catalog-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MedicationCatalogManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly medications = signal<MedicationSuggestionResponse[]>([]);
  protected readonly query = signal('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly code = signal('');
  protected readonly name = signal('');
  protected readonly filteredMedications = computed(() => {
    const query = this.query().trim().toLocaleLowerCase();
    if (!query) return this.medications();
    return this.medications().filter((item) => `${item.code} ${item.name}`.toLocaleLowerCase().includes(query));
  });
  protected readonly activeCount = computed(() => this.medications().filter((item) => item.active).length);
  protected readonly inactiveCount = computed(() => this.medications().filter((item) => !item.active).length);

  ngOnInit(): void {
    this.load();
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.code.set('');
    this.name.set('');
    this.error.set('');
    this.modalOpen.set(true);
  }

  protected openEdit(item: MedicationSuggestionResponse): void {
    this.editingId.set(item.id);
    this.code.set(item.code);
    this.name.set(item.name);
    this.error.set('');
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    if (!this.saving()) this.modalOpen.set(false);
  }

  protected save(): void {
    const code = this.code().trim().toUpperCase();
    const name = this.name().trim();
    if (!/^[A-Z0-9_-]{2,50}$/.test(code) || !name || name.length > 200) {
      this.error.set('Nhập mã thuốc hợp lệ và tên thuốc không quá 200 ký tự.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const editingId = this.editingId();
    const request = editingId ? this.authApi.updateMedication(editingId, code, name) : this.authApi.createMedication(code, name);
    request.subscribe({
      next: (saved) => {
        this.medications.update((items) => {
          const next = editingId ? items.map((item) => item.id === saved.id ? saved : item) : [...items, saved];
          return next.sort((a, b) => a.name.localeCompare(b.name, 'vi'));
        });
        this.saving.set(false);
        this.modalOpen.set(false);
        this.notice.set(editingId ? 'Đã cập nhật thuốc.' : 'Đã thêm thuốc.');
        setTimeout(() => this.notice.set(''), 4000);
      },
      error: (response: ApiErrorResponse) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected toggleActive(item: MedicationSuggestionResponse): void {
    this.toggle(item);
  }

  protected toggle(item: MedicationSuggestionResponse): void {
    this.error.set('');
    this.notice.set('');
    this.authApi.setMedicationActive(item.id, !item.active).subscribe({
      next: (saved) => {
        this.medications.update((items) => items.map((current) => current.id === saved.id ? saved : current));
        this.notice.set(saved.active ? 'Đã cho phép dùng lại thuốc.' : 'Đã tạm ngưng thuốc.');
        setTimeout(() => this.notice.set(''), 4000);
      },
      error: (response: ApiErrorResponse) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected load(): void {
    this.authApi.getAdminMedications().subscribe({
      next: (items) => { this.medications.set(items); this.loading.set(false); },
      error: (response: ApiErrorResponse) => { this.error.set(apiErrorMessage(response)); this.loading.set(false); },
    });
  }
}
