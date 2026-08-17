import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, SmsDeliveryResponse, apiErrorMessage } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-sms-delivery-management',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './sms-delivery-management.html',
})
export class SmsDeliveryManagement implements OnInit {
  private readonly api = inject(AuthApiService);
  protected readonly items = signal<SmsDeliveryResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  ngOnInit(): void { this.refresh(); }

  protected refresh(): void {
    this.loading.set(true);
    this.api.getSmsDeliveries().subscribe({
      next: (items) => { this.items.set(items); this.loading.set(false); },
      error: (response) => { this.error.set(apiErrorMessage(response)); this.loading.set(false); },
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
