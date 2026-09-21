import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import { apiErrorMessage, AuthApiService, MedicalRecordResponse } from '../../core/auth/auth-api.service';
import { PatientHeader } from '../../shared/patient-header/patient-header';
import { formatClinicDate, formatClinicMediumDateTime } from '../../core/time/clinic-time';

@Component({
  selector: 'app-medical-record-detail',
  standalone: true,
  imports: [RouterLink, MatIconModule, PatientHeader],
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
    return formatClinicDate(value) || 'Không có';
  }

  protected formatDateTime(value: string | null | undefined): string {
    return formatClinicMediumDateTime(value) || 'Không có';
  }

  protected printRecord(): void {
    window.print();
  }
}
