import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AppointmentSlotResponse, AuthApiService, ReceptionAppointmentResponse, ReceptionDoctorOption, ReceptionPatientProfile, SpecialtyOption, apiErrorMessage } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';
import { StaffWorkspaceShell } from '../../../shared/staff-workspace-shell/staff-workspace-shell';
import { clinicTodayIso, formatClinicPadDate } from '../../../core/time/clinic-time';
import { hasStaffRole } from '../../../core/auth/auth.guard';
import { VietnamAddressService, VietnamAddressUnit } from '../../../core/address/vietnam-address.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';

type ReceptionTab = 'overview' | 'search' | 'intake' | 'queue' | 'exceptions' | 'profiles';

@Component({
  selector: 'app-reception-check-in',
  standalone: true,
  imports: [FormsModule, MatIconModule, RouterLink, StaffWorkspaceShell],
  templateUrl: './reception-check-in.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceptionCheckIn implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly addressApi = inject(VietnamAddressService);
  private readonly destroyRef = inject(DestroyRef);
  private walkInProfileRequestId = 0;
  private walkInSlotRequestId = 0;
  private walkInOtpRequestId = 0;
  private rebookSlotRequestId = 0;
  private adjustmentDoctorRequestId = 0;
  private registrationAddressRequestId = 0;
  private worklistRequestId = 0;
  private searchRequestId = 0;
  private profileSearchRequestId = 0;
  private submittedSearchQuery = '';
  private submittedSearchDate = '';

  protected readonly query = signal('');
  protected readonly activeTab = signal<ReceptionTab>(receptionTab(this.route.snapshot.data['receptionView']));
  protected readonly pageTitle = computed(() => receptionPageTitle(this.activeTab()));
  protected readonly profileQuery = signal('');
  protected readonly profileResults = signal<ReceptionPatientProfile[]>([]);
  protected readonly profileLoading = signal(false);
  protected readonly selectedDate = signal(clinicTodayIso());
  protected readonly exceptionReason = signal('');
  protected readonly leaveReason = signal('');
  protected readonly appointments = signal<ReceptionAppointmentResponse[]>([]);
  protected readonly loading = signal(false);
  protected readonly searched = signal(false);
  protected readonly busyAppointmentIds = signal<ReadonlySet<string>>(new Set());
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
  protected readonly walkInAccountLocked = signal(false);
  protected readonly pendingAccountNeedsOtp = signal(false);
  protected readonly activationOtpRequested = signal(false);
  protected readonly activationOtpVerified = signal(false);
  protected readonly walkInProfileNeedsCompletion = signal(false);
  protected readonly completionProfile = signal<ReceptionPatientProfile | null>(null);
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
  protected readonly identityMutationLoading = signal(false);
  protected readonly activationPassword = signal('');
  protected readonly activationConfirmPassword = signal('');
  protected readonly activationLoading = signal(false);
  protected readonly doctors = signal<ReceptionDoctorOption[]>([]);
  protected readonly adjustmentTicket = signal<ReceptionAppointmentResponse | null>(null);
  protected readonly adjustmentDoctorId = signal('');
  protected readonly adjustmentReason = signal('');
  protected readonly adjustmentLoading = signal(false);
  protected readonly adjustmentShowDifferentSpecialty = signal(false);
  protected readonly adjustmentSelectedSlot = signal('');
  protected readonly filteredDoctors = computed(() => {
    const ticket = this.adjustmentTicket();
    const showDiff = this.adjustmentShowDifferentSpecialty();
    const all = this.doctors();
    if (!ticket || showDiff) return all;
    return all.filter(d => d.specialty === ticket.specialty);
  });
  protected readonly rebookTicket = signal<ReceptionAppointmentResponse | null>(null);
  protected readonly rebookMode = signal<'LATE' | 'ABSENT'>('ABSENT');
  protected readonly rebookDate = signal(clinicTodayIso());
  protected readonly rebookSlots = signal<AppointmentSlotResponse[]>([]);
  protected readonly rebookStartTime = signal('');
  protected readonly rebookReason = signal('');
  protected readonly rebookSlotsLoading = signal(false);
  protected readonly rebookLoading = signal(false);
  protected readonly rebookAvailableDates = signal<{ date: string; label: string; weekday: string; count: number }[]>([]);
  protected readonly walkInAvailableDates = signal<{ date: string; label: string; weekday: string; count: number }[]>([]);
  protected readonly printingSlip = signal<ReceptionAppointmentResponse | null>(null);
  protected readonly visibleAppointments = computed(() => {
    if (this.activeTab() !== 'exceptions') return this.appointments();
    return this.appointments().filter((appointment) => appointment.status === 'ABSENT'
      || appointment.queuePresenceStatus === 'RETURN_REQUIRED'
      || appointment.queueClosureOutcome === 'FACILITY_UNAVAILABLE'
      || appointment.lateArrival);
  });
  protected readonly queueAppointments = computed(() => this.appointments().filter((appointment) =>
    appointment.queueStatus === 'WAITING' || appointment.queueStatus === 'CALLED'));
  protected readonly dashboardCounts = computed(() => {
    const items = this.appointments();
    return {
      total: items.length,
      waiting: items.filter((item) => item.queueStatus === 'WAITING').length,
      checkedIn: items.filter((item) => item.queueStatus && item.queueStatus !== 'COMPLETED').length,
      exceptions: items.filter((item) => item.status === 'ABSENT'
        || item.queuePresenceStatus === 'RETURN_REQUIRED'
        || item.queueClosureOutcome === 'FACILITY_UNAVAILABLE'
        || item.lateArrival).length,
    };
  });

  ngOnInit(): void {
    this.query.set('');
    this.loadWorkspace();
    interval(3000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (['overview', 'queue', 'exceptions'].includes(this.activeTab())) this.loadWorkspace(true);
      else this.refreshSearch();
    });
  }

  protected loadWorkspace(silent = false): void {
    if (!['overview', 'queue', 'exceptions'].includes(this.activeTab())) return;
    const requestId = ++this.worklistRequestId;
    const date = this.selectedDate();
    if (!silent) this.loading.set(true);
    this.authApi.getReceptionWorklist(date).subscribe({
      next: (appointments) => {
        if (requestId !== this.worklistRequestId || this.selectedDate() !== date) return;
        this.appointments.set(appointments);
        this.loading.set(false);
      },
      error: (response) => {
        if (requestId !== this.worklistRequestId || this.selectedDate() !== date) return;
        this.loading.set(false);
        this.handleError(response);
      },
    });
  }

  protected onSelectedDateChange(date: string): void {
    if (date === this.selectedDate()) return;
    this.selectedDate.set(date);
    if (['overview', 'queue', 'exceptions'].includes(this.activeTab())) {
      this.loadWorkspace();
    } else if (this.activeTab() === 'search' && this.searched()) {
      this.search();
    }
  }

  protected selectTab(tab: ReceptionTab | string): void {
    if (!['overview', 'search', 'intake', 'queue', 'exceptions', 'profiles'].includes(tab)) return;
    const previousTab = this.activeTab();
    if (tab !== previousTab) {
      const worklistTabs: ReceptionTab[] = ['overview', 'queue', 'exceptions'];
      const wasWorklist = worklistTabs.includes(previousTab);
      const isWorklist = worklistTabs.includes(tab as ReceptionTab);
      if (wasWorklist && !isWorklist) this.worklistRequestId++;
      if (previousTab === 'search' && tab !== 'search') this.searchRequestId++;
      if (!wasWorklist && isWorklist) this.searchRequestId++;
      if (tab === 'search' || previousTab === 'search' || wasWorklist !== isWorklist) {
        this.appointments.set([]);
        this.searched.set(false);
      }
      this.loading.set(false);
    }
    this.activeTab.set(tab as ReceptionTab);
    this.error.set('');
    if (!['overview', 'queue', 'exceptions'].includes(previousTab)
      && ['overview', 'queue', 'exceptions'].includes(tab)) {
      this.loadWorkspace();
    }
  }

  protected searchProfiles(): void {
    const phone = this.profileQuery().trim();
    if (!/^0\d{9}$/.test(phone)) {
      this.error.set('Nhập số điện thoại gồm 10 chữ số để tra hồ sơ.');
      return;
    }
    const requestId = ++this.profileSearchRequestId;
    this.profileLoading.set(true);
    this.profileResults.set([]);
    this.notice.set('');
    this.error.set('');
    this.authApi.getReceptionProfiles(phone).subscribe({
      next: (profiles) => {
        if (requestId !== this.profileSearchRequestId) return;
        this.profileLoading.set(false);
        if (phone !== this.profileQuery().trim()) return;
        this.profileResults.set(profiles);
        if (profiles.length === 0) this.notice.set('Chưa có hồ sơ phù hợp.');
      },
      error: (response) => {
        if (requestId !== this.profileSearchRequestId) return;
        this.profileLoading.set(false);
        if (phone !== this.profileQuery().trim()) return;
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
    if (this.activeTab() === 'overview') {
      this.activeTab.set('search');
      this.worklistRequestId++;
    }
    const value = this.query().trim();
    if (value.length < 3) {
      this.searchRequestId++;
      this.submittedSearchQuery = '';
      this.loading.set(false);
      this.error.set('Nhập ít nhất 3 ký tự của mã lịch hẹn hoặc số điện thoại.');
      return;
    }
    const date = this.selectedDate();
    const requestId = ++this.searchRequestId;
    this.worklistRequestId++;
    this.submittedSearchQuery = value;
    this.submittedSearchDate = date;
    this.loading.set(true);
    this.searched.set(true);
    this.error.set('');
    this.notice.set('');
    this.leaveReason.set('');
    this.authApi.searchReceptionAppointments(value, date).subscribe({
      next: (appointments) => {
        if (requestId !== this.searchRequestId) return;
        this.appointments.set(appointments);
        this.loading.set(false);
        if (appointments.length === 1 && appointments[0].status === 'ABSENT') {
          this.openRebook(appointments[0]);
        }
        if (appointments.length === 0) this.notice.set('Không tìm thấy lịch hẹn phù hợp trong ngày đã chọn.');
      },
      error: (response) => {
        if (requestId !== this.searchRequestId) return;
        this.loading.set(false);
        this.handleError(response);
      },
    });
  }

  private refreshSearch(): void {
    if (!['search'].includes(this.activeTab()) || !this.searched()
      || this.submittedSearchQuery.length < 3 || this.loading() || this.walkInOpen()) return;
    const requestId = ++this.searchRequestId;
    this.authApi.searchReceptionAppointments(this.submittedSearchQuery, this.submittedSearchDate).subscribe({
      next: (appointments) => {
        if (requestId === this.searchRequestId) this.appointments.set(appointments);
      },
      error: (response) => {
        if (requestId === this.searchRequestId) this.handleError(response);
      },
    });
  }

  protected hasAbsentAppointments(): boolean {
    return this.appointments().some((appointment) => appointment.status === 'ABSENT');
  }

  protected isAppointmentBusy(appointmentId: string): boolean {
    return this.busyAppointmentIds().has(appointmentId);
  }

  private setAppointmentBusy(appointmentId: string, busy: boolean): void {
    this.busyAppointmentIds.update((current) => {
      const next = new Set(current);
      if (busy) next.add(appointmentId);
      else next.delete(appointmentId);
      return next;
    });
  }

  protected checkIn(appointment: ReceptionAppointmentResponse): void {
    if (this.isAppointmentBusy(appointment.id)) return;
    if (appointment.status !== 'BOOKED' || !appointment.roomCode || appointment.queueStatus) return;
    const reason = this.exceptionReason().trim();
    if (reason.length < 3) {
      this.error.set('Nhập lý do hỗ trợ tại quầy trước khi cấp số.');
      return;
    }
    this.setAppointmentBusy(appointment.id, true);
    this.error.set('');
    this.notice.set('');
    this.authApi.receptionCheckIn(appointment.id, appointment.roomCode, reason).subscribe({
      next: (updated) => {
        this.appointments.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.setAppointmentBusy(appointment.id, false);
        this.notice.set(`Đã cấp số ${String(updated.queueNumber).padStart(2, '0')} cho ${updated.patientName}.`);
      },
      error: (response) => {
        this.setAppointmentBusy(appointment.id, false);
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

  protected printQueueSlip(appointment: ReceptionAppointmentResponse): void {
    this.printingSlip.set(appointment);
    setTimeout(() => {
      window.print();
    }, 100);
  }

  protected closePrintSlip(): void {
    this.printingSlip.set(null);
  }

  protected markReturned(appointment: ReceptionAppointmentResponse): void {
    if (this.isAppointmentBusy(appointment.id)
      || appointment.queuePresenceStatus !== 'RETURN_REQUIRED' || !appointment.roomCode) return;
    this.setAppointmentBusy(appointment.id, true);
    this.error.set('');
    this.authApi.receptionCheckIn(appointment.id, appointment.roomCode, 'Người bệnh đã quay lại hàng đợi').subscribe({
      next: (updated) => {
        this.appointments.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.setAppointmentBusy(appointment.id, false);
        this.notice.set(`Đã xác nhận ${updated.patientName} quay lại hàng đợi.`);
      },
      error: (response) => {
        this.setAppointmentBusy(appointment.id, false);
        this.handleError(response);
      },
    });
  }

  protected leaveBeforeExam(appointment: ReceptionAppointmentResponse): void {
    if (!this.canAdjustQueue()) {
      this.error.set('Chỉ nhân viên tiếp nhận được ghi nhận người bệnh rời trước khám.');
      return;
    }
    if (this.isAppointmentBusy(appointment.id) || appointment.queueStatus !== 'WAITING') return;
    const reason = this.leaveReason().trim();
    if (reason.length < 10) {
      this.error.set('Nhập lý do người bệnh rời trước khám (ít nhất 10 ký tự).');
      return;
    }
    this.setAppointmentBusy(appointment.id, true);
    this.error.set('');
    this.notice.set('');
    this.authApi.leaveReceptionAppointment(appointment.id, reason).subscribe({
      next: (updated) => {
        this.appointments.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.setAppointmentBusy(appointment.id, false);
        this.leaveReason.set('');
        this.notice.set(`Đã ghi nhận ${updated.patientName} rời trước khám.`);
      },
      error: (response) => {
        this.setAppointmentBusy(appointment.id, false);
        this.handleError(response);
      },
    });
  }

  protected markFacilityUnavailable(appointment: ReceptionAppointmentResponse): void {
    if (this.isAppointmentBusy(appointment.id) || !this.canAdjustQueue() || !appointment.queueTicketId) return;
    const reason = this.exceptionReason().trim();
    if (reason.length < 10) {
      this.error.set('Nhập lý do phòng khám tạm dừng phục vụ (ít nhất 10 ký tự).');
      return;
    }
    this.setAppointmentBusy(appointment.id, true);
    this.error.set('');
    this.notice.set('');
    this.authApi.markReceptionFacilityUnavailable(appointment.id, reason).subscribe({
      next: (updated) => {
        this.appointments.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.setAppointmentBusy(appointment.id, false);
        this.notice.set(`Đã ghi nhận phòng khám tạm dừng phục vụ cho ${updated.patientName}.`);
      },
      error: (response) => {
        this.setAppointmentBusy(appointment.id, false);
        this.handleError(response);
      },
    });
  }

  protected openAdjustment(appointment: ReceptionAppointmentResponse): void {
    if (appointment.queueStatus !== 'WAITING' || !appointment.queueTicketId) return;
    const requestId = ++this.adjustmentDoctorRequestId;
    this.adjustmentTicket.set(appointment);
    this.adjustmentDoctorId.set('');
    this.doctors.set([]);
    this.adjustmentReason.set('');
    this.adjustmentShowDifferentSpecialty.set(false);
    this.adjustmentSelectedSlot.set(appointment.startTime?.slice(0,5) ?? '');
    this.error.set('');
    const date = appointment.appointmentDate ?? clinicTodayIso();
    this.authApi.getReceptionDoctorsAvailability(date).subscribe({
      next: (doctors) => {
        if (requestId === this.adjustmentDoctorRequestId) this.doctors.set(doctors);
      },
      error: (response) => {
        if (requestId === this.adjustmentDoctorRequestId) this.handleError(response);
      },
    });
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
    if (this.rebookLoading()) return;
    this.rebookSlotRequestId++;
    this.rebookSlotsLoading.set(false);
    this.rebookTicket.set(null);
  }

  protected loadRebookSlots(): void {
    const requestId = ++this.rebookSlotRequestId;
    const appointment = this.rebookTicket();
    const date = this.rebookDate();
    if (!appointment || !date) {
      this.rebookSlots.set([]);
      this.rebookStartTime.set('');
      this.rebookAvailableDates.set([]);
      this.rebookSlotsLoading.set(false);
      return;
    }
    this.rebookSlotsLoading.set(true);
    // LATE: within-day like queue adjustment -> use doctors?date= (lightweight, correct with ensureBookable)
    if (this.rebookMode() === 'LATE') {
      const today = clinicTodayIso();
      // Force today for LATE (business rule: must stay within same day)
      if (date !== today) {
        this.rebookDate.set(today);
      }
      this.authApi.getAppointmentSlots(
        appointment.specialty, today, today, appointment.serviceId ?? undefined,
      ).subscribe({
        next: (slots) => {
          if (requestId !== this.rebookSlotRequestId) return;
          const list = slots.filter((slot) => slot.appointmentDate === today
            && !!slot.doctorId && slot.remainingCapacity > 0 && slot.startTime !== appointment.startTime);
          this.rebookAvailableDates.set([]);
          this.rebookSlots.set(list);
          this.rebookStartTime.set(list[0]?.startTime ?? '');
          this.rebookSlotsLoading.set(false);
        },
        error: (response) => {
          if (requestId !== this.rebookSlotRequestId) return;
          this.rebookSlotsLoading.set(false);
          this.handleError(response);
        },
      });
      return;
    }
    const fromDate = date < clinicTodayIso() ? date : clinicTodayIso();
    const toDate = this.addDays(fromDate, 30);
    this.authApi.getAppointmentSlots(appointment.specialty, fromDate, toDate, appointment.serviceId ?? undefined).subscribe({
      next: (slots) => {
        if (requestId !== this.rebookSlotRequestId) return;
        const today = clinicTodayIso();
        const dateMap = new Map<string, number>();
        for (const slot of slots) {
          if (!slot.doctorId) continue;
          const isTodaySlot = slot.appointmentDate === today;
          if (isTodaySlot || slot.remainingCapacity > 0) {
            dateMap.set(slot.appointmentDate, (dateMap.get(slot.appointmentDate) || 0) + 1);
          }
        }
        const dates: { date: string; label: string; weekday: string; count: number }[] = [];
        for (const [dateStr, count] of dateMap.entries()) {
          dates.push({
            date: dateStr,
            label: this.formatDate(dateStr),
            weekday: this.formatWeekday(dateStr),
            count,
          });
        }
        dates.sort((a, b) => a.date.localeCompare(b.date));
        this.rebookAvailableDates.set(dates);
        const filtered = slots.filter((slot) => slot.appointmentDate === date && !!slot.doctorId && slot.remainingCapacity > 0)
          .filter((slot) => !(slot.appointmentDate === appointment.appointmentDate && slot.startTime === appointment.startTime));
        this.rebookSlots.set(filtered);
        this.rebookStartTime.set(filtered[0]?.startTime ?? '');
        this.rebookSlotsLoading.set(false);
      },
      error: (response) => {
        if (requestId !== this.rebookSlotRequestId) return;
        this.rebookSlotsLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected submitRebook(): void {
    if (this.rebookLoading()) return;
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
    if (!this.adjustmentLoading()) {
      this.adjustmentDoctorRequestId++;
      this.adjustmentTicket.set(null);
      this.doctors.set([]);
    }
  }

  protected adjustQueue(action: 'MOVE' | 'SET_PRIORITY' | 'CLEAR_PRIORITY'): void {
    if (this.adjustmentLoading()) return;
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
    if (action === 'MOVE' && doctor && doctor.shiftStatus && doctor.shiftStatus !== 'ACTIVE') {
      this.error.set('Bác sĩ đích ngoài ca trực, không thể chuyển.');
      return;
    }
    const selectedSlot = this.adjustmentSelectedSlot();
    if (action === 'MOVE' && doctor && selectedSlot) {
      const slot = doctor.slots?.find(s => s.startTime.slice(0,5) === selectedSlot);
      if (slot && slot.remaining === 0) {
        this.error.set('Khung giờ đã hết chỗ, chọn khung giờ khác.');
        return;
      }
    }
    this.adjustmentLoading.set(true);
    this.error.set('');
    this.authApi.adjustQueueTicket(appointment.queueTicketId, {
      action,
      targetDoctorId: action === 'MOVE' ? doctor?.staffId : undefined,
      targetRoomCode: action === 'MOVE' ? doctor?.roomCode : undefined,
      targetSpecialty: action === 'MOVE' ? doctor?.specialty : undefined,
      reason,
      targetStartTime: action === 'MOVE' ? (this.adjustmentSelectedSlot() || undefined) : undefined,
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
    this.walkInProfileRequestId++;
    this.walkInSlotRequestId++;
    this.walkInOpen.set(true);
    this.walkInRequestKey.set('');
    this.walkInPhone.set('');
    this.walkInDate.set(clinicTodayIso());
    this.walkInProfiles.set([]);
    this.walkInProfilesLoading.set(false);
    this.walkInProfileId.set('');
    this.walkInSpecialty.set('');
    this.walkInSlots.set([]);
    this.walkInSlotsLoading.set(false);
    this.walkInStartTime.set('');
    this.walkInReason.set('');
    this.walkInExceptionReason.set('');
    this.walkInRegistration.set(false);
    this.walkInTemporaryProfile.set(false);
    this.walkInNeedsPasswordChange.set(false);
    this.walkInAccountLocked.set(false);
    this.pendingAccountNeedsOtp.set(false);
    this.activationOtpRequested.set(false);
    this.activationOtpVerified.set(false);
    this.walkInProfileNeedsCompletion.set(false);
    this.completionProfile.set(null);
    this.walkInOtp.set('');
    this.registrationLoading.set(false);
    this.activationLoading.set(false);
    this.identityMutationLoading.set(false);
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
    this.error.set('');
    this.notice.set('');
    if (this.walkInSpecialties().length === 0) {
      this.authApi.getSpecialties().subscribe({
        next: (specialties) => this.walkInSpecialties.set(specialties),
        error: (response) => this.handleError(response),
      });
    }
  }

  protected closeWalkIn(): void {
    if (!this.walkInLoading() && !this.identityMutationLoading()) {
      this.walkInProfileRequestId++;
      this.walkInSlotRequestId++;
      this.walkInOtpRequestId++;
      this.walkInProfilesLoading.set(false);
      this.walkInSlotsLoading.set(false);
      this.registrationLoading.set(false);
      this.walkInRequestKey.set('');
      this.walkInOpen.set(false);
      this.notice.set('');
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
    if (this.registrationLoading() || this.registrationOtpSent()) return;
    const phone = this.walkInPhone().trim();
    if (!/^0\d{9}$/.test(phone)) {
      this.error.set('Nhập số điện thoại hợp lệ trước khi gửi OTP.');
      return;
    }
    const requestId = ++this.walkInOtpRequestId;
    this.registrationLoading.set(true);
    this.error.set('');
    this.authApi.requestReceptionPatientOtp(phone).subscribe({
      next: () => {
        if (requestId !== this.walkInOtpRequestId || this.walkInPhone().trim() !== phone) return;
        this.registrationLoading.set(false);
        this.registrationOtpSent.set(true);
        this.notice.set('Đã gửi OTP. Mã có hiệu lực trong 5 phút.');
      },
      error: (response) => {
        if (requestId !== this.walkInOtpRequestId || this.walkInPhone().trim() !== phone) return;
        this.registrationLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected requestPendingActivationOtp(): void {
    if (this.registrationLoading()) return;
    const phone = this.walkInPhone().trim();
    if (!/^0\d{9}$/.test(phone)) {
      this.error.set('Nhập số điện thoại hợp lệ trước khi gửi OTP.');
      return;
    }
    const requestId = ++this.walkInOtpRequestId;
    this.registrationLoading.set(true);
    this.error.set('');
    this.authApi.requestReceptionPatientOtp(phone).subscribe({
      next: () => {
        if (requestId !== this.walkInOtpRequestId || this.walkInPhone().trim() !== phone) return;
        this.registrationLoading.set(false);
        this.activationOtpRequested.set(true);
        this.activationOtpVerified.set(false);
        this.walkInOtp.set('');
        this.notice.set('Đã gửi mã OTP. Nhập mã mới để xác minh tài khoản.');
      },
      error: (response) => {
        if (requestId !== this.walkInOtpRequestId || this.walkInPhone().trim() !== phone) return;
        this.registrationLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected verifyPendingActivationOtp(): void {
    if (this.registrationLoading()) return;
    const phone = this.walkInPhone().trim();
    const code = this.walkInOtp().trim();
    if (!this.activationOtpRequested() || !/^\d{6}$/.test(code)) {
      this.error.set('Gửi OTP và nhập mã xác minh gồm 6 chữ số.');
      return;
    }
    const requestId = ++this.walkInOtpRequestId;
    this.registrationLoading.set(true);
    this.error.set('');
    this.authApi.verifySmsOtp(phone, 'REGISTRATION', code).subscribe({
      next: () => {
        if (requestId !== this.walkInOtpRequestId || this.walkInPhone().trim() !== phone) return;
        this.registrationLoading.set(false);
        this.activationOtpVerified.set(true);
        this.notice.set('Số điện thoại đã được xác minh. Bạn có thể đặt mật khẩu mới.');
      },
      error: (response) => {
        if (requestId !== this.walkInOtpRequestId || this.walkInPhone().trim() !== phone) return;
        this.registrationLoading.set(false);
        this.activationOtpVerified.set(false);
        this.handleError(response);
      },
    });
  }

  protected submitRegistration(): void {
    if (this.registrationLoading() || this.identityMutationLoading()) return;
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
    this.identityMutationLoading.set(true);
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
          this.identityMutationLoading.set(false);
          this.walkInRegistration.set(false);
          this.walkInTemporaryProfile.set(false);
          this.walkInProfiles.set([profile]);
          this.walkInProfileId.set(profile.id);
          this.notice.set(`Đã tạo hồ sơ tạm cho ${profile.fullName}.`);
        },
        error: (response) => {
          this.registrationLoading.set(false);
          this.identityMutationLoading.set(false);
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
        this.identityMutationLoading.set(false);
        this.walkInRegistration.set(false);
        this.walkInNeedsPasswordChange.set(true);
        this.pendingAccountNeedsOtp.set(false);
        this.activationOtpRequested.set(true);
        this.activationOtpVerified.set(true);
        this.notice.set(`Đã tạo tài khoản cho ${response.fullName}.`);
      },
      error: (response) => {
        this.registrationLoading.set(false);
        this.identityMutationLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected activatePendingAccount(): void {
    if (this.activationLoading() || this.identityMutationLoading()) return;
    const phone = this.walkInPhone().trim();
    const password = this.activationPassword();
    if (!/^0\d{9}$/.test(phone) || password.length < 8 || password.length > 64) {
      this.error.set('Nhập mật khẩu mới từ 8 đến 64 ký tự.');
      return;
    }
    if (password !== this.activationConfirmPassword()) {
      this.error.set('Hai lần nhập mật khẩu mới không giống nhau.');
      return;
    }
    if (this.pendingAccountNeedsOtp() && !this.activationOtpVerified()) {
      this.error.set('Gửi và xác minh OTP mới trước khi đặt mật khẩu.');
      return;
    }
    this.activationLoading.set(true);
    this.identityMutationLoading.set(true);
    this.error.set('');
    this.authApi.activateReceptionPatientAccount(phone, password, this.activationConfirmPassword()).subscribe({
      next: () => {
        this.activationLoading.set(false);
        this.identityMutationLoading.set(false);
        this.activationPassword.set('');
        this.activationConfirmPassword.set('');
        this.walkInNeedsPasswordChange.set(false);
        this.notice.set('Đã kích hoạt tài khoản. Có thể tiếp tục tiếp nhận.');
        this.loadWalkInProfiles();
      },
      error: (response) => {
        this.activationLoading.set(false);
        this.identityMutationLoading.set(false);
        if (response?.error?.code === 'ACTIVATION_OTP_REQUIRED') {
          this.activationOtpVerified.set(false);
        }
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
    const requestId = ++this.walkInProfileRequestId;
    this.walkInProfilesLoading.set(true);
    this.error.set('');
    this.authApi.getReceptionProfiles(phone).subscribe({
      next: (profiles) => {
        if (requestId !== this.walkInProfileRequestId || this.walkInPhone().trim() !== phone) return;
        this.walkInProfiles.set(profiles);
        const selected = profiles.find((profile) => profile.primaryProfile) ?? profiles[0];
        const accountLocked = profiles.some((profile) => profile.accountStatus === 'LOCKED');
        const passwordChangeRequired = profiles.some((profile) => profile.mustChangePassword);
        this.walkInAccountLocked.set(accountLocked);
        this.walkInProfileId.set(selected?.id ?? '');
        this.prepareProfileCompletion(selected);
        if (accountLocked) {
          this.walkInNeedsPasswordChange.set(false);
          this.pendingAccountNeedsOtp.set(false);
          this.activationOtpRequested.set(false);
          this.activationOtpVerified.set(false);
          this.notice.set('Tài khoản đang bị khóa; cần mở khóa trước khi tiếp nhận.');
        } else if (passwordChangeRequired) {
          this.walkInNeedsPasswordChange.set(true);
          this.pendingAccountNeedsOtp.set(true);
          this.activationOtpRequested.set(false);
          this.activationOtpVerified.set(false);
          this.notice.set('Người bệnh chưa kích hoạt tài khoản. Hãy đặt mật khẩu mới trước khi tiếp tục.');
        } else {
          this.walkInNeedsPasswordChange.set(false);
          this.pendingAccountNeedsOtp.set(false);
          this.activationOtpRequested.set(false);
          this.activationOtpVerified.set(false);
        }
        this.walkInProfilesLoading.set(false);
      },
      error: (response) => {
        if (requestId !== this.walkInProfileRequestId || this.walkInPhone().trim() !== phone) return;
        this.walkInProfilesLoading.set(false);
        if (response.status === 404) {
        this.walkInRegistration.set(true);
          this.walkInTemporaryProfile.set(false);
          this.walkInAccountLocked.set(false);
          this.pendingAccountNeedsOtp.set(false);
          this.activationOtpRequested.set(false);
          this.activationOtpVerified.set(false);
          this.error.set('');
          return;
        }
        this.handleError(response);
      },
    });
  }

  protected onWalkInPhoneChange(phone: string): void {
    if (phone === this.walkInPhone() || this.identityMutationLoading()) return;
    this.walkInPhone.set(phone);
    this.walkInProfileRequestId++;
    this.walkInOtpRequestId++;
    this.walkInProfilesLoading.set(false);
    this.registrationLoading.set(false);
    this.walkInRegistration.set(false);
    this.walkInTemporaryProfile.set(false);
    this.walkInProfiles.set([]);
    this.walkInProfileId.set('');
    this.walkInProfileNeedsCompletion.set(false);
    this.completionProfile.set(null);
    this.walkInAccountLocked.set(false);
    this.walkInNeedsPasswordChange.set(false);
    this.pendingAccountNeedsOtp.set(false);
    this.activationOtpRequested.set(false);
    this.activationOtpVerified.set(false);
    this.registrationOtpSent.set(false);
    this.walkInOtp.set('');
    this.notice.set('');
  }

  protected loadWalkInSlots(): void {
    const specialty = this.walkInSpecialty();
    const date = this.walkInDate();
    const requestId = ++this.walkInSlotRequestId;
    if (!specialty || !date) {
      this.walkInSlots.set([]);
      this.walkInStartTime.set('');
      this.walkInAvailableDates.set([]);
      this.walkInSlotsLoading.set(false);
      return;
    }
    this.walkInSlotsLoading.set(true);
    this.walkInAvailableDates.set([]);
    this.walkInSlots.set([]);
    this.walkInStartTime.set('');
    const fromDate = date < clinicTodayIso() ? date : clinicTodayIso();
    const toDate = this.addDays(fromDate, 30);
    this.authApi.getAppointmentSlots(specialty, fromDate, toDate).subscribe({
      next: (slots) => {
        if (requestId !== this.walkInSlotRequestId
          || this.walkInSpecialty() !== specialty
          || this.walkInDate() !== date) return;
        const today = clinicTodayIso();
        const dateMap = new Map<string, number>();
        for (const slot of slots) {
          if (!slot.doctorId) continue;
          const isTodaySlot = slot.appointmentDate === today;
          if (isTodaySlot || slot.remainingCapacity > 0) {
            dateMap.set(slot.appointmentDate, (dateMap.get(slot.appointmentDate) || 0) + 1);
          }
        }
        const dates: { date: string; label: string; weekday: string; count: number }[] = [];
        for (const [dateStr, count] of dateMap.entries()) {
          dates.push({
            date: dateStr,
            label: this.formatDate(dateStr),
            weekday: this.formatWeekday(dateStr),
            count,
          });
        }
        dates.sort((a, b) => a.date.localeCompare(b.date));
        this.walkInAvailableDates.set(dates);

        const appointmentIsToday = date === today;
        const available = slots.filter((slot) => slot.appointmentDate === date && !!slot.doctorId
          && (appointmentIsToday || slot.remainingCapacity > 0));
        this.walkInSlots.set(available);
        this.walkInStartTime.set(available[0]?.startTime ?? '');
        this.walkInSlotsLoading.set(false);
      },
      error: (response) => {
        if (requestId !== this.walkInSlotRequestId
          || this.walkInSpecialty() !== specialty
          || this.walkInDate() !== date) return;
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
        this.notice.set(appointment.queueNumber == null
          ? `Đã tạo lịch ${appointment.appointmentCode} cho ${appointment.patientName}.`
          : `Đã tạo lịch và cấp số ${String(appointment.queueNumber).padStart(2, '0')} cho ${appointment.patientName}.`);
      },
      error: (response) => {
        this.walkInLoading.set(false);
        this.handleError(response);
      },
    });
  }

  private prepareProfileCompletion(profile: ReceptionPatientProfile | undefined): void {
    this.registrationAddressRequestId++;
    this.registrationAddressLoading.set(false);
    this.registrationDistricts.set([]);
    this.registrationWards.set([]);
    this.completionProfile.set(profile ?? null);
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
    this.registrationStreetAddress.set(streetAddressOnly(profile.streetAddress, [
      profile.wardName,
      profile.districtName,
      profile.provinceName,
    ]));
    this.walkInProfileNeedsCompletion.set([profile.fullName, profile.dateOfBirth, profile.gender]
      .some((value) => !value || !String(value).trim()));
  }

  protected selectWalkInProfile(profileId: string): void {
    this.walkInProfileId.set(profileId);
    this.prepareProfileCompletion(this.walkInProfiles().find((profile) => profile.id === profileId));
  }

  protected submitProfileCompletion(): void {
    if (this.registrationLoading()) return;
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
      address: this.completedAddress(),
      provinceCode: this.registrationProvinceCode(), provinceName: this.registrationProvinceName(),
      districtCode: this.registrationDistrictCode(), districtName: this.registrationDistrictName(),
      wardCode: this.registrationWardCode(), wardName: this.registrationWardName(),
      streetAddress: this.registrationStreetAddress().trim(),
    }).subscribe({
      next: (profile) => {
        this.registrationLoading.set(false);
        this.walkInProfiles.update((items) => items.map((item) => item.id === profile.id ? profile : item));
        this.walkInProfileNeedsCompletion.set(false);
        this.notice.set('');
      },
      error: (response) => {
        this.registrationLoading.set(false);
        this.handleError(response);
      },
    });
  }

  protected registrationProvinceChanged(): void {
    const requestId = ++this.registrationAddressRequestId;
    const provinceCode = this.registrationProvinceCode();
    const province = this.registrationProvinces().find((item) => String(item.code) === this.registrationProvinceCode());
    this.registrationProvinceName.set(province?.name ?? '');
    this.registrationDistrictCode.set('');
    this.registrationDistrictName.set('');
    this.registrationWardCode.set('');
    this.registrationWardName.set('');
    this.registrationDistricts.set([]);
    this.registrationWards.set([]);
    if (provinceCode) {
      this.registrationAddressLoading.set(true);
      this.addressApi.getDistricts(provinceCode).subscribe({
        next: (items) => {
          if (requestId === this.registrationAddressRequestId && provinceCode === this.registrationProvinceCode()) {
            this.registrationDistricts.set(items);
          }
        },
        complete: () => {
          if (requestId === this.registrationAddressRequestId) this.registrationAddressLoading.set(false);
        },
        error: () => {
          if (requestId === this.registrationAddressRequestId) this.registrationAddressLoading.set(false);
        },
      });
    } else {
      this.registrationAddressLoading.set(false);
    }
  }

  protected registrationDistrictChanged(): void {
    const requestId = ++this.registrationAddressRequestId;
    const districtCode = this.registrationDistrictCode();
    const district = this.registrationDistricts().find((item) => String(item.code) === this.registrationDistrictCode());
    this.registrationDistrictName.set(district?.name ?? '');
    this.registrationWardCode.set('');
    this.registrationWardName.set('');
    this.registrationWards.set([]);
    if (districtCode) {
      this.registrationAddressLoading.set(true);
      this.addressApi.getWards(districtCode).subscribe({
        next: (items) => {
          if (requestId === this.registrationAddressRequestId && districtCode === this.registrationDistrictCode()) {
            this.registrationWards.set(items);
          }
        },
        complete: () => {
          if (requestId === this.registrationAddressRequestId) this.registrationAddressLoading.set(false);
        },
        error: () => {
          if (requestId === this.registrationAddressRequestId) this.registrationAddressLoading.set(false);
        },
      });
    } else {
      this.registrationAddressLoading.set(false);
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
    if (appointment.status === 'CANCELLED') {
      return 'erp-badge-danger';
    }
    if (appointment.status === 'ABSENT' || appointment.status === 'NOT_PERFORMED' || appointment.queueStatus === 'LEFT_BEFORE_EXAM' || appointment.queueStatus === 'SKIPPED') {
      return 'erp-badge-warning';
    }
    if (appointment.queueStatus === 'COMPLETED' || appointment.status === 'COMPLETED') {
      return 'erp-badge-success';
    }
    if (appointment.queueStatus === 'CALLED' || appointment.queueStatus === 'IN_SERVICE' || appointment.queueStatus === 'WAITING' || appointment.status === 'CHECKED_IN') {
      return 'erp-badge-info';
    }
    return 'erp-badge-info';
  }

  protected queueDotClass(appointment: ReceptionAppointmentResponse): string {
    if (appointment.status === 'CANCELLED') {
      return 'erp-dot-danger';
    }
    if (appointment.status === 'ABSENT' || appointment.status === 'NOT_PERFORMED' || appointment.queueStatus === 'LEFT_BEFORE_EXAM' || appointment.queueStatus === 'SKIPPED') {
      return 'erp-dot-warning';
    }
    if (appointment.queueStatus === 'COMPLETED' || appointment.status === 'COMPLETED') {
      return 'erp-dot-success';
    }
    if (appointment.queueStatus === 'CALLED' || appointment.queueStatus === 'IN_SERVICE' || appointment.queueStatus === 'WAITING' || appointment.status === 'CHECKED_IN') {
      return 'erp-dot-info';
    }
    return 'erp-dot-info';
  }

  private noticeTimer: ReturnType<typeof setTimeout> | null = null;
  private setNotice(msg: string): void {
    this.notice.set(msg);
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
    if (msg) {
      this.noticeTimer = setTimeout(() => {
        if (this.notice() === msg) this.notice.set('');
      }, 4000);
    }
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

  protected completionFieldLocked(field: keyof ReceptionPatientProfile): boolean {
    const value = this.completionProfile()?.[field];
    return value !== null && value !== undefined && String(value).trim().length > 0;
  }

  protected walkInIsToday(): boolean {
    return this.walkInDate() === clinicTodayIso();
  }

  protected formatDate(value?: string | null): string {
    return formatClinicPadDate(value) || '—';
  }

  protected formatWeekday(value: string): string {
    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(year, month - 1, day);
    const weekdays = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
    return weekdays[date.getDay()] ?? '';
  }

  protected addDays(isoDate: string, days: number): string {
    const [year, month, day] = isoDate.split('-').map(Number);
    const d = new Date(year, month - 1, day);
    d.setDate(d.getDate() + days);
    return this.toIsoDate(d);
  }

  private completedAddress(): string {
    const parts = [
      this.registrationStreetAddress().trim(),
      this.registrationWardName().trim(),
      this.registrationDistrictName().trim(),
      this.registrationProvinceName().trim(),
    ].filter((part, index, items) => part && items.indexOf(part) === index);
    return parts.join(', ') || this.registrationAddress().trim();
  }
}

function streetAddressOnly(value: string | null | undefined, administrativeNames: Array<string | null | undefined>): string {
  const normalizedNames = new Set(administrativeNames.filter(Boolean).map((name) => name!.trim().toLocaleLowerCase('vi')));
  return (value ?? '')
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part && !normalizedNames.has(part.toLocaleLowerCase('vi')))
    .join(', ');
}

function receptionTab(value: unknown): ReceptionTab {
  return typeof value === 'string' && ['overview', 'search', 'intake', 'queue', 'exceptions', 'profiles'].includes(value)
    ? value as ReceptionTab
    : 'overview';
}

function receptionPageTitle(tab: ReceptionTab): string {
  switch (tab) {
    case 'search': return 'Tra cứu lịch';
    case 'intake': return 'Tiếp nhận tại quầy';
    case 'queue': return 'Hàng đợi tiếp nhận';
    case 'exceptions': return 'Ngoại lệ cần xử lý';
    case 'profiles': return 'Hồ sơ người bệnh';
    default: return 'Tổng quan tiếp nhận';
  }
}
