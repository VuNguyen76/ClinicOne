import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { InvoiceResponse, PaymentMethod, apiErrorMessage, AuthApiService } from '../../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-reception-payments',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './reception-payments.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceptionPayments {
  private readonly authApi = inject(AuthApiService);

  protected readonly printing = signal(false);
  protected readonly appointmentCode = signal('');
  protected readonly receipt = signal<InvoiceResponse | null>(null);
  protected readonly paymentMethod = signal<PaymentMethod>('CASH');
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  protected createReceipt(): void {
    if (this.loading()) return;
    const code = this.appointmentCode().trim();
    this.error.set('');
    this.notice.set('');
    if (!code) {
      this.error.set('Vui lòng nhập mã lịch hẹn.');
      return;
    }

    this.receipt.set(null);
    this.loading.set(true);
    this.authApi.createPaymentReceiptForAppointment(code).subscribe({
      next: (receipt) => {
        this.receipt.set(receipt);
        if (this.appointmentCode().trim() === code) this.appointmentCode.set(receipt.appointmentCode);
        this.loading.set(false);
      },
      error: (response) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected markPaid(): void {
    const receipt = this.receipt();
    if (!receipt || receipt.status !== 'PENDING' || this.saving()) return;

    this.error.set('');
    this.notice.set('');
    this.saving.set(true);
    this.authApi.markInvoicePaid(receipt.invoiceCode, receipt.totalAmount, this.paymentMethod()).subscribe({
      next: (updated) => {
        this.receipt.set(updated);
        this.notice.set('Đã ghi nhận thanh toán.');
        this.saving.set(false);
      },
      error: (response) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected printReceipt(): void {
    this.printing.set(true);
    window.print();
    this.printing.set(false);
  }

  protected formatAmount(amount: number): string {
    return new Intl.NumberFormat('vi-VN').format(amount);
  }

  protected paymentMethodLabel(method: PaymentMethod | null): string {
    switch (method) {
      case 'CASH': return 'Tiền mặt';
      case 'BANK_TRANSFER': return 'Chuyển khoản';
      case 'CARD': return 'Thẻ';
      default: return '—';
    }
  }
}
