import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AppointmentSlotResponse, AuthApiService, ReceptionAppointmentResponse, ReceptionDoctorOption, ReceptionPatientProfile, SpecialtyOption, apiErrorMessage } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';
import { StaffWorkspaceShell } from '../../../shared/staff-workspace-shell/staff-workspace-shell';
import { clinicTodayIso } from '../../../core/time/clinic-time';
import { hasStaffRole } from '../../../core/auth/auth.guard';
import { VietnamAddressService, VietnamAddressUnit } from '../../../core/address/vietnam-address.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';

type ReceptionTab = 'overview' | 'search' | 'intake' | 'queue' | 'exceptions' | 'profiles';

@Component({
  selector: 'app-reception-check-in',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './reception-check-in.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceptionCheckIn implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly addressApi = inject(VietnamAddressService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly query = signal('');
  protected readonly activeTab = signal<ReceptionTab>('overview');
  protected readonly profileQuery = signal('');
  protected readonly profileResults = signal<ReceptionPatientProfile[]>([]);
  protected readonly profileLoading = signal(false);
  protected readonly selectedDate = signal(clinicTodayIso());
  protected readonly exceptionReason = signal('');
  protected readonly leaveReason = signal('');
  protected readonly appointments = signal<ReceptionAppointmentResponse[]>([]);
  protected readonly loading = signal(false);
  protected readonly searched = signal(false);
  protected readonly busyId = signal('');
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly walkInOpen = signal(false);
  protected readonly walkInPhone = signal('');
  protected readonly walkInDate = signal(clinicTodayIso());
  protected readonly walkInProfiles = signal<ReceptionPatientProfile[]>([]);
  protected readonly walkInProfileId = signal('');
  protected readonly walkInSpecialties = signal<SpecialtyOption[]>([]);
  protected readonly walkInSpecialty = signal('');
  protected readonly walkInSlots = signal<AppointmentSlotResponse[]>([]);
  protected readonly walkInStartTime = signal('');
  protected readonly walkInReason = signal('');
  protected readonly walkInExceptionReason = signal('');
  protected readonly walkInLoading = signal(false);
  protected readonly walkInRequestKey = signal('');
  protected readonly walkInProfilesLoading = signal(false);
  protected readonly walkInSlotsLoading = signal(false);
  protected readonly walkInRegistration = signal(false);
  protected readonly walkInTemporaryProfile = signal(false);
  protected readonly walkInNeedsPasswordChange = signal(false);
  protected readonly walkInProfileNeedsCompletion = signal(false);
  protected readonly walkInOtp = signal('');
  protected readonly registrationFullName = signal('');
  protected readonly registrationDateOfBirth = signal('');
  protected readonly registrationGender = signal('');
  protected readonly registrationIdentityNumber = signal('');
  protected readonly registrationNationality = signal('Việt Nam');
  protected readonly registrationEthnicity = signal('Kinh');
  protected readonly registrationAddress = signal('');
  protected readonly registrationProvinceCode = signal('');
  protected readonly registrationProvinceName = signal('');
  protected readonly registrationDistrictCode = signal('');
  protected readonly registrationDistrictName = signal('');
  protected readonly registrationWardCode = signal('');
  protected readonly registrationWardName = signal('');
  protected readonly registrationStreetAddress = signal('');
  protected readonly registrationProvinces = signal<VietnamAddressUnit[]>([]);
  protected readonly registrationDistricts = signal<VietnamAddressUnit[]>([]);
  protected readonly registrationWards = signal<VietnamAddressUnit[]>([]);
  protected readonly registrationAddressLoading = signal(false);
  protected readonly registrationOtpSent = signal(false);
  protected readonly registrationLoading = signal(false);
  protected readonly activationPassword = signal('');
  protected readonly activationConfirmPassword = signal('');
  protected readonly activationOtp = signal('');
  protected readonly activationOtpSent = signal(false);
  protected readonly activationLoading = signal(false);
  protected readonly doctors = signal<ReceptionDoctorOption[]>([]);
  protected readonly adjustmentTicket = signal<ReceptionAppointmentResponse | null>(null);
  protected readonly adjustmentDoctorId = signal('');
  protected readonly adjustmentReason = signal('');
  protected readonly adjustmentLoading = signal(false);
  protected readonly rebookTicket = signal<ReceptionAppointmentResponse | null>(null);
  protected readonly rebookMode = signal<'LATE' | 'ABSENT'>('ABSENT');
  protected readonly rebookDate = signal(clinicTodayIso());
  protected readonly rebookSlots = signal<AppointmentSlotResponse[]>([]);
  protected readonly rebookStartTime = signal('');
  protected readonly rebookReason = signal('');
  protected readonly rebookSlotsLoading = signal(false);
  protected readonly rebookLoading = signal(false);
  protected readonly visibleAppointments = computed(() => {
    if (this.activeTab() !== 'exceptions') return this.appointments();
    return this.appointments().filter((appointment) => appointment.status === 'ABSENT'
      || appointment.queuePresenceStatus === 'RETURN_REQUIRED'
      || appointment.queueStatus === 'FACILITY_UNAVAILABLE'
      || appointment.status === 'RESCHEDULE_REQUIRED');
  });
  protected readonly dashboardCounts = computed(() => {
    const items = this.appointments();
    return {
      total: items.length,
      waiting: items.filter((item) => item.queueStatus === 'WAITING').length,
      checkedIn: items.filter((item) => item.queueStatus && item.queueStatus !== 'COMPLETED').length,
      exceptions: items.filter((item) => item.status === 'ABSENT'
        || item.queuePresenceStatus === 'RETURN_REQUIRED'
        || item.queueStatus === 'FACILITY_UNAVAILABLE'
        || item.status === 'RESCHEDULE_REQUIRED').length,
    };
  });

  ngOnInit(): void {
    this.query.set('');
    interval(3000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refreshSearch());
  }

  protected selectTab(tab: ReceptionTab | string): void {
    if (!['overview', 'search', 'intake', 'queue', 'exceptions', 'profiles'].includes(tab)) return;
    this.activeTab.set(tab as ReceptionTab);
    this.error.set('');
  }

  protected searchProfiles(): void {
    const phone = this.profileQuery().trim();
    if (!/^0\d{9}$/.test(phone)) {
      this.error.set('Nhập số điện thoại gồm 10 chữ số để tra hồ sơ.');
      return;
    }
    this.profileLoading.set(true);
    this.error.set('');
    this.authApi.getReceptionProfiles(phone).subscribe({
      next: (profiles) => {
        this.profileResults.set(profiles);
        this.profileLoading.set(false);
        if (profiles.length === 0) this.notice.set('Chưa có hồ sơ phù hợp.');
      },
      error: (response) => {
        this.profileLoading.set(false);
        if (response.status === 404) {
          this.profileResults.set([]);
          this.notice.set('Chưa có hồ sơ cho số điện thoại này.');
          return;
        }
        this.handleError(response);
      },
    });
  }

  protected openIntakeForProfile(profile: ReceptionPatientProfile): void {
    this.openWalkIn();
    this.walkInPhone.set(profile.phone || this.profileQuery());
    this.walkInProfileId.set(profile.id);
    this.walkInProfiles.set([profile]);
  }

  protected search(): void {
    this.activeTab.set('search');
    const value = this.query().trim();
    if (value.length < 3) {
      this.error.set('Nhập ít nhất 3 ký tự của mã lịch hẹn hoặc số điện thoại.');
      return;
    }
    this.loading.set(true);
    this.searched.set(true);
    this.error.set('');
    this.notice.set('');
    this.leaveReason.set('');
    this.authApi.searchReceptionAppointments(value, this.selectedDate()).subscribe({
      next: (appointments) => {
        this.appointments.set(appointments);
        this.loading.set(false);
        if (appointments.length === 1 && appointments[0].status === 'ABSENT') {
          this.openRebook(appointments[0]);
        }
        if (appointments.length === 0) this.notice.set('Không tìm thấy lịch hẹn phù hợp trong ngày đã chọn.');
      },
      error: (response) => {
        this.loading.set(false);
        this.handleError(response);
      },
    });
  }

  private refreshSearch(): void {
    const value = this.query().trim();
    if (!this.searched() || value.length < 3 || this.loading() || this.walkInOpen()) return;
    this.authApi.searchReceptionAppointments(value, this.selectedDate()).subscribe({
      next: (appointments) => this.appointments.set(appointments),
      error: (response) => this.handleError(response),
    });
  }

  protected hasAbsentAppointments(): boolean {
    return this.appointments().some((appointment) => appointment.status === 'ABSENT');
  }

  protected checkIn(appointment: ReceptionAppointmentResponse): void {
    if (appointment.status !== 'BOOKED' || !appointment.roomCode || appointment.queueStatus) return;
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
        const errorCode = typeof response?.error === 'object' && response.error !== null
          ? response.error.code
          : undefined;
        if (errorCode === 'QUEUE_LATE_APPOINTMENT') {
          this.openRebook(appointment);
          this.notice.set('Lịch đã quá giờ check-in; hãy chọn khung giờ thay thế cho người bệnh.');
          return;
        }
        this.handleError(response);
      },
    });
  }

  protected leaveBeforeExam(appointment: ReceptionAppointmentResponse): void {
    if (!this.canAdjustQueue()) {
      this.error.set('Chỉ nhân viên tiếp nhận được ghi nhận người bệnh rời trước khám.');
      return;
    }
    if (appointment.queueStatus !== 'WAITING') return;
    const reason = this.leaveReason().trim();
    if (reason.length < 10) {
      this.error.set('Nhập lý do người bệnh rời trước khám (ít nhất 10 ký tự).');
      return;
    }
    this.busyId.set(appointment.id);
    this.error.set('');
    this.notice.set('');
    this.authApi.leaveReceptionAppointment(appointment.id, reason).subscribe({
      next: (updated) => {
        this.appointments.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.busyId.set('');
        this.leaveReason.set('');
        this.notice.set(`Đã ghi nhận ${updated.patientName} rời trước khám.`);
      },
      error: (response) => {
        this.busyId.set('');
        this.handleError(response);
      },
    });
  }

  protected markFacilityUnavailable(appointment: ReceptionAppointmentResponse): void {
    if (!this.canAdjustQueue() || !appointment.queueTicketId) return;
    const reason = this.exceptionReason().trim();
    if (reason.length < 10) {
      this.error.set('Nhập lý do cơ sở tạm dừng phục vụ (ít nhất 10 ký tự).');
      return;
    }
    this.busyId.set(appointment.id);
    this.error.set('');
    this.notice.set('');
    this.authApi.markReceptionFacilityUnavailable(appointment.id, reason).subscribe({
      next: (updated) => {
        this.appointments.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.busyId.set('');
        this.notice.set(`Đã ghi nhận cơ sở tạm dừng phục vụ cho ${updated.patientName}.`);
      },
      error: (response) => {
        this.busyId.set('');
        this.handleError(response);
      },
    });
  }

  protected openAdjustment(appointment: ReceptionAppointmentResponse): void {
    if (appointment.queueStatus !== 'WAITING' || !appointment.queueTicketId) return;
    this.adjustmentTicket.set(appointment);
    this.adjustmentDoctorId.set('');
    this.adjustmentReason.set('');
    this.error.set('');
    if (this.doctors().length === 0) {
      this.authApi.getReceptionDoctors().subscribe({
        next: (doctors) => this.doctors.set(doctors),
        error: (response) => this.handleError(response),
      });
    }
  }

  protected openRebook(appointment: ReceptionAppointmentResponse): void {
    if (appointment.status !== 'ABSENT' && appointment.status !== 'BOOKED') return;
    this.rebookTicket.set(appointment);
    this.rebookMode.set(appointment.status === 'BOOKED' ? 'LATE' : 'ABSENT');
    this.rebookDate.set(clinicTodayIso());
    this.rebookStartTime.set('');
    this.rebookReason.set('');
    this.error.set('');
    this.loadRebookSlots();
  }

  protected closeRebook(): void {
    if (!this.rebookLoading()) this.rebookTicket.set(null);
  }

  protected loadRebookSlots(): void {
    const appointment = this.rebookTicket();
    const date = this.rebookDate();
    if (!appointment || !date) {
      this.rebookSlots.set([]);
      this.rebookStartTime.set('');
      return;
    }
    this.rebookSlotsLoading.set(true);
    this.authApi.getAppointmentSlots(appointment.specialty, date, date).subscribe({
      next: (slots) => {
        const available = slots.filter((slot) => !!slot.doctorId);
        this.rebookSlots.set(available);
        this.rebookStartTime.set(available[0]?.startTime ?? '');
        this.rebookSlotsLoading.set(false);
      },
      error: (response) => {
        this.rebookSlotsLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected submitRebook(): void {
    const appointment = this.rebookTicket();
    const slot = this.rebookSlots().find((item) => item.startTime === this.rebookStartTime());
    const reason = this.rebookReason().trim();
    if (!appointment || !slot?.doctorId) {
      this.error.set('Chọn một khung giờ còn trống.');
      return;
    }
    const minimumReasonLength = this.rebookMode() === 'LATE' ? 3 : 10;
    if (reason.length < minimumReasonLength) {
      this.error.set(`Nhập lý do (tối thiểu ${minimumReasonLength} ký tự).`);
      return;
    }
    this.rebookLoading.set(true);
    this.error.set('');
    const request = {
      doctorId: slot.doctorId,
      appointmentDate: this.rebookDate(),
      startTime: slot.startTime,
      lateReason: reason,
    };
    const operation = this.rebookMode() === 'LATE'
      ? this.authApi.rescheduleLateReceptionAppointment(appointment.id, request)
      : this.authApi.rebookReceptionAppointment(appointment.id, request);
    operation.subscribe({
      next: (updated) => {
        this.rebookLoading.set(false);
        this.rebookTicket.set(null);
        this.appointments.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        if (this.rebookMode() === 'LATE') {
          this.notice.set(`Đã chuyển lịch ${updated.appointmentCode} sang khung giờ mới cho ${updated.patientName}.`);
        } else {
          this.appointments.update((items) => [updated, ...items]);
          this.notice.set(`Đã tạo lịch mới ${updated.appointmentCode} cho ${updated.patientName}.`);
        }
      },
      error: (response) => {
        this.rebookLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected closeAdjustment(): void {
    if (!this.adjustmentLoading()) this.adjustmentTicket.set(null);
  }

  protected adjustQueue(action: 'MOVE' | 'SET_PRIORITY' | 'CLEAR_PRIORITY'): void {
    if (!this.canAdjustQueue()) {
      this.error.set('Chỉ nhân viên tiếp nhận được điều chỉnh hàng đợi.');
      return;
    }
    const appointment = this.adjustmentTicket();
    const reason = this.adjustmentReason().trim();
    if (!appointment?.queueTicketId) return;
    if (reason.length < 10) {
      this.error.set('Lý do điều chỉnh phải từ 10 ký tự.');
      return;
    }
    const doctor = this.doctors().find((item) => item.staffId === this.adjustmentDoctorId());
    if (action === 'MOVE' && !doctor) {
      this.error.set('Chọn bác sĩ và phòng đích.');
      return;
    }
    this.adjustmentLoading.set(true);
    this.error.set('');
    this.authApi.adjustQueueTicket(appointment.queueTicketId, {
      action,
      targetDoctorId: action === 'MOVE' ? doctor?.staffId : undefined,
      targetRoomCode: action === 'MOVE' ? doctor?.roomCode : undefined,
      targetSpecialty: action === 'MOVE' ? doctor?.specialty : undefined,
      reason,
    }).subscribe({
      next: (ticket) => {
        this.appointments.update((items) => items.map((item) => item.id === appointment.id ? {
          ...item,
          specialty: ticket.specialty,
          doctorName: ticket.doctorName,
          roomCode: ticket.roomCode,
          roomName: ticket.roomName,
          queueNumber: ticket.queueNumber,
          queueStatus: ticket.status,
          queueStatusLabel: ticket.statusLabel,
          queuePresenceStatus: ticket.presenceStatus,
          queuePresenceLabel: ticket.presenceLabel,
          queueTicketId: ticket.id,
          queuePriority: ticket.priority,
        } : item));
        this.adjustmentLoading.set(false);
        this.adjustmentTicket.set(null);
        this.adjustmentReason.set('');
        this.notice.set(action === 'MOVE' ? 'Đã chuyển người bệnh sang hàng đợi mới.' : 'Đã cập nhật cờ ưu tiên.');
      },
      error: (response) => {
        this.adjustmentLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected openWalkIn(): void {
    this.walkInOpen.set(true);
    this.walkInRequestKey.set('');
    this.walkInPhone.set('');
    this.walkInDate.set(clinicTodayIso());
    this.walkInProfiles.set([]);
    this.walkInProfileId.set('');
    this.walkInSpecialty.set('');
    this.walkInSlots.set([]);
    this.walkInStartTime.set('');
    this.walkInReason.set('');
    this.walkInExceptionReason.set('');
    this.walkInRegistration.set(false);
    this.walkInTemporaryProfile.set(false);
    this.walkInNeedsPasswordChange.set(false);
    this.walkInProfileNeedsCompletion.set(false);
    this.walkInOtp.set('');
    this.registrationFullName.set('');
    this.registrationDateOfBirth.set('');
    this.registrationGender.set('');
    this.registrationIdentityNumber.set('');
    this.registrationNationality.set('Việt Nam');
    this.registrationEthnicity.set('Kinh');
    this.registrationAddress.set('');
    this.registrationProvinceCode.set('');
    this.registrationProvinceName.set('');
    this.registrationDistrictCode.set('');
    this.registrationDistrictName.set('');
    this.registrationWardCode.set('');
    this.registrationWardName.set('');
    this.registrationStreetAddress.set('');
    this.registrationOtpSent.set(false);
    this.activationPassword.set('');
    this.activationConfirmPassword.set('');
    this.activationOtp.set('');
    this.activationOtpSent.set(false);
    this.error.set('');
    if (this.walkInSpecialties().length === 0) {
      this.authApi.getSpecialties().subscribe({
        next: (specialties) => this.walkInSpecialties.set(specialties),
        error: (response) => this.handleError(response),
      });
    }
  }

  protected closeWalkIn(): void {
    if (!this.walkInLoading()) {
      this.walkInRequestKey.set('');
      this.walkInOpen.set(false);
    }
  }

  protected openTemporaryProfileForm(): void {
    this.walkInTemporaryProfile.set(true);
    this.registrationOtpSent.set(false);
    this.walkInOtp.set('');
    this.error.set('');
    this.notice.set('Tạo hồ sơ tạm để tiếp nhận ngay; hồ sơ chỉ hiển thị tại quầy cho tới khi xác thực số điện thoại.');
  }

  protected requestRegistrationOtp(): void {
    const phone = this.walkInPhone().trim();
    if (!/^0\d{9}$/.test(phone)) {
      this.error.set('Nhập số điện thoại hợp lệ trước khi gửi OTP.');
      return;
    }
    this.registrationLoading.set(true);
    this.error.set('');
    this.authApi.requestReceptionPatientOtp(phone).subscribe({
      next: () => {
        this.registrationLoading.set(false);
        this.registrationOtpSent.set(true);
        this.notice.set('Đã gửi OTP. Mã có hiệu lực trong 5 phút.');
      },
      error: (response) => {
        this.registrationLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected submitRegistration(): void {
    const phone = this.walkInPhone().trim();
    if (!/^0\d{9}$/.test(phone) || (!this.walkInTemporaryProfile() && !/^\d{6}$/.test(this.walkInOtp()))
      || this.registrationFullName().trim().length < 2 || !this.registrationDateOfBirth()
      || !this.registrationGender()) {
      this.error.set(this.walkInTemporaryProfile()
        ? 'Nhập đủ họ tên, ngày sinh và giới tính.'
        : 'Nhập đủ họ tên, ngày sinh, giới tính và mã OTP 6 số.');
      return;
    }
    this.registrationLoading.set(true);
    this.error.set('');
    if (this.walkInTemporaryProfile()) {
      this.authApi.createReceptionTemporaryProfile({
        phone,
        fullName: this.registrationFullName().trim(),
        dateOfBirth: this.registrationDateOfBirth(),
        gender: this.registrationGender(),
        identityNumber: this.registrationIdentityNumber().trim() || undefined,
        nationality: this.registrationNationality().trim() || undefined,
        ethnicity: this.registrationEthnicity().trim() || undefined,
        address: this.registrationAddress().trim() || undefined,
      }).subscribe({
        next: (profile) => {
          this.registrationLoading.set(false);
          this.walkInRegistration.set(false);
          this.walkInTemporaryProfile.set(false);
          this.walkInProfiles.set([profile]);
          this.walkInProfileId.set(profile.id);
          this.notice.set(`Đã tạo hồ sơ tạm cho ${profile.fullName}.`);
        },
        error: (response) => {
          this.registrationLoading.set(false);
          this.handleError(response);
        },
      });
      return;
    }
    this.authApi.registerReceptionPatient({
      phone,
      otpCode: this.walkInOtp(),
      fullName: this.registrationFullName().trim(),
      dateOfBirth: this.registrationDateOfBirth(),
      gender: this.registrationGender(),
      identityNumber: this.registrationIdentityNumber().trim() || undefined,
      nationality: this.registrationNationality().trim() || undefined,
      ethnicity: this.registrationEthnicity().trim() || undefined,
      address: this.registrationAddress().trim() || undefined,
      provinceCode: this.registrationProvinceCode() || undefined,
      provinceName: this.registrationProvinceName() || undefined,
      districtCode: this.registrationDistrictCode() || undefined,
      districtName: this.registrationDistrictName() || undefined,
      wardCode: this.registrationWardCode() || undefined,
      wardName: this.registrationWardName() || undefined,
      streetAddress: this.registrationStreetAddress().trim() || undefined,
    }).subscribe({
      next: (response) => {
        this.registrationLoading.set(false);
        this.walkInRegistration.set(false);
        this.walkInNeedsPasswordChange.set(true);
        this.notice.set(`Đã tạo tài khoản cho ${response.fullName}.`);
      },
      error: (response) => {
        this.registrationLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected activatePendingAccount(): void {
    const phone = this.walkInPhone().trim();
    const password = this.activationPassword();
    if (!/^0\d{9}$/.test(phone) || !/^\d{6}$/.test(this.activationOtp()) || password.length < 8 || password.length > 64) {
      this.error.set('Nhập mã OTP 6 số và mật khẩu mới từ 8 đến 64 ký tự.');
      return;
    }
    if (password !== this.activationConfirmPassword()) {
      this.error.set('Hai lần nhập mật khẩu mới không giống nhau.');
      return;
    }
    this.activationLoading.set(true);
    this.error.set('');
    this.authApi.activateReceptionPatientAccount(phone, this.activationOtp(), password, this.activationConfirmPassword()).subscribe({
      next: () => {
        this.activationLoading.set(false);
        this.activationPassword.set('');
        this.activationConfirmPassword.set('');
        this.activationOtp.set('');
        this.activationOtpSent.set(false);
        this.walkInNeedsPasswordChange.set(false);
        this.notice.set('Đã kích hoạt tài khoản. Có thể tiếp tục tiếp nhận.');
        this.loadWalkInProfiles();
      },
      error: (response) => {
        this.activationLoading.set(false);
        this.handleError(response);
      },
    });
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
        const selected = profiles.find((profile) => profile.primaryProfile) ?? profiles[0];
        this.walkInProfileId.set(selected?.id ?? '');
        this.prepareProfileCompletion(selected);
        if (profiles.some((profile) => profile.mustChangePassword || profile.accountStatus === 'LOCKED')) {
          this.walkInNeedsPasswordChange.set(true);
          this.notice.set(profiles.some((profile) => profile.accountStatus === 'LOCKED')
            ? 'Tài khoản đang bị khóa. Cần xử lý tài khoản trước khi tiếp nhận.'
            : 'Người bệnh chưa kích hoạt tài khoản. Hãy đặt mật khẩu mới trước khi tiếp tục.');
        } else {
          this.walkInNeedsPasswordChange.set(false);
        }
        this.walkInProfilesLoading.set(false);
      },
      error: (response) => {
        this.walkInProfilesLoading.set(false);
        if (response.status === 404) {
        this.walkInRegistration.set(true);
          this.walkInTemporaryProfile.set(false);
          this.error.set('');
          return;
        }
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
    const temporaryProfile = this.temporaryProfileSelected();
    const overCapacity = (slot?.remainingCapacity ?? 0) <= 0;
    if (!/^0\d{9}$/.test(phone)) {
      this.error.set('Nhập số điện thoại hợp lệ trước khi tiếp nhận.');
      return;
    }
    if (!slot?.doctorId) {
      this.error.set('Chọn một khung giờ còn trống.');
      return;
    }
    if (overCapacity && !temporaryProfile && exceptionReason.length < 10) {
      this.error.set('Nhập lý do nhận ngoài công suất (từ 10 đến 500 ký tự).');
      return;
    }
    if (reason.length < 3 || exceptionReason.length < (temporaryProfile ? 10 : 3)) {
      this.error.set(temporaryProfile
        ? 'Nhập lý do không thể xác thực (từ 10 đến 500 ký tự).'
        : 'Nhập lý do khám và lý do tiếp nhận tại quầy.');
      return;
    }
    this.walkInLoading.set(true);
    this.error.set('');
    const requestKey = this.walkInRequestKey() || `walk-in-${crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`}`;
    this.walkInRequestKey.set(requestKey);
    this.authApi.createReceptionWalkIn({
      phone,
      profileId: this.walkInProfileId() || null,
      doctorId: slot.doctorId,
      appointmentDate: this.walkInDate(),
      startTime: slot.startTime,
      reason,
      exceptionReason,
      ...(overCapacity ? { overCapacity: true } : {}),
    }, requestKey).subscribe({
      next: (appointment) => {
        this.walkInLoading.set(false);
        this.walkInRequestKey.set('');
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

  private prepareProfileCompletion(profile: ReceptionPatientProfile | undefined): void {
    if (!profile || profile.accountStatus !== 'ACTIVE' || profile.mustChangePassword) {
      this.walkInProfileNeedsCompletion.set(false);
      return;
    }
    this.registrationFullName.set(profile.fullName ?? '');
    this.registrationDateOfBirth.set(profile.dateOfBirth ?? '');
    this.registrationGender.set(profile.gender ?? '');
    this.registrationIdentityNumber.set(profile.identityNumber ?? '');
    this.registrationNationality.set(profile.nationality ?? '');
    this.registrationEthnicity.set(profile.ethnicity ?? '');
    this.registrationAddress.set(profile.streetAddress ?? profile.address ?? '');
    this.registrationProvinceCode.set(profile.provinceCode ?? '');
    this.registrationProvinceName.set(profile.provinceName ?? '');
    this.registrationDistrictCode.set(profile.districtCode ?? '');
    this.registrationDistrictName.set(profile.districtName ?? '');
    this.registrationWardCode.set(profile.wardCode ?? '');
    this.registrationWardName.set(profile.wardName ?? '');
    this.registrationStreetAddress.set(profile.streetAddress ?? '');
    this.walkInProfileNeedsCompletion.set([
      profile.fullName, profile.dateOfBirth, profile.gender, profile.identityNumber,
      profile.nationality, profile.ethnicity, profile.provinceCode, profile.districtCode,
      profile.wardCode, profile.streetAddress,
    ].some((value) => !value || !String(value).trim()));
    if (profile.provinceCode) {
      this.addressApi.getDistricts(profile.provinceCode).subscribe((items) => this.registrationDistricts.set(items));
    }
    if (profile.districtCode) {
      this.addressApi.getWards(profile.districtCode).subscribe((items) => this.registrationWards.set(items));
    }
    if (this.registrationProvinces().length === 0) {
      this.addressApi.getProvinces().subscribe((items) => this.registrationProvinces.set(items));
    }
  }

  protected selectWalkInProfile(profileId: string): void {
    this.walkInProfileId.set(profileId);
    this.prepareProfileCompletion(this.walkInProfiles().find((profile) => profile.id === profileId));
  }

  protected submitProfileCompletion(): void {
    const profileId = this.walkInProfileId();
    if (!profileId) return;
    if (this.registrationFullName().trim().length < 2 || !this.registrationDateOfBirth() || !this.registrationGender()) {
      this.error.set('Bổ sung họ tên, ngày sinh và giới tính trước khi tiếp nhận.');
      return;
    }
    this.registrationLoading.set(true);
    this.error.set('');
    this.authApi.updateReceptionPatientProfile(profileId, {
      fullName: this.registrationFullName().trim(), dateOfBirth: this.registrationDateOfBirth(),
      gender: this.registrationGender(), identityNumber: this.registrationIdentityNumber().trim(),
      nationality: this.registrationNationality().trim(), ethnicity: this.registrationEthnicity().trim(),
      address: this.registrationAddress().trim(),
      provinceCode: this.registrationProvinceCode(), provinceName: this.registrationProvinceName(),
      districtCode: this.registrationDistrictCode(), districtName: this.registrationDistrictName(),
      wardCode: this.registrationWardCode(), wardName: this.registrationWardName(),
      streetAddress: this.registrationStreetAddress().trim(),
    }).subscribe({
      next: (profile) => {
        this.registrationLoading.set(false);
        this.walkInProfiles.update((items) => items.map((item) => item.id === profile.id ? profile : item));
        this.walkInProfileNeedsCompletion.set(false);
        this.notice.set(`Đã bổ sung hồ sơ cho ${profile.fullName}.`);
      },
      error: (response) => {
        this.registrationLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected requestActivationOtp(): void {
    const phone = this.walkInPhone().trim();
    if (!/^0\d{9}$/.test(phone)) {
      this.error.set('Nhập số điện thoại hợp lệ trước khi gửi OTP.');
      return;
    }
    this.activationLoading.set(true);
    this.error.set('');
    this.authApi.requestReceptionPatientOtp(phone).subscribe({
      next: () => {
        this.activationLoading.set(false);
        this.activationOtpSent.set(true);
        this.notice.set('Đã gửi mã OTP kích hoạt qua SMS.');
      },
      error: (response) => {
        this.activationLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected registrationProvinceChanged(): void {
    const province = this.registrationProvinces().find((item) => String(item.code) === this.registrationProvinceCode());
    this.registrationProvinceName.set(province?.name ?? '');
    this.registrationDistrictCode.set('');
    this.registrationDistrictName.set('');
    this.registrationWardCode.set('');
    this.registrationWardName.set('');
    this.registrationDistricts.set([]);
    this.registrationWards.set([]);
    if (this.registrationProvinceCode()) {
      this.registrationAddressLoading.set(true);
      this.addressApi.getDistricts(this.registrationProvinceCode()).subscribe({
        next: (items) => this.registrationDistricts.set(items),
        complete: () => this.registrationAddressLoading.set(false),
        error: () => this.registrationAddressLoading.set(false),
      });
    }
  }

  protected registrationDistrictChanged(): void {
    const district = this.registrationDistricts().find((item) => String(item.code) === this.registrationDistrictCode());
    this.registrationDistrictName.set(district?.name ?? '');
    this.registrationWardCode.set('');
    this.registrationWardName.set('');
    this.registrationWards.set([]);
    if (this.registrationDistrictCode()) {
      this.registrationAddressLoading.set(true);
      this.addressApi.getWards(this.registrationDistrictCode()).subscribe({
        next: (items) => this.registrationWards.set(items),
        complete: () => this.registrationAddressLoading.set(false),
        error: () => this.registrationAddressLoading.set(false),
      });
    }
  }

  protected registrationWardChanged(): void {
    const ward = this.registrationWards().find((item) => String(item.code) === this.registrationWardCode());
    this.registrationWardName.set(ward?.name ?? '');
  }

  protected formatTime(value: string): string {
    return value?.slice(0, 5) ?? '';
  }

  protected temporaryProfileSelected(): boolean {
    const profileId = this.walkInProfileId();
    if (!profileId) {
      return false;
    }
    return this.walkInProfiles().find((profile) => profile.id === profileId)?.accountStatus === null;
  }

  protected queueLabel(appointment: ReceptionAppointmentResponse): string {
    return appointment.queueNumber == null ? 'Chưa lấy số' : `Số ${String(appointment.queueNumber).padStart(2, '0')} · ${appointment.queueStatusLabel}`;
  }

  protected queueClass(appointment: ReceptionAppointmentResponse): string {
    if (!appointment.queueStatus) return 'erp-badge-warning';
    if (appointment.queueStatus === 'LEFT_BEFORE_EXAM' || appointment.queueStatus === 'SKIPPED') {
      return 'erp-badge-warning';
    }
    return 'erp-badge-success';
  }

  protected queueDotClass(appointment: ReceptionAppointmentResponse): string {
    if (!appointment.queueStatus) return 'erp-dot-warning';
    if (appointment.queueStatus === 'LEFT_BEFORE_EXAM' || appointment.queueStatus === 'SKIPPED') {
      return 'erp-dot-warning';
    }
    return 'erp-dot-success';
  }

  protected canAdjustQueue(): boolean { return hasStaffRole('RECEPTIONIST'); }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOneSessionType');
      sessionStorage.removeItem('clinicOneStaffRole');
      sessionStorage.removeItem('clinicOneStaffRoles');
      void this.router.navigateByUrl('/staff/login');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }

  private toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
