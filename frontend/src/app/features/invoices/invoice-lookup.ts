import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { PatientHeader } from '../../shared/patient-header/patient-header';
import { AuthApiService, PublicInvoiceResponse, PublicPatientProfileDataResponse, apiErrorMessage } from '../../core/auth/auth-api.service';

export type LookupStep = 'phone' | 'otp' | 'results';
export type LookupTab = 'invoices' | 'prescriptions';

@Component({
  selector: 'app-invoice-lookup',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatIconModule, PatientHeader],
  templateUrl: './invoice-lookup.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvoiceLookup {
  private readonly authApi = inject(AuthApiService);
  private lookupAttempt = 0;

  readonly step = signal<LookupStep>('phone');
  readonly phone = signal('');
  readonly code = signal('');
  readonly requestingOtp = signal(false);
  readonly searching = signal(false);
  readonly error = signal('');
  readonly profiles = signal<PublicPatientProfileDataResponse[]>([]);
  readonly selectedProfileId = signal<string | null>(null);
  readonly selectedTab = signal<LookupTab>('invoices');
  readonly selectedInvoice = signal<PublicInvoiceResponse | null>(null);
  readonly selectedProfile = computed(() =>
    this.profiles().find((profile) => profile.profileId === this.selectedProfileId()) ?? null,
  );

  requestOtp(): void {
    if (this.requestingOtp()) return;
    this.error.set('');
    const phone = this.phone().trim();
    if (!phone) {
      this.error.set('Vui lòng nhập số điện thoại.');
      return;
    }
    if (!/^0\d{9}$/.test(phone)) {
      this.error.set('Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.');
      return;
    }

    this.requestingOtp.set(true);
    this.authApi.requestSmsOtp(phone, 'RECEIPT_LOOKUP').subscribe({
      next: () => {
        this.requestingOtp.set(false);
        if (this.phone().trim() !== phone) {
          this.error.set('Số điện thoại đã thay đổi. Vui lòng gửi lại OTP cho số mới.');
          return;
        }
        this.code.set('');
        this.step.set('otp');
      },
      error: (response) => {
        this.requestingOtp.set(false);
        if (this.phone().trim() === phone) this.error.set(apiErrorMessage(response));
      },
    });
  }

  lookup(): void {
    if (this.searching()) return;

    this.error.set('');
    const phone = this.phone().trim();
    const code = this.code().trim();
    if (!/^\d{6}$/.test(code)) {
      this.error.set('Mã OTP phải gồm 6 chữ số.');
      return;
    }

    this.searching.set(true);
    const attempt = ++this.lookupAttempt;
    this.authApi.lookupPatientData(phone, code).subscribe({
      next: (response) => {
        if (attempt !== this.lookupAttempt) return;
        this.searching.set(false);
        this.profiles.set(response.profiles);
        this.selectedProfileId.set(response.profiles.length === 1 ? response.profiles[0].profileId : null);
        this.selectedTab.set('invoices');
        this.selectedInvoice.set(null);
        this.step.set('results');
      },
      error: (response) => {
        if (attempt !== this.lookupAttempt) return;
        this.searching.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  selectProfile(profileId: string): void {
    this.selectedProfileId.set(profileId);
    this.selectedInvoice.set(null);
  }

  setTab(tab: LookupTab): void {
    this.selectedTab.set(tab);
    this.selectedInvoice.set(null);
  }

  showInvoice(invoice: PublicInvoiceResponse): void {
    this.selectedInvoice.set(invoice);
  }

  clearInvoice(): void {
    this.selectedInvoice.set(null);
  }

  changePhone(): void {
    this.lookupAttempt++;
    this.searching.set(false);
    this.step.set('phone');
    this.code.set('');
    this.error.set('');
    this.profiles.set([]);
    this.selectedProfileId.set(null);
    this.selectedInvoice.set(null);
  }

  printInvoice(): void {
    window.print();
  }
}
