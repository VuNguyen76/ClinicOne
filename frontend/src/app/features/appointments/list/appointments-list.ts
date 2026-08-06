import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import { apiErrorMessage, AppointmentResponse, AuthApiService } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';
import { AccountNav } from '../../../shared/account-nav/account-nav';

type AppointmentFilter = 'ALL' | 'BOOKED' | 'COMPLETED' | 'CANCELLED';

@Component({
  selector: 'app-appointments-list',
  standalone: true,
  imports: [RouterLink, MatIconModule, AccountMenu, AccountNav],
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
          if (response.status === 401 || response.status === 403) {
            sessionStorage.removeItem('clinicOneAccessToken');
            sessionStorage.removeItem('clinicOnePatientName');
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
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
      .format(new Date(year, month - 1, day));
  }

  protected formatTime(value: string): string {
    return value.slice(0, 5);
  }

  protected statusClass(status: string): string {
    if (status === 'CANCELLED') {
      return 'bg-slate-100 text-slate-600';
    }
    if (status === 'COMPLETED') {
      return 'bg-emerald-50 text-emerald-700';
    }
    return 'bg-sky-50 text-sky-700';
  }
}
