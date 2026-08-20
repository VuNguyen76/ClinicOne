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
  protected readonly selectedMessage = signal<SmsDeliveryResponse | null>(null);

  protected eventTitle(eventKey: string): string {
    if (!eventKey) return 'Thông báo hệ thống';
    const key = eventKey.split(':')[0].trim().toUpperCase();
    const map: Record<string, string> = {
      APPOINTMENT_CREATED: 'Xác nhận đặt lịch khám',
      APPOINTMENT_CONFIRMATION: 'Xác nhận đặt lịch khám',
      APPOINTMENT_CONFIRMED: 'Xác nhận đặt lịch khám',
      APPOINTMENT_CANCELLED: 'Thông báo hủy lịch hẹn',
      APPOINTMENT_RESCHEDULED: 'Thay đổi thời gian hẹn khám',
      APPOINTMENT_RESCHEDULE_REQUIRED: 'Yêu cầu đổi lịch khám',
      APPOINTMENT_REMINDER_24H: 'Nhắc lịch hẹn khám 24h',
      APPOINTMENT_REMINDER_2H: 'Nhắc lịch hẹn khám 2h',
      APPOINTMENT_REMINDER: 'Nhắc lịch hẹn khám',
      APPOINTMENT_LATE_WARNING: 'Cảnh báo trễ lịch hẹn',
      APPOINTMENT_ABSENT: 'Ghi nhận vắng mặt',
      MEDICAL_RECORD_SIGNED: 'Kết quả khám bệnh đã ký số',
      ACCOUNT_SECURITY_LOCKED: 'Khóa bảo mật tài khoản',
      OTP: 'Mã xác thực OTP',
      AUTH_OTP: 'Mã xác thực OTP',
      PASSWORD_RESET: 'Khôi phục mật khẩu',
    };
    return map[key] ?? key.replace(/_/g, ' ');
  }

  protected filteredItems(): SmsDeliveryResponse[] {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.items();
    return this.items().filter((item) =>
      item.phone.toLowerCase().includes(q) ||
      this.eventTitle(item.eventKey).toLowerCase().includes(q) ||
      (item.message && item.message.toLowerCase().includes(q)) ||
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

  protected showPreviewModal(item: SmsDeliveryResponse): void {
    this.selectedMessage.set(item);
  }

  protected closePreviewModal(): void {
    this.selectedMessage.set(null);
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
