import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { PatientHeader } from '../../shared/patient-header/patient-header';
import { AuthApiService, InvoiceResponse, apiErrorMessage } from '../../core/auth/auth-api.service';

export type LookupType = 'invoiceCode' | 'appointmentCode';

@Component({
  selector: 'app-invoice-lookup',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatIconModule, PatientHeader],
  templateUrl: './invoice-lookup.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvoiceLookup implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly authApi = inject(AuthApiService);

  readonly lookupType = signal<LookupType>('invoiceCode');
  readonly invoiceCodeInput = signal('');
  readonly appointmentCodeInput = signal('');
  readonly searching = signal(false);
  readonly loadingReceipts = signal(false);
  readonly error = signal('');
  readonly receiptsError = signal('');
  readonly patientReceipts = signal<InvoiceResponse[]>([]);
  readonly invoiceResult = signal<InvoiceResponse | null>(null);

  ngOnInit(): void {
    this.loadReceipts();
    const query = this.route.snapshot.queryParamMap;
    const code = query.get('code') || query.get('appointmentCode');
    const invoiceCode = query.get('invoiceCode') || query.get('lookupCode') || query.get('id');

    if (invoiceCode) {
      this.lookupType.set('invoiceCode');
      this.invoiceCodeInput.set(invoiceCode);
      this.search();
    } else if (code) {
      this.lookupType.set('appointmentCode');
      this.appointmentCodeInput.set(code);
      this.search();
    }
  }

  loadReceipts(): void {
    this.loadingReceipts.set(true);
    this.receiptsError.set('');
    this.authApi.getInvoices().subscribe({
      next: (invoices) => {
        this.patientReceipts.set(invoices);
        this.loadingReceipts.set(false);
      },
      error: (response) => {
        this.receiptsError.set(apiErrorMessage(response));
        this.loadingReceipts.set(false);
      },
    });
  }

  showReceipt(receipt: InvoiceResponse): void {
    this.invoiceResult.set(receipt);
    this.error.set('');
  }

  setLookupType(type: LookupType): void {
    this.lookupType.set(type);
    this.error.set('');
  }

  search(): void {
    this.error.set('');
    const type = this.lookupType();
    const code = type === 'invoiceCode' ? this.invoiceCodeInput().trim() : this.appointmentCodeInput().trim();

    if (!code) {
      this.error.set(type === 'invoiceCode' ? 'Vui lòng nhập mã phiếu thanh toán.' : 'Vui lòng nhập mã lịch hẹn.');
      return;
    }

    this.searching.set(true);
    this.invoiceResult.set(null);
    const request = type === 'invoiceCode'
      ? this.authApi.getInvoice(code)
      : this.authApi.getInvoiceByAppointmentCode(code);
    request.subscribe({
      next: (invoice) => {
        this.searching.set(false);
        this.invoiceResult.set(invoice);
      },
      error: (response) => {
        this.searching.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  printInvoice(): void {
    window.print();
  }

  resetSearch(): void {
    this.invoiceResult.set(null);
    this.invoiceCodeInput.set('');
    this.appointmentCodeInput.set('');
    this.error.set('');
  }
}
