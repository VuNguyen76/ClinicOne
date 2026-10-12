import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import { apiErrorMessage, AuthApiService, MedicalRecordResponse } from '../../core/auth/auth-api.service';
import { PatientHeader } from '../../shared/patient-header/patient-header';
import { formatClinicDate, formatClinicMediumDateTime } from '../../core/time/clinic-time';
import { ElectronicInvoiceComponent } from '../../shared/electronic-invoice/electronic-invoice.component';
import { ElectronicInvoiceData } from '../../core/invoice/vietnamese-currency';

@Component({
  selector: 'app-medical-record-detail',
  standalone: true,
  imports: [RouterLink, MatIconModule, PatientHeader, ElectronicInvoiceComponent],
  templateUrl: './medical-record-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MedicalRecordDetail implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly record = signal<MedicalRecordResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly viewInvoiceModal = signal(false);

  protected readonly invoice = computed((): ElectronicInvoiceData | null => {
    // Invoice issuance is not implemented by BE; never synthesize invoice data in FE.
    return null;
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('Không tìm thấy phiếu khám.');
      this.loading.set(false);
      return;
    }
    this.authApi.getMedicalRecord(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (record) => this.record.set(record),
        error: (response) => {
          if (response.status === 401) {
            sessionStorage.removeItem('clinicOneAccessToken');
            sessionStorage.removeItem('clinicOnePatientName');
            sessionStorage.removeItem('clinicOneSessionType');
            void this.router.navigateByUrl('/login');
            return;
          }
          this.error.set(apiErrorMessage(response));
        },
      });
  }

  protected formatDate(value: string | null | undefined): string {
    return formatClinicDate(value) || 'Không có';
  }

  protected formatDateTime(value: string | null | undefined): string {
    return formatClinicMediumDateTime(value) || 'Không có';
  }

  protected readonly printMode = signal<'full' | 'prescription' | 'invoice'>('full');

  protected printFullRecord(): void {
    this.printMode.set('full');
    setTimeout(() => window.print(), 50);
  }

  protected printPrescription(): void {
    this.printMode.set('prescription');
    setTimeout(() => window.print(), 50);
  }

  protected printInvoice(): void {
    this.printMode.set('invoice');
    setTimeout(() => window.print(), 50);
  }

  protected openInvoiceModal(): void {
    this.viewInvoiceModal.set(true);
  }

  protected closeInvoiceModal(): void {
    this.viewInvoiceModal.set(false);
  }

  protected printRecord(): void {
    this.printFullRecord();
  }
}
