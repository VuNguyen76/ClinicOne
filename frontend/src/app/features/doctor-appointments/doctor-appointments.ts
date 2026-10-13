import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import { AppointmentResponse, AuthApiService, apiErrorMessage } from '../../core/auth/auth-api.service';
import { clinicTodayIso, formatClinicPadDate, formatClinicTime } from '../../core/time/clinic-time';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-doctor-appointments',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './doctor-appointments.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DoctorAppointments implements OnInit {
  private readonly api = inject(AuthApiService);
  private requestVersion = 0;
  private readonly today = clinicTodayIso();

  protected readonly from = signal(this.today);
  protected readonly to = signal(addDays(this.today, 30));
  protected readonly appointments = signal<AppointmentResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  ngOnInit(): void {
    this.load();
  }

  protected searchAppointments(): void {
    if (!this.from() || !this.to() || this.from() > this.to()) {
      this.error.set('Ngày bắt đầu không thể sau ngày kết thúc.');
      return;
    }
    const rangeDays = Math.floor((Date.parse(`${this.to()}T00:00:00Z`) - Date.parse(`${this.from()}T00:00:00Z`)) / 86_400_000);
    if (!Number.isFinite(rangeDays) || rangeDays > 90) {
      this.error.set('Vui lòng chọn khoảng ngày tối đa 90 ngày.');
      return;
    }
    this.load();
  }

  protected resetRange(): void {
    this.from.set(this.today);
    this.to.set(addDays(this.today, 30));
    this.load();
  }

  protected formatDate(value: string): string {
    return formatClinicPadDate(value);
  }

  protected formatTime(value: string): string {
    return formatClinicTime(value);
  }

  protected statusClass(status: string): string {
    switch (status) {
      case 'BOOKED': return 'border-sky-200 bg-sky-50 text-sky-700';
      case 'CHECKED_IN': return 'border-amber-200 bg-amber-50 text-amber-800';
      case 'COMPLETED': return 'border-emerald-200 bg-emerald-50 text-emerald-700';
      case 'CANCELLED': return 'border-slate-200 bg-slate-100 text-slate-600';
      case 'ABSENT': return 'border-rose-200 bg-rose-50 text-rose-700';
      default: return 'border-slate-200 bg-slate-50 text-slate-600';
    }
  }

  private load(): void {
    const request = ++this.requestVersion;
    this.loading.set(true);
    this.error.set('');
    this.api.getDoctorAppointments(this.from(), this.to()).pipe(finalize(() => {
      if (request === this.requestVersion) this.loading.set(false);
    })).subscribe({
      next: (items) => {
        if (request === this.requestVersion) this.appointments.set(items ?? []);
      },
      error: (response) => {
        if (request === this.requestVersion) this.error.set(apiErrorMessage(response));
      },
    });
  }
}

function addDays(value: string, days: number): string {
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day + days));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`;
}
