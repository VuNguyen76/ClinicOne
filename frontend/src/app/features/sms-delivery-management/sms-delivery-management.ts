import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, SmsDeliveryResponse, apiErrorMessage } from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-sms-delivery-management',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './sms-delivery-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SmsDeliveryManagement implements OnInit {
  private readonly api = inject(AuthApiService);
  protected readonly items = signal<SmsDeliveryResponse[]>([]);
  protected readonly searchTerm = signal('');
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  protected filteredItems(): SmsDeliveryResponse[] {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.items();
    return this.items().filter((item) =>
      item.phone.toLowerCase().includes(q) ||
      item.eventKey.toLowerCase().includes(q) ||
      (item.lastError && item.lastError.toLowerCase().includes(q))
    );
  }

  protected totalCount(): number {
    return this.items().length;
  }

  protected sentCount(): number {
    return this.items().filter((item) => item.status === 'SENT').length;
  }

  protected failedCount(): number {
    return this.items().filter((item) => item.status === 'FAILED' || item.status === 'RETRY_WAITING').length;
  }

  ngOnInit(): void {
    this.refresh();
  }

  protected refresh(): void {
    this.loading.set(true);
    this.api.getSmsDeliveries().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: (response) => {
        this.error.set(apiErrorMessage(response));
        this.loading.set(false);
      },
    });
  }

  protected statusLabel(status: string): string {
    return ({ PENDING: 'Đang chờ', PROCESSING: 'Đang gửi', SENT: 'Đã gửi', RETRY_WAITING: 'Chờ thử lại', FAILED: 'Gửi thất bại' } as Record<string, string>)[status] ?? status;
  }

  protected retry(item: SmsDeliveryResponse): void {
    const key = `sms-retry-${item.id}-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`;
    this.api.retrySmsDelivery(item.id, key).subscribe({
      next: () => this.refresh(),
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }
}
