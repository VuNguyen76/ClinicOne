import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { ElectronicInvoiceData } from '../../core/invoice/vietnamese-currency';

@Component({
  selector: 'app-electronic-invoice',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './electronic-invoice.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ElectronicInvoiceComponent {
  readonly invoice = input.required<ElectronicInvoiceData>();
  readonly printOnly = input<boolean>(false);

  readonly formattedDate = computed(() => {
    const raw = this.invoice().issueDate;
    if (!raw) return { day: '12', month: '10', year: '2026' };
    const parts = raw.split('-');
    if (parts.length === 3) {
      return { day: parts[2], month: parts[1], year: parts[0] };
    }
    const d = new Date(raw);
    return {
      day: String(d.getDate()).padStart(2, '0'),
      month: String(d.getMonth() + 1).padStart(2, '0'),
      year: String(d.getFullYear()),
    };
  });

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('vi-VN').format(value);
  }
}
