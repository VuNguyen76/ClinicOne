import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, BusinessLogResponse, apiErrorMessage } from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-business-audit',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './business-audit.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BusinessAudit implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly entityType = signal('');
  protected readonly entityId = signal('');
  protected readonly items = signal<BusinessLogResponse[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(false);
  protected readonly searched = signal(false);
  protected readonly error = signal('');
  protected readonly activeHashId = signal('');

  ngOnInit(): void {
    this.search();
  }

  protected setEntityType(type: string): void {
    this.entityType.set(type);
    this.search(true);
  }

  protected search(reset = true): void {
    if (reset) this.page.set(0);
    this.loading.set(true);
    this.error.set('');
    const id = this.entityId().trim();
    const type = this.entityType().trim();

    this.authApi.searchBusinessLogs({
      entityType: type || undefined,
      identifier: id || undefined,
      page: this.page(),
      size: 50,
    }).subscribe({
      next: (result) => {
        this.items.set(result.items);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
        this.searched.set(true);
      },
      error: (response) => {
        this.loading.set(false);
        this.searched.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected toggleHashPopover(id: string): void {
    this.activeHashId.update((curr) => (curr === id ? '' : id));
  }

  protected formatEntityType(type: string): string {
    if (!type) return 'Tất cả';
    const map: Record<string, string> = {
      APPOINTMENT: 'Lịch hẹn',
      QUEUE_TICKET: 'Hàng đợi',
      EXAMINATION: 'Khám bệnh',
      BUSINESS_LOG: 'Nhật ký',
    };
    return map[type] ?? type;
  }

  protected formatActor(actor: string): string {
    if (!actor) return '—';
    if (/^[0-9a-fA-F-]{36}$/.test(actor)) {
      return 'Người dùng hệ thống';
    }
    return actor;
  }

  protected previous(): void {
    if (this.page() > 0) {
      this.page.update((value) => value - 1);
      this.search(false);
    }
  }

  protected next(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.update((value) => value + 1);
      this.search(false);
    }
  }

  protected formatDate(value: string): string {
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
  }
}
