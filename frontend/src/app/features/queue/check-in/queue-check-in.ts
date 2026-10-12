import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { distinctUntilChanged, map } from 'rxjs';
import { ApiErrorResponse, AppointmentResponse, AuthApiService, ClinicRoomCheckInResponse, QueueTicketResponse, apiErrorMessage } from '../../../core/auth/auth-api.service';
import { PatientHeader } from '../../../shared/patient-header/patient-header';
import { clinicTodayIso, formatClinicDate, formatClinicTime } from '../../../core/time/clinic-time';

interface CheckInProfileOption {
  key: string;
  name: string;
  appointments: AppointmentResponse[];
}

@Component({
  selector: 'app-queue-check-in',
  standalone: true,
  imports: [RouterLink, MatIconModule, PatientHeader, DecimalPipe],
  templateUrl: './queue-check-in.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueueCheckIn implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authApi = inject(AuthApiService);
  private readonly destroyRef = inject(DestroyRef);
  private roomRequestId = 0;
  private appointmentsRequestId = 0;
  private checkInRequestId = 0;

  protected readonly roomCode = signal('');
  protected readonly room = signal<ClinicRoomCheckInResponse | null>(null);
  protected readonly appointments = signal<AppointmentResponse[]>([]);
  protected readonly selectedProfileKey = signal('');
  protected readonly ticket = signal<QueueTicketResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly lateNotice = signal('');
  protected readonly today = clinicTodayIso();
  private checkInRequestKey: string | null = null;

  ngOnInit(): void {
    this.route.paramMap.pipe(
      map((params) => params.get('roomCode') ?? ''),
      distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((roomCode) => this.openRoom(roomCode));
  }

  protected todayAppointments(): AppointmentResponse[] {
    return this.appointments();
  }

  protected profileOptions(): CheckInProfileOption[] {
    const groups = new Map<string, CheckInProfileOption>();
    for (const appointment of this.todayAppointments()) {
      const key = appointment.profileId || 'profile:unlinked';
      const group = groups.get(key) ?? {
        key,
        name: appointment.profileName || 'Hồ sơ người đi khám',
        appointments: [],
      };
      group.appointments.push(appointment);
      groups.set(key, group);
    }
    return [...groups.values()];
  }

  protected needsProfileSelection(): boolean {
    return this.profileOptions().length > 1 && !this.selectedProfileKey();
  }

  protected visibleProfileAppointments(): AppointmentResponse[] {
    const groups = this.profileOptions();
    const selected = groups.find((group) => group.key === this.selectedProfileKey());
    if (selected) return selected.appointments;
    return groups.length === 1 ? groups[0].appointments : [];
  }

  protected selectedProfileName(): string {
    const profiles = this.profileOptions();
    return profiles.find((profile) => profile.key === this.selectedProfileKey())?.name
      || (profiles.length === 1 ? profiles[0].name : 'Lịch hẹn hôm nay');
  }

  protected chooseProfile(profile: CheckInProfileOption): void {
    this.selectedProfileKey.set(profile.key);
    this.checkInRequestKey = null;
    this.error.set('');
    this.lateNotice.set('');
    if (profile.appointments.length === 1) this.beginCheckIn(profile.appointments[0]);
  }

  protected resetProfileSelection(): void {
    this.selectedProfileKey.set('');
    this.checkInRequestKey = null;
    this.error.set('');
    this.lateNotice.set('');
  }

  protected chooseAppointment(appointment: AppointmentResponse): void {
    this.error.set('');
    this.checkInRequestKey = null;
    this.beginCheckIn(appointment);
  }

  protected isLate(appointment: AppointmentResponse): boolean {
    if (appointment.status !== 'BOOKED' || appointment.appointmentDate !== this.today) return false;
    const duration = appointment.serviceDurationMinutes || 60;
    const scheduledStart = Date.parse(`${appointment.appointmentDate}T${appointment.startTime.slice(0, 5)}:00+07:00`);
    return Date.now() >= scheduledStart + (duration + 15) * 60_000;
  }

  private beginCheckIn(appointment: AppointmentResponse): void {
    if (this.busy() || this.ticket()) return;
    if (this.isLate(appointment)) {
      this.lateNotice.set('Lịch đã quá giờ check-in. Vui lòng đến quầy để được hỗ trợ.');
      return;
    }
    this.lateNotice.set('');
    this.error.set('');
    this.busy.set(true);
    const requestId = ++this.checkInRequestId;
    this.checkInRequestKey ??= `checkin-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    const clinicRoomCode = this.room()?.code;
    if (!clinicRoomCode) {
      this.busy.set(false);
      this.error.set('Không xác định được phòng khám từ mã QR.');
      return;
    }
    this.authApi.checkInToRoom(clinicRoomCode, appointment.id, this.checkInRequestKey).subscribe({
      next: (ticket) => {
        if (requestId !== this.checkInRequestId) return;
        this.ticket.set(ticket);
        this.busy.set(false);
      },
      error: (response) => {
        if (requestId !== this.checkInRequestId) return;
        this.busy.set(false);
        this.handleError(response);
      },
    });
  }

  protected formatTime(value: string): string {
    return formatClinicTime(value);
  }

  protected formatDate(value: string): string {
    return formatClinicDate(value);
  }

  protected statusClass(status: string): string {
    if (status === 'COMPLETED') return 'erp-badge-success';
    if (status === 'SKIPPED' || status === 'LEFT_BEFORE_EXAM' || status === 'CANCELLED') return 'erp-badge-warning';
    return 'erp-badge-info';
  }

  protected retry(): void {
    this.error.set('');
    this.appointmentsRequestId++;
    this.loadRoom(++this.roomRequestId);
  }

  private loadAppointments(roomRequestId: number, roomCode: string): void {
    const requestId = ++this.appointmentsRequestId;
    this.loading.set(true);
    if (!roomCode) {
      this.loading.set(false);
      this.error.set('Không xác định được phòng khám từ mã QR.');
      return;
    }
    this.authApi.getRoomCheckInAppointments(roomCode).subscribe({
      next: (appointments) => {
        if (roomRequestId !== this.roomRequestId || requestId !== this.appointmentsRequestId) return;
        this.appointments.set(appointments);
        const profiles = this.profileOptions();
        if (profiles.length === 1) {
          this.selectedProfileKey.set(profiles[0].key);
          if (profiles[0].appointments.length === 1) this.beginCheckIn(profiles[0].appointments[0]);
        }
        this.loading.set(false);
      },
      error: (response) => {
        if (roomRequestId !== this.roomRequestId || requestId !== this.appointmentsRequestId) return;
        this.loading.set(false);
        this.handleError(response);
      },
    });
  }

  private openRoom(roomKey: string): void {
    this.roomCode.set(roomKey);
    this.roomRequestId++;
    this.appointmentsRequestId++;
    this.checkInRequestId++;
    this.room.set(null);
    this.appointments.set([]);
    this.selectedProfileKey.set('');
    this.ticket.set(null);
    this.busy.set(false);
    this.error.set('');
    this.lateNotice.set('');
    this.checkInRequestKey = null;
    this.loading.set(true);
    this.loadRoom(this.roomRequestId);
  }

  private loadRoom(requestId = ++this.roomRequestId): void {
    const roomKey = this.roomCode();
    this.loading.set(true);
    this.authApi.getRoomForCheckIn(roomKey).subscribe({
      next: (room) => {
        if (requestId !== this.roomRequestId || roomKey !== this.roomCode()) return;
        this.room.set(room);
        this.loadAppointments(requestId, room.code);
      },
      error: (response) => {
        if (requestId !== this.roomRequestId || roomKey !== this.roomCode()) return;
        this.loading.set(false);
        this.handleError(response);
      },
    });
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOnePatientName');
      sessionStorage.removeItem('clinicOneSessionType');
      void this.router.navigateByUrl('/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }

}
