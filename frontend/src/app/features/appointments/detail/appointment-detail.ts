import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import { apiErrorMessage, ApiErrorResponse, AppointmentResponse, AuthApiService, ReasonCatalogResponse } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';

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
  protected readonly today = new Date().toISOString().slice(0, 10);
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

  protected cancel(): void {
    const appointment = this.appointment();
    if (!appointment || !this.canEdit()) {
      return;
    }
    this.busy.set(true);
    this.authApi.cancelAppointment(appointment.id, this.selectedCancellationReason() || undefined)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/dashboard'),
        error: (response) => this.handleError(response),
      });
  }

  protected reschedule(): void {
    const appointment = this.appointment();
    if (!appointment || !this.canEdit()) {
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

  private handleError(response: ApiErrorResponse & { status?: number }): void {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOnePatientName');
      void this.router.navigateByUrl('/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }
}
