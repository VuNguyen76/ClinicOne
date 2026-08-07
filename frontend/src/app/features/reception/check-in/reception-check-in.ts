import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AppointmentSlotResponse, AuthApiService, ReceptionAppointmentResponse, ReceptionPatientProfile, SpecialtyOption, apiErrorMessage } from '../../../core/auth/auth-api.service';
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
  protected readonly walkInOpen = signal(false);
  protected readonly walkInPhone = signal('');
  protected readonly walkInDate = signal(this.toIsoDate(new Date()));
  protected readonly walkInProfiles = signal<ReceptionPatientProfile[]>([]);
  protected readonly walkInProfileId = signal('');
  protected readonly walkInSpecialties = signal<SpecialtyOption[]>([]);
  protected readonly walkInSpecialty = signal('');
  protected readonly walkInSlots = signal<AppointmentSlotResponse[]>([]);
  protected readonly walkInStartTime = signal('');
  protected readonly walkInReason = signal('');
  protected readonly walkInExceptionReason = signal('');
  protected readonly walkInLoading = signal(false);
  protected readonly walkInProfilesLoading = signal(false);
  protected readonly walkInSlotsLoading = signal(false);

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

  protected openWalkIn(): void {
    this.walkInOpen.set(true);
    this.walkInPhone.set('');
    this.walkInDate.set(this.toIsoDate(new Date()));
    this.walkInProfiles.set([]);
    this.walkInProfileId.set('');
    this.walkInSpecialty.set('');
    this.walkInSlots.set([]);
    this.walkInStartTime.set('');
    this.walkInReason.set('');
    this.walkInExceptionReason.set('');
    this.error.set('');
    if (this.walkInSpecialties().length === 0) {
      this.authApi.getSpecialties().subscribe({
        next: (specialties) => this.walkInSpecialties.set(specialties),
        error: (response) => this.handleError(response),
      });
    }
  }

  protected closeWalkIn(): void {
    if (!this.walkInLoading()) this.walkInOpen.set(false);
  }

  protected loadWalkInProfiles(): void {
    const phone = this.walkInPhone().trim();
    if (!/^0\d{9}$/.test(phone)) {
      this.error.set('Nhập số điện thoại gồm 10 chữ số để tìm hồ sơ.');
      return;
    }
    this.walkInProfilesLoading.set(true);
    this.error.set('');
    this.authApi.getReceptionProfiles(phone).subscribe({
      next: (profiles) => {
        this.walkInProfiles.set(profiles);
        this.walkInProfileId.set(profiles.find((profile) => profile.primaryProfile)?.id ?? profiles[0]?.id ?? '');
        this.walkInProfilesLoading.set(false);
      },
      error: (response) => {
        this.walkInProfilesLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected loadWalkInSlots(): void {
    const specialty = this.walkInSpecialty();
    if (!specialty || !this.walkInDate()) {
      this.walkInSlots.set([]);
      this.walkInStartTime.set('');
      return;
    }
    this.walkInSlotsLoading.set(true);
    this.authApi.getAppointmentSlots(specialty, this.walkInDate(), this.walkInDate()).subscribe({
      next: (slots) => {
        const available = slots.filter((slot) => !!slot.doctorId && slot.remainingCapacity > 0);
        this.walkInSlots.set(available);
        this.walkInStartTime.set(available[0]?.startTime ?? '');
        this.walkInSlotsLoading.set(false);
      },
      error: (response) => {
        this.walkInSlotsLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected submitWalkIn(): void {
    const phone = this.walkInPhone().trim();
    const slot = this.walkInSlots().find((item) => item.startTime === this.walkInStartTime());
    const reason = this.walkInReason().trim();
    const exceptionReason = this.walkInExceptionReason().trim();
    if (!/^0\d{9}$/.test(phone)) {
      this.error.set('Nhập số điện thoại hợp lệ trước khi tiếp nhận.');
      return;
    }
    if (!slot?.doctorId) {
      this.error.set('Chọn một khung giờ còn trống.');
      return;
    }
    if (reason.length < 3 || exceptionReason.length < 3) {
      this.error.set('Nhập lý do khám và lý do tiếp nhận tại quầy.');
      return;
    }
    this.walkInLoading.set(true);
    this.error.set('');
    this.authApi.createReceptionWalkIn({
      phone,
      profileId: this.walkInProfileId() || null,
      doctorId: slot.doctorId,
      appointmentDate: this.walkInDate(),
      startTime: slot.startTime,
      reason,
      exceptionReason,
    }).subscribe({
      next: (appointment) => {
        this.walkInLoading.set(false);
        this.walkInOpen.set(false);
        this.appointments.update((items) => [appointment, ...items]);
        this.notice.set(`Đã tạo lịch và cấp số ${String(appointment.queueNumber).padStart(2, '0')} cho ${appointment.patientName}.`);
      },
      error: (response) => {
        this.walkInLoading.set(false);
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
