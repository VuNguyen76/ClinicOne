import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import { apiErrorMessage, AuthApiService, MedicalRecordResponse, PatientProfileItem } from '../../core/auth/auth-api.service';
import { PatientHeader } from '../../shared/patient-header/patient-header';
import { AccountNav } from '../../shared/account-nav/account-nav';

@Component({
  selector: 'app-medical-records',
  standalone: true,
  imports: [FormsModule, RouterLink, MatIconModule, PatientHeader, AccountNav],
  templateUrl: './medical-records.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MedicalRecords implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly records = signal<MedicalRecordResponse[]>([]);
  protected readonly profiles = signal<PatientProfileItem[]>([]);
  protected readonly selectedProfileId = signal('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  ngOnInit(): void {
    this.loadHistory();
    this.authApi.getPatientProfiles().subscribe({ next: (profiles) => this.profiles.set(profiles) });
  }

  protected applyFilters(): void { this.loadHistory(0); }

  protected clearFilters(): void {
    this.selectedProfileId.set('');
    this.from.set('');
    this.to.set('');
    this.loadHistory(0);
  }

  protected previous(): void { if (this.page() > 0) this.loadHistory(this.page() - 1); }

  protected next(): void { if (this.page() + 1 < this.totalPages()) this.loadHistory(this.page() + 1); }

  private loadHistory(page = this.page()): void {
    this.loading.set(true);
    this.error.set('');
    this.authApi.getMedicalRecords({
      page,
      size: 20,
      profileId: this.selectedProfileId() || null,
      from: this.from() || null,
      to: this.to() || null,
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (result) => {
          this.records.set(result.items);
          this.page.set(result.page);
          this.totalPages.set(result.totalPages);
          this.totalElements.set(result.totalElements);
        },
        error: (response) => {
          if (response.status === 401 || response.status === 403) {
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
    if (!value) return 'Chưa cập nhật';
    const [year, month, day] = value.slice(0, 10).split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN').format(new Date(year, month - 1, day));
  }

  protected formatDateTime(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
  }
}
