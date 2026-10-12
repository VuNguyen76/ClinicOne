import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import { apiErrorMessage, AppointmentResponse, AuthApiService } from '../../../core/auth/auth-api.service';
import { PatientHeader } from '../../../shared/patient-header/patient-header';
import { AccountNav } from '../../../shared/account-nav/account-nav';
import { formatClinicDate, formatClinicTime } from '../../../core/time/clinic-time';

type AppointmentFilter = 'ALL' | 'BOOKED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED' | 'ABSENT' | 'NOT_PERFORMED';

@Component({
  selector: 'app-appointments-list',
  standalone: true,
  imports: [RouterLink, MatIconModule, PatientHeader, AccountNav],
  templateUrl: './appointments-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppointmentsList implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly appointments = signal<AppointmentResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly filter = signal<AppointmentFilter>('ALL');

  protected readonly visibleAppointments = () => {
    const selected = this.filter();
    const appointments = this.appointments();
    if (selected === 'ALL') {
      return appointments;
    }
    return appointments.filter((appointment) => appointment.status === selected);
  };

  ngOnInit(): void {
    this.authApi.getAppointments()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (appointments) => this.appointments.set(appointments),
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

  protected setFilter(filter: AppointmentFilter): void {
    this.filter.set(filter);
  }

  protected formatDate(value: string): string {
    return formatClinicDate(value);
  }

  protected formatTime(value: string): string {
    return formatClinicTime(value);
  }

  protected statusClass(status: string): string {
    if (status === 'CANCELLED') {
      return 'erp-badge-danger';
    }
    if (status === 'COMPLETED') {
      return 'erp-badge-success';
    }
    if (status === 'CHECKED_IN') {
      return 'erp-badge-info';
    }
    if (status === 'ABSENT' || status === 'NOT_PERFORMED') {
      return 'erp-badge-warning';
    }
    return 'erp-badge-info';
  }

  protected statusBadgeClass(status: string): string {
    if (status === 'CANCELLED') {
      return 'border-rose-200 bg-rose-50 text-rose-700';
    }
    if (status === 'ABSENT' || status === 'NOT_PERFORMED') {
      return 'border-amber-200 bg-amber-50 text-amber-700';
    }
    if (status === 'COMPLETED') {
      return 'border-emerald-200 bg-emerald-50 text-emerald-700';
    }
    if (status === 'CHECKED_IN') {
      return 'border-sky-200 bg-sky-50 text-[#0284c7]';
    }
    return 'border-sky-200 bg-sky-50 text-[#0284c7]';
  }

  protected statusDotClass(status: string): string {
    if (status === 'CANCELLED') {
      return 'bg-rose-500';
    }
    if (status === 'ABSENT' || status === 'NOT_PERFORMED') {
      return 'bg-amber-500';
    }
    if (status === 'COMPLETED') {
      return 'bg-emerald-500';
    }
    if (status === 'CHECKED_IN') {
      return 'bg-[#0284c7]';
    }
    return 'bg-[#0284c7]';
  }
}

