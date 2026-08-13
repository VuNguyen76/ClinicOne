import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
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
import { AccountMenu } from '../../../shared/account-menu/account-menu';
import { clinicTodayIso } from '../../../core/time/clinic-time';

@Component({
  selector: 'app-appointment-detail',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './appointment-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppointmentDetail implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly appointment = signal<AppointmentResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly confirmCancel = signal(false);
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
  protected readonly today = clinicTodayIso();
  protected readonly rescheduleForm = this.formBuilder.nonNullable.group({
    appointmentDate: ['', [Validators.required]],
    startTime: ['', [Validators.required]],
  });

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
          this.rescheduleForm.setValue({ appointmentDate: appointment.appointmentDate, startTime: appointment.startTime.slice(0, 5) });
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

  protected formatTime(value: string): string {
    return value.slice(0, 5);
  }

  protected canEdit(): boolean {
    return this.appointment()?.status === 'BOOKED';
  }

  protected hasOpenReschedulingCase(): boolean {
    return this.rescheduleCase()?.status === 'OPEN';
  }

  protected cancel(): void {
    const appointment = this.appointment();
    if (!appointment || !this.canEdit()) {
      return;
    }
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

  protected reschedule(): void {
    const appointment = this.appointment();
    if (!appointment || !this.canEdit() || this.hasOpenReschedulingCase()) {
      return;
    }
    if (this.rescheduleForm.invalid) {
      this.rescheduleForm.markAllAsTouched();
      return;
    }
    const values = this.rescheduleForm.getRawValue();
    this.busy.set(true);
    this.authApi.rescheduleAppointment(appointment.id, values.appointmentDate, values.startTime)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (updated) => {
          this.appointment.set(updated);
          this.notice.set('Lịch hẹn đã được đổi thành công.');
          this.confirmCancel.set(false);
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
      if (!selected) {
        this.error.set('Hãy chọn một khung giờ thay thế.');
      }
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
          });
          this.rescheduleCase.set(null);
          this.replacementSlots.set([]);
          this.selectedReplacement.set(null);
          this.notice.set('Đã xác nhận khung giờ thay thế.');
        },
        error: (response) => this.handleError(response),
      });
  }

  private loadPatientReschedulingCase(appointmentId: string): void {
    this.rescheduleCaseLoading.set(true);
    this.authApi.getPatientReschedulingCase(appointmentId).subscribe({
      next: (item) => {
        this.rescheduleCase.set(item);
        this.rescheduleCaseLoading.set(false);
        if (item.status === 'OPEN') {
          this.loadReplacementSlots(appointmentId);
        }
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
        this.replacementSearchFailed.set(false);
        this.replacementLoading.set(false);
      },
      error: (response) => {
        this.replacementLoading.set(false);
        this.replacementSearchFailed.set(true);
        this.handleError(response);
      },
    });
  }

  protected retryReplacementSearch(): void {
    const appointment = this.appointment();
    if (appointment && this.hasOpenReschedulingCase()) {
      this.loadReplacementSlots(appointment.id);
    }
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
}
