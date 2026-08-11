import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AccountMenu } from '../../shared/account-menu/account-menu';
import { ApiErrorResponse, AuthApiService, DiagnosisSuggestionResponse, apiErrorMessage } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-diagnosis-catalog-management',
  standalone: true,
  imports: [FormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './diagnosis-catalog-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DiagnosisCatalogManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly diagnoses = signal<DiagnosisSuggestionResponse[]>([]);
  protected readonly query = signal('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly code = signal('');
  protected readonly name = signal('');
  protected readonly filteredDiagnoses = computed(() => {
    const query = this.query().trim().toLocaleLowerCase();
    if (!query) return this.diagnoses();
    return this.diagnoses().filter((item) => `${item.code} ${item.name}`.toLocaleLowerCase().includes(query));
  });
  protected readonly activeCount = computed(() => this.diagnoses().filter((item) => item.active).length);

  ngOnInit(): void { this.load(); }

  protected openCreate(): void {
    this.editingId.set(null); this.code.set(''); this.name.set(''); this.error.set(''); this.modalOpen.set(true);
  }

  protected openEdit(item: DiagnosisSuggestionResponse): void {
    this.editingId.set(item.id); this.code.set(item.code); this.name.set(item.name); this.error.set(''); this.modalOpen.set(true);
  }

  protected closeModal(): void { if (!this.saving()) this.modalOpen.set(false); }

  protected save(): void {
    const code = this.code().trim().toUpperCase();
    const name = this.name().trim();
    if (!/^[A-Z0-9_-]{2,50}$/.test(code) || !name || name.length > 200) {
      this.error.set('Nhập mã chẩn đoán hợp lệ và tên không quá 200 ký tự.');
      return;
    }
    this.saving.set(true); this.error.set('');
    const editingId = this.editingId();
    const request = editingId ? this.authApi.updateDiagnosis(editingId, code, name) : this.authApi.createDiagnosis(code, name);
    request.subscribe({
      next: (saved) => {
        this.diagnoses.update((items) => {
          const next = editingId ? items.map((item) => item.id === saved.id ? saved : item) : [...items, saved];
          return next.sort((a, b) => a.name.localeCompare(b.name, 'vi'));
        });
        this.saving.set(false); this.modalOpen.set(false);
        this.notice.set(editingId ? 'Đã cập nhật chẩn đoán.' : 'Đã thêm chẩn đoán.');
      },
      error: (response: ApiErrorResponse) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected toggle(item: DiagnosisSuggestionResponse): void {
    this.error.set(''); this.notice.set('');
    this.authApi.setDiagnosisActive(item.id, !item.active).subscribe({
      next: (saved) => {
        this.diagnoses.update((items) => items.map((current) => current.id === saved.id ? saved : current));
        this.notice.set(saved.active ? 'Đã cho phép dùng lại chẩn đoán.' : 'Đã tạm ngưng chẩn đoán.');
      },
      error: (response: ApiErrorResponse) => this.error.set(apiErrorMessage(response)),
    });
  }

  private load(): void {
    this.authApi.getAdminDiagnoses().subscribe({
      next: (items) => { this.diagnoses.set(items); this.loading.set(false); },
      error: (response: ApiErrorResponse) => { this.error.set(apiErrorMessage(response)); this.loading.set(false); },
    });
  }
}
