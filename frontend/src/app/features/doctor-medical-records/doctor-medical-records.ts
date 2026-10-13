import { ChangeDetectionStrategy, Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import {
  apiErrorMessage,
  AuthApiService,
  DoctorMedicalRecordResponse,
} from '../../core/auth/auth-api.service';
import { formatClinicDate, formatClinicDateTime } from '../../core/time/clinic-time';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-doctor-medical-records',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './doctor-medical-records.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DoctorMedicalRecords implements OnInit {
  private readonly api = inject(AuthApiService);
  @ViewChild('prescriptionDialog') private prescriptionDialog?: ElementRef<HTMLDialogElement>;
  private historyRequest = 0;
  private detailRequest = 0;

  protected readonly records = signal<DoctorMedicalRecordResponse[]>([]);
  protected readonly selectedRecord = signal<DoctorMedicalRecordResponse | null>(null);
  protected readonly search = signal('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(true);
  protected readonly loadingDetail = signal(false);
  protected readonly error = signal('');

  protected openPrescription(): void {
    const dialog = this.prescriptionDialog?.nativeElement;
    if (!dialog || dialog.open) return;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
  }

  protected closePrescription(): void {
    const dialog = this.prescriptionDialog?.nativeElement;
    if (!dialog?.open) return;
    if (typeof dialog.close === 'function') dialog.close();
    else dialog.removeAttribute('open');
  }

  protected closePrescriptionOnBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.closePrescription();
  }

  ngOnInit(): void {
    this.loadHistory();
  }

  protected searchRecords(): void {
    if (this.from() && this.to() && this.from() > this.to()) {
      this.error.set('Ngày bắt đầu không thể sau ngày kết thúc.');
      return;
    }
    this.loadHistory(0);
  }

  protected clearFilters(): void {
    this.search.set('');
    this.from.set('');
    this.to.set('');
    this.loadHistory(0);
  }

  protected openRecord(record: DoctorMedicalRecordResponse): void {
    if (this.selectedRecord()?.id === record.id) return;
    const request = ++this.detailRequest;
    this.selectedRecord.set(null);
    this.loadingDetail.set(true);
    this.error.set('');
    this.api.getDoctorMedicalRecord(record.id)
      .pipe(finalize(() => {
        if (request === this.detailRequest) this.loadingDetail.set(false);
      }))
      .subscribe({
        next: (detail) => {
          if (request === this.detailRequest) this.selectedRecord.set(detail);
        },
        error: (response) => {
          if (request === this.detailRequest) this.error.set(apiErrorMessage(response));
        },
      });
  }

  protected previous(): void {
    if (this.page() > 0 && !this.loading()) this.loadHistory(this.page() - 1);
  }

  protected next(): void {
    if (this.page() + 1 < this.totalPages() && !this.loading()) this.loadHistory(this.page() + 1);
  }

  protected formatDate(value: string | null): string {
    return formatClinicDate(value) || '—';
  }

  protected formatDateTime(value: string | null): string {
    return value ? formatClinicDateTime(value) : '—';
  }

  protected vital(value: string | number | null, suffix = ''): string {
    return value === null || value === '' ? '—' : `${value}${suffix}`;
  }

  private loadHistory(page = this.page()): void {
    const request = ++this.historyRequest;
    this.loading.set(true);
    this.error.set('');
    this.api.getDoctorMedicalRecords({
      search: this.search(), from: this.from() || null, to: this.to() || null, page, size: 20,
    }).pipe(finalize(() => {
      if (request === this.historyRequest) this.loading.set(false);
    })).subscribe({
      next: (result) => {
        if (request !== this.historyRequest) return;
        this.records.set(result.items);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        const selected = this.selectedRecord();
        if (selected && !result.items.some((item) => item.id === selected.id)) {
          this.selectedRecord.set(null);
        }
      },
      error: (response) => {
        if (request === this.historyRequest) this.error.set(apiErrorMessage(response));
      },
    });
  }
}
