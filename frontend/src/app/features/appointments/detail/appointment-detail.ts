import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import {
  apiErrorMessage,
  ApiErrorResponse,
  AppointmentResponse,
  AuthApiService,
  AvailableReplacementSlot,
  ReasonCatalogResponse,
  RescheduleCaseResponse,
} from '../../../core/auth/auth-api.service';
import { PatientHeader } from '../../../shared/patient-header/patient-header';
import { clinicTodayIso, formatClinicDate, formatClinicTime } from '../../../core/time/clinic-time';

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
    return formatClinicDate(value);
  }

  protected formatWeekday(value: string): string {
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN', { weekday: 'long' }).format(new Date(year, month - 1, day));
  }

  protected formatTime(value: string): string {
    return formatClinicTime(value);
  }

  protected isLate(): boolean {
    const app = this.appointment();
    if (!app || app.status !== 'BOOKED') return false;
    const duration = app.serviceDurationMinutes || 60;
    const [year, month, day] = app.appointmentDate.split('-').map(Number);
    const [hour, minute] = app.startTime.split(':').map(Number);
    const scheduledStart = Date.parse(
      `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00+07:00`,
    );
    return Date.now() >= scheduledStart + (duration + 15) * 60_000;
  }

  protected canCancel(): boolean {
    return this.appointment()?.status === 'BOOKED';
  }

  protected canCheckIn(): boolean {
    const appointment = this.appointment();
    return appointment?.status === 'BOOKED'
      && appointment.appointmentDate === clinicTodayIso()
      && !this.isLate();
  }

  protected hasOpenReschedulingCase(): boolean {
    return this.rescheduleCase()?.status === 'OPEN';
  }

  protected openCancel(): void {
    if (!this.canCancel()) return;
    this.cancelOpen.set(true);
    this.error.set('');
  }

  protected closeCancel(): void {
    if (this.busy()) return;
    this.cancelOpen.set(false);
  }

  protected cancel(): void {
    const appointment = this.appointment();
    if (!appointment || !this.canCancel()) return;
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

  protected chooseReplacement(slot: AvailableReplacementSlot): void {
    this.selectedReplacement.set(slot);
    this.error.set('');
  }

  protected confirmPatientReschedule(): void {
    const appointment = this.appointment();
    const selected = this.selectedReplacement();
    if (!appointment || !this.canCancel() || !this.hasOpenReschedulingCase() || !selected) {
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

  protected retryReplacementSearch(): void {
    const appointment = this.appointment();
    if (appointment && this.hasOpenReschedulingCase()) this.loadReplacementSlots(appointment.id);
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

}
