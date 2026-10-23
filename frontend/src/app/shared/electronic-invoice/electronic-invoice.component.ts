import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { ElectronicInvoiceData } from '../../core/invoice/vietnamese-currency';

const VI_CURRENCY_FORMATTER = new Intl.NumberFormat('vi-VN');

@Component({
  selector: 'app-electronic-invoice',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './electronic-invoice.component.html',
  styles: [`
    :host {
      display: block;
    }
    .invoice-formal-font {
      font-family: 'Times New Roman', Times, 'Liberation Serif', serif;
    }
    @media print {
      @page {
        size: A4 portrait;
        margin: 8mm 10mm;
      }
      .electronic-invoice-sheet {
        border: none !important;
        box-shadow: none !important;
        padding: 0 !important;
        max-width: 100% !important;
        width: 100% !important;
        break-inside: avoid !important;
        page-break-inside: avoid !important;
        font-size: 9.5pt !important;
        line-height: 1.25 !important;
      }
      .electronic-invoice-sheet table {
        font-size: 9pt !important;
      }
      .electronic-invoice-sheet th,
      .electronic-invoice-sheet td {
        padding: 3px 6px !important;
        height: auto !important;
      }
      .invoice-signatures {
        break-inside: avoid !important;
        page-break-inside: avoid !important;
        display: grid !important;
        grid-template-columns: repeat(3, 1fr) !important;
      }
    }
  `],
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
    return VI_CURRENCY_FORMATTER.format(value);
  }
}
