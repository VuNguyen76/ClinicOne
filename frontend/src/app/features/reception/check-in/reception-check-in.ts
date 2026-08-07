import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AuthApiService, ReceptionAppointmentResponse, apiErrorMessage } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';

@Component({
  selector: 'app-reception-check-in',
  standalone: true,
  imports: [FormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './reception-check-in.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceptionCheckIn implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly query = signal('');
  protected readonly selectedDate = signal(this.toIsoDate(new Date()));
  protected readonly exceptionReason = signal('');
  protected readonly appointments = signal<ReceptionAppointmentResponse[]>([]);
  protected readonly loading = signal(false);
  protected readonly searched = signal(false);
  protected readonly busyId = signal('');
  protected readonly error = signal('');
  protected readonly notice = signal('');

  ngOnInit(): void {
    this.query.set('');
  }

  protected search(): void {
    const value = this.query().trim();
    if (value.length < 3) {
      this.error.set('Nhập ít nhất 3 ký tự của mã lịch hẹn hoặc số điện thoại.');
      return;
    }
    this.loading.set(true);
    this.searched.set(true);
    this.error.set('');
    this.notice.set('');
    this.authApi.searchReceptionAppointments(value, this.selectedDate()).subscribe({
      next: (appointments) => {
        this.appointments.set(appointments);
        this.loading.set(false);
        if (appointments.length === 0) this.notice.set('Không tìm thấy lịch hẹn đang chờ trong ngày đã chọn.');
      },
      error: (response) => {
        this.loading.set(false);
        this.handleError(response);
      },
    });
  }

  protected checkIn(appointment: ReceptionAppointmentResponse): void {
    if (!appointment.roomCode || appointment.queueStatus) return;
    const reason = this.exceptionReason().trim();
    if (reason.length < 3) {
      this.error.set('Nhập lý do hỗ trợ tại quầy trước khi cấp số.');
      return;
    }
    this.busyId.set(appointment.id);
    this.error.set('');
    this.notice.set('');
    this.authApi.receptionCheckIn(appointment.id, appointment.roomCode, reason).subscribe({
      next: (updated) => {
        this.appointments.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.busyId.set('');
        this.notice.set(`Đã cấp số ${String(updated.queueNumber).padStart(2, '0')} cho ${updated.patientName}.`);
      },
      error: (response) => {
        this.busyId.set('');
        this.handleError(response);
      },
    });
  }

  protected formatTime(value: string): string {
    return value?.slice(0, 5) ?? '';
  }

  protected queueLabel(appointment: ReceptionAppointmentResponse): string {
    return appointment.queueNumber == null ? 'Chưa lấy số' : `Số ${String(appointment.queueNumber).padStart(2, '0')} · ${appointment.queueStatusLabel}`;
  }

  protected queueClass(appointment: ReceptionAppointmentResponse): string {
    return appointment.queueStatus ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700';
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOneStaffRole');
      void this.router.navigateByUrl('/staff/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }

  private toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
