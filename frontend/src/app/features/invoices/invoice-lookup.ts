import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { PatientHeader } from '../../shared/patient-header/patient-header';
import { ElectronicInvoiceComponent } from '../../shared/electronic-invoice/electronic-invoice.component';
import { ElectronicInvoiceData, buildElectronicInvoice } from '../../core/invoice/vietnamese-currency';
import { AuthApiService } from '../../core/auth/auth-api.service';

export type LookupType = 'lookupCode' | 'patientCode';

@Component({
  selector: 'app-invoice-lookup',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatIconModule, PatientHeader, ElectronicInvoiceComponent],
  templateUrl: './invoice-lookup.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvoiceLookup implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly authApi = inject(AuthApiService);

  readonly lookupType = signal<LookupType>('lookupCode');
  readonly lookupCodeInput = signal('');
  readonly patientCodeInput = signal('');
  readonly captchaInput = signal('');
  readonly currentCaptcha = signal('78K2');
  readonly searching = signal(false);
  readonly error = signal('');
  readonly invoiceResult = signal<ElectronicInvoiceData | null>(null);

  ngOnInit(): void {
    this.refreshCaptcha();
    const query = this.route.snapshot.queryParamMap;
    const code = query.get('code') || query.get('appointmentCode');
    const lookup = query.get('lookupCode') || query.get('id');

    if (lookup) {
      this.lookupType.set('lookupCode');
      this.lookupCodeInput.set(lookup);
      this.search();
    } else if (code) {
      this.lookupType.set('patientCode');
      this.patientCodeInput.set(code);
      this.search();
    }
  }

  setLookupType(type: LookupType): void {
    this.lookupType.set(type);
    this.error.set('');
  }

  refreshCaptcha(): void {
    const chars = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    let code = '';
    for (let i = 0; i < 4; i++) {
      code += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    this.currentCaptcha.set(code);
    this.captchaInput.set('');
  }

  search(): void {
    this.error.set('');
    const type = this.lookupType();
    const code = type === 'lookupCode' ? this.lookupCodeInput().trim() : this.patientCodeInput().trim();

    if (!code) {
      this.error.set(type === 'lookupCode' ? 'Vui lòng nhập Mã tra cứu hóa đơn.' : 'Vui lòng nhập Mã bệnh nhân / Mã lịch hẹn.');
      return;
    }

    // Verify captcha if user manually entered
    if (this.captchaInput().trim() && this.captchaInput().trim().toUpperCase() !== this.currentCaptcha()) {
      this.error.set('Mã xác thực không chính xác. Vui lòng nhập lại.');
      this.refreshCaptcha();
      return;
    }

    this.searching.set(true);

    // Check if token exists to query patient records
    const token = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('clinicOneAccessToken') : null;
    if (token) {
      this.authApi.getMedicalRecords({ page: 0, size: 20 }).subscribe({
        next: (page) => {
          this.searching.set(false);
          const found = page.items?.find((r) => {
            const matchesAppt = r.appointmentCode?.toLowerCase() === code.toLowerCase();
            const inv = buildElectronicInvoice({ appointmentCode: r.appointmentCode });
            const matchesLookup = inv.lookupCode === code;
            return matchesAppt || matchesLookup;
          });

          if (found) {
            this.invoiceResult.set(buildElectronicInvoice({
              appointmentCode: found.appointmentCode,
              patientName: found.patientName,
              doctorName: found.doctorName,
              specialty: found.specialty,
              serviceName: found.specialty ? `Khám chuyên khoa ${found.specialty}` : 'Khám chuyên khoa',
              signedAt: found.signedAt,
              prescriptionLines: found.prescriptionLines,
            }));
          } else {
            // Build demo/sample invoice matching the search code
            this.buildFallbackInvoice(code);
          }
        },
        error: () => {
          this.searching.set(false);
          this.buildFallbackInvoice(code);
        },
      });
    } else {
      this.searching.set(false);
      this.buildFallbackInvoice(code);
    }
  }

  private buildFallbackInvoice(code: string): void {
    const isApptCode = code.toUpperCase().startsWith('CL-');
    const apptCode = isApptCode ? code.toUpperCase() : `CL-${code.slice(0, 10).toUpperCase()}`;

    this.invoiceResult.set(buildElectronicInvoice({
      appointmentCode: apptCode,
      patientName: 'NGUYỄN THANH VŨ',
      patientPhone: '0900000001',
      specialty: 'Khám Tổng Quát',
      serviceName: 'Khám chuyên khoa Nội tổng quát',
      doctorName: 'BS. CKII Nguyễn An',
      prescriptionLines: [
        { medicationName: 'Acetylcystein 200mg (Long đờm, tiêu nhầy)', quantity: 2, unit: 'Gói' },
        { medicationName: 'Paracetamol 500mg (Hạ sốt, giảm đau)', quantity: 10, unit: 'Viên' },
      ],
    }));
  }

  printInvoice(): void {
    window.print();
  }

  resetSearch(): void {
    this.invoiceResult.set(null);
    this.lookupCodeInput.set('');
    this.patientCodeInput.set('');
    this.captchaInput.set('');
    this.refreshCaptcha();
  }
}
