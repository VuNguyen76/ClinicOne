import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import { apiErrorMessage, AppointmentResponse, AuthApiService, PatientProfileResponse, QueueTicketResponse } from '../../core/auth/auth-api.service';
import { PatientHeader } from '../../shared/patient-header/patient-header';
import { clinicTodayIso } from '../../core/time/clinic-time';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, MatIconModule, PatientHeader],
  templateUrl: './dashboard.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly profile = signal<PatientProfileResponse | null>(null);
  protected readonly appointments = signal<AppointmentResponse[]>([]);
  protected readonly queueTickets = signal<QueueTicketResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly appointmentsLoading = signal(true);
  protected readonly error = signal('');

  ngOnInit(): void {
    this.authApi.getProfile()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (profile) => this.profile.set(profile),
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

    this.authApi.getAppointments()
      .pipe(finalize(() => this.appointmentsLoading.set(false)))
      .subscribe({
        next: (appointments) => this.appointments.set(appointments),
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

    this.authApi.getMyQueue(clinicTodayIso())
      .subscribe({
        next: (tickets) => this.queueTickets.set(tickets),
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
    if (!value) {
      return 'Chưa cập nhật';
    }
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN').format(new Date(year, month - 1, day));
  }

  protected formatTime(value: string): string {
    return value?.slice(0, 5) ?? '';
  }

  protected formatQueueNumber(value: number): string {
    return String(value).padStart(2, '0');
  }

  protected queueStatusClass(status: string): string {
    switch (status) {
      case 'CANCELLED': return 'erp-badge-danger';
      case 'ABSENT':
      case 'NOT_PERFORMED':
      case 'SKIPPED':
      case 'LEFT_BEFORE_EXAM': return 'erp-badge-warning';
      case 'COMPLETED': return 'erp-badge-success';
      case 'CALLED':
      case 'IN_SERVICE':
      case 'CHECKED_IN':
      case 'WAITING':
      case 'BOOKED': return 'erp-badge-info';
      default: return 'erp-badge-neutral';
    }
  }

}
