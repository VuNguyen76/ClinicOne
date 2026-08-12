import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AppointmentResponse, AuthApiService, ClinicRoomCheckInResponse, QueueTicketResponse, apiErrorMessage } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';
import { clinicTodayIso } from '../../../core/time/clinic-time';

@Component({
  selector: 'app-queue-check-in',
  standalone: true,
  imports: [RouterLink, MatIconModule, AccountMenu, DecimalPipe],
  templateUrl: './queue-check-in.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueueCheckIn implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authApi = inject(AuthApiService);

  protected readonly roomCode = signal('');
  protected readonly room = signal<ClinicRoomCheckInResponse | null>(null);
  protected readonly appointments = signal<AppointmentResponse[]>([]);
  protected readonly selectedAppointment = signal<AppointmentResponse | null>(null);
  protected readonly ticket = signal<QueueTicketResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly today = clinicTodayIso();
  private checkInRequestKey: string | null = null;

  ngOnInit(): void {
    this.roomCode.set(this.route.snapshot.paramMap.get('roomCode') ?? '');
    this.loadRoom();
  }

  protected todayAppointments(): AppointmentResponse[] {
    const specialty = this.room()?.specialty.toLowerCase();
    return this.appointments().filter((appointment) => appointment.appointmentDate === this.today
      && ['BOOKED', 'CHECKED_IN', 'COMPLETED', 'NOT_PERFORMED'].includes(appointment.status)
      && (!specialty || appointment.specialty.toLowerCase() === specialty));
  }

  protected chooseAppointment(appointment: AppointmentResponse): void {
    this.error.set('');
    this.selectedAppointment.set(appointment);
    this.checkInRequestKey = null;
  }

  protected submitLabel(): string {
    const status = this.selectedAppointment()?.status;
    if (status === 'COMPLETED' || status === 'NOT_PERFORMED') {
      return 'Xem kết quả đã có';
    }
    return status === 'CHECKED_IN' ? 'Báo đã quay lại' : 'Nhận số thứ tự';
  }

  protected checkIn(): void {
    const appointment = this.selectedAppointment();
    if (!appointment) {
      this.error.set('Vui lòng chọn lịch hẹn để lấy số.');
      return;
    }
    this.error.set('');
    this.busy.set(true);
    this.checkInRequestKey ??= `checkin-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    this.authApi.checkInToRoom(this.roomCode(), appointment.id, this.checkInRequestKey).subscribe({
      next: (ticket) => {
        this.ticket.set(ticket);
        this.busy.set(false);
      },
      error: (response) => {
        this.busy.set(false);
        this.handleError(response);
      },
    });
  }

  protected formatTime(value: string): string {
    return value.slice(0, 5);
  }

  protected formatDate(value: string): string {
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
      .format(new Date(year, month - 1, day));
  }

  protected retry(): void {
    this.error.set('');
    this.loadRoom();
  }

  private loadAppointments(): void {
    this.loading.set(true);
    this.authApi.getAppointments().subscribe({
      next: (appointments) => {
        this.appointments.set(appointments);
        this.loading.set(false);
      },
      error: (response) => {
        this.loading.set(false);
        this.handleError(response);
      },
    });
  }

  private loadRoom(): void {
    this.authApi.getRoomForCheckIn(this.roomCode()).subscribe({
      next: (room) => {
        this.room.set(room);
        this.loadAppointments();
      },
      error: (response) => {
        this.loading.set(false);
        this.handleError(response);
      },
    });
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOnePatientName');
      void this.router.navigateByUrl('/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }

  private toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
