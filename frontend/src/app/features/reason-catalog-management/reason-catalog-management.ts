import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { ApiErrorResponse, AuthApiService, ReasonCatalogResponse, apiErrorMessage } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-reason-catalog-management',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './reason-catalog-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReasonCatalogManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly reasons = signal<ReasonCatalogResponse[]>([]);
  protected readonly code = signal('');
  protected readonly label = signal('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly modalOpen = signal(false);

  ngOnInit(): void {
    this.load();
  }

  protected create(): void {
    const code = this.code().trim().toUpperCase();
    const label = this.label().trim();
    if (!/^[A-Z0-9_]{2,50}$/.test(code) || !label) {
      this.error.set('Nhập mã (chữ in hoa, số, gạch dưới) và tên lý do.');
      return;
    }
    this.error.set('');
    this.notice.set('');
    this.saving.set(true);
    this.authApi.createCancellationReason(code, label).subscribe({
      next: (item) => { this.reasons.update((items) => [...items, item].sort((a, b) => a.label.localeCompare(b.label))); this.code.set(''); this.label.set(''); this.notice.set('Đã thêm lý do.'); this.saving.set(false); this.modalOpen.set(false); },
      error: (response: ApiErrorResponse) => { this.error.set(apiErrorMessage(response)); this.saving.set(false); },
    });
  }

  protected openCreate(): void {
    this.error.set('');
    this.notice.set('');
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    if (this.saving()) return;
    this.modalOpen.set(false);
  }

  protected toggle(item: ReasonCatalogResponse): void {
    this.error.set('');
    this.notice.set('');
    this.authApi.setCancellationReasonActive(item.id, !item.active).subscribe({
      next: (updated) => { this.reasons.update((items) => items.map((current) => current.id === updated.id ? updated : current)); this.notice.set(updated.active ? 'Đã bật lý do.' : 'Đã tạm ngưng lý do.'); },
      error: (response: ApiErrorResponse) => this.error.set(apiErrorMessage(response)),
    });
  }

  private load(): void {
    this.authApi.getAdminCancellationReasons().subscribe({
      next: (items) => { this.reasons.set(items); this.loading.set(false); },
      error: (response: ApiErrorResponse) => { this.error.set(apiErrorMessage(response)); this.loading.set(false); },
    });
  }
}
