import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import {
  apiErrorMessage,
  ApiErrorResponse,
  AppointmentResponse,
  AppointmentSlotResponse,
  AuthApiService,
  AvailableReplacementSlot,
  ReasonCatalogResponse,
  RescheduleCaseResponse,
} from '../../../core/auth/auth-api.service';
import { PatientHeader } from '../../../shared/patient-header/patient-header';
import { clinicTodayDate, clinicTodayIso } from '../../../core/time/clinic-time';

@Component({
  selector: 'app-appointment-detail',
  standalone: true,
  imports: [RouterLink, MatIconModule, PatientHeader],
  templateUrl: './appointment-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppointmentDetail implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly appointment = signal<AppointmentResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly cancelOpen = signal(false);
  protected readonly rescheduleOpen = signal(false);
  protected readonly notice = signal('');
  protected readonly error = signal('');
  protected readonly cancellationReasons = signal<ReasonCatalogResponse[]>([]);
  protected readonly selectedCancellationReason = signal('');
  protected readonly rescheduleCase = signal<RescheduleCaseResponse | null>(null);
  protected readonly replacementSlots = signal<AvailableReplacementSlot[]>([]);
  protected readonly replacementLoading = signal(false);
  protected readonly replacementSearchFailed = signal(false);
  protected readonly rescheduleCaseLoading = signal(false);
  protected readonly selectedReplacement = signal<AvailableReplacementSlot | null>(null);
  protected readonly availableSlots = signal<AppointmentSlotResponse[]>([]);
  protected readonly availableSlotsLoading = signal(false);
  protected readonly availableSlotsFailed = signal(false);
  protected readonly selectedSlot = signal<AppointmentSlotResponse | null>(null);
  private cancellationRequestKey: string | null = null;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      void this.router.navigateByUrl('/dashboard');
      return;
    }
    this.authApi.getAppointment(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (appointment) => {
          this.appointment.set(appointment);
          this.loadPatientReschedulingCase(appointment.id);
        },
        error: (response) => this.handleError(response),
      });
    this.authApi.getCancellationReasons().subscribe({
      next: (reasons) => this.cancellationReasons.set(reasons),
    });
  }

  protected formatDate(value: string): string {
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN').format(new Date(year, month - 1, day));
  }

  protected formatWeekday(value: string): string {
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN', { weekday: 'long' }).format(new Date(year, month - 1, day));
  }

  protected formatTime(value: string): string {
    return value.slice(0, 5);
  }

  protected isLate(): boolean {
    const app = this.appointment();
    if (!app || app.status !== 'BOOKED') return false;
    const duration = app.serviceDurationMinutes || 60;
    const [year, month, day] = app.appointmentDate.split('-').map(Number);
    const [hour, minute] = app.startTime.split(':').map(Number);
    const appointmentTime = new Date(year, month - 1, day, hour, minute);
    const lateThresholdTime = new Date(appointmentTime.getTime() + (duration + 15) * 60 * 1000);
    return Date.now() >= lateThresholdTime.getTime();
  }

  protected canEdit(): boolean {
    return this.appointment()?.status === 'BOOKED' && !this.isLate();
  }

  protected hasOpenReschedulingCase(): boolean {
    return this.rescheduleCase()?.status === 'OPEN';
  }

  protected openReschedule(): void {
    if (!this.canEdit() || this.hasOpenReschedulingCase()) return;
    this.rescheduleOpen.set(true);
    this.cancelOpen.set(false);
    this.error.set('');
    if (this.availableSlots().length === 0) this.loadAvailableSlots();
  }

  protected closeReschedule(): void {
    if (this.busy()) return;
    this.rescheduleOpen.set(false);
    this.selectedSlot.set(null);
  }

  protected openCancel(): void {
    if (!this.canEdit()) return;
    this.cancelOpen.set(true);
    this.rescheduleOpen.set(false);
    this.error.set('');
  }

  protected closeCancel(): void {
    if (this.busy()) return;
    this.cancelOpen.set(false);
  }

  protected cancel(): void {
    const appointment = this.appointment();
    if (!appointment || !this.canEdit()) return;
    this.busy.set(true);
    this.cancellationRequestKey ??= crypto.randomUUID();
    this.authApi.cancelAppointment(appointment.id, this.selectedCancellationReason() || undefined,
      this.cancellationRequestKey)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/dashboard'),
        error: (response) => this.handleError(response),
      });
  }

  protected chooseSlot(slot: AppointmentSlotResponse): void {
    this.selectedSlot.set(slot);
    this.error.set('');
  }

  protected reschedule(): void {
    const appointment = this.appointment();
    const slot = this.selectedSlot();
    if (!appointment || !slot || !this.canEdit() || this.hasOpenReschedulingCase()) {
      if (!slot) this.error.set('Vui lòng chọn một khung giờ còn trống.');
      return;
    }
    this.busy.set(true);
    this.authApi.rescheduleAppointment(appointment.id, slot.appointmentDate, slot.startTime.slice(0, 5))
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (updated) => {
          this.appointment.set(updated);
          this.rescheduleOpen.set(false);
          this.selectedSlot.set(null);
          this.availableSlots.set([]);
          this.notice.set('Đã đổi lịch hẹn sang khung giờ mới thành công.');
          setTimeout(() => this.notice.set(''), 4000);
        },
        error: (response) => this.handleError(response),
      });
  }

  protected chooseReplacement(slot: AvailableReplacementSlot): void {
    this.selectedReplacement.set(slot);
    this.error.set('');
  }

  protected confirmPatientReschedule(): void {
    const appointment = this.appointment();
    const selected = this.selectedReplacement();
    if (!appointment || !this.canEdit() || !this.hasOpenReschedulingCase() || !selected) {
      if (!selected) this.error.set('Hãy chọn một khung giờ thay thế.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.authApi.confirmPatientRescheduling(appointment.id, selected.appointmentDate,
      selected.startTime.slice(0, 5), selected.doctorName, selected.doctorId)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => {
          this.appointment.set({
            ...appointment,
            appointmentDate: selected.appointmentDate,
            startTime: selected.startTime,
            doctorName: selected.doctorName,
            doctorId: selected.doctorId,
          });
          this.rescheduleCase.set(null);
          this.replacementSlots.set([]);
          this.selectedReplacement.set(null);
          this.notice.set('Đã xác nhận khung giờ thay thế thành công.');
          setTimeout(() => this.notice.set(''), 4000);
        },
        error: (response) => this.handleError(response),
      });
  }

  protected retryAvailableSlots(): void {
    this.loadAvailableSlots();
  }

  protected retryReplacementSearch(): void {
    const appointment = this.appointment();
    if (appointment && this.hasOpenReschedulingCase()) this.loadReplacementSlots(appointment.id);
  }

  private loadAvailableSlots(): void {
    const appointment = this.appointment();
    if (!appointment) return;
    const from = clinicTodayIso();
    const end = clinicTodayDate();
    end.setDate(end.getDate() + 30);
    const to = this.toIsoDate(end);
    this.availableSlotsLoading.set(true);
    this.availableSlotsFailed.set(false);
    this.authApi.getAppointmentSlots(appointment.specialty, from, to, appointment.serviceId ?? undefined)
      .pipe(finalize(() => this.availableSlotsLoading.set(false)))
      .subscribe({
        next: (slots) => {
          this.availableSlots.set(slots
            .filter((slot) => this.belongsToCurrentDoctor(slot, appointment))
            .filter((slot) => slot.remainingCapacity > 0)
            .filter((slot) => slot.appointmentDate !== appointment.appointmentDate
              || slot.startTime.slice(0, 5) !== appointment.startTime.slice(0, 5))
            .sort((left, right) => `${left.appointmentDate}${left.startTime}`.localeCompare(`${right.appointmentDate}${right.startTime}`))
            .slice(0, 24));
        },
        error: (response) => {
          this.availableSlotsFailed.set(true);
          this.handleError(response);
        },
      });
  }

  private belongsToCurrentDoctor(slot: AppointmentSlotResponse, appointment: AppointmentResponse): boolean {
    return appointment.doctorId ? slot.doctorId === appointment.doctorId : slot.doctorName === appointment.doctorName;
  }

  private loadPatientReschedulingCase(appointmentId: string): void {
    this.rescheduleCaseLoading.set(true);
    this.authApi.getPatientReschedulingCase(appointmentId).subscribe({
      next: (item) => {
        this.rescheduleCase.set(item);
        this.rescheduleCaseLoading.set(false);
        if (item.status === 'OPEN') this.loadReplacementSlots(appointmentId);
      },
      error: (response) => {
        this.rescheduleCaseLoading.set(false);
        if (response.status === 404) {
          this.rescheduleCase.set(null);
          return;
        }
        this.handleError(response);
      },
    });
  }

  private loadReplacementSlots(appointmentId: string): void {
    this.replacementLoading.set(true);
    this.replacementSearchFailed.set(false);
    this.authApi.getPatientReplacementSlots(appointmentId).subscribe({
      next: (slots) => {
        this.replacementSlots.set(slots);
        this.replacementLoading.set(false);
      },
      error: (response) => {
        this.replacementLoading.set(false);
        this.replacementSearchFailed.set(true);
        this.handleError(response);
      },
    });
  }

  private handleError(response: ApiErrorResponse & { status?: number }): void {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOnePatientName');
      sessionStorage.removeItem('clinicOneSessionType');
      void this.router.navigateByUrl('/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
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

  private toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
