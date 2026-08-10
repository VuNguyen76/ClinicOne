import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AppointmentSlotResponse, AuthApiService, apiErrorMessage, ClinicServiceResponse, PatientProfileItem, SpecialtyOption } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';

type BookingStep = 1 | 2 | 3;

interface DateOption {
  iso: string;
  day: string;
  weekday: string;
  month: string;
  inCurrentMonth: boolean;
}

interface TimeSlot {
  label: string;
  key: string;
  startTime: string;
  period: 'Buổi sáng' | 'Buổi chiều';
  doctorName: string;
  doctorId: string | null;
  roomCode: string | null;
}

@Component({
  selector: 'app-booking',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './booking.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Booking implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly today = this.toIsoDate(new Date());
  protected readonly step = signal<BookingStep>(1);
  protected readonly specialtySearch = signal('');
  protected readonly selectedSpecialty = signal('');
  protected readonly selectedClinicService = signal<ClinicServiceResponse | null>(null);
  protected readonly selectedDate = signal('');
  protected readonly selectedSlot = signal('');
  protected readonly holdId = signal<string | null>(null);
  protected readonly holdBusy = signal(false);
  protected readonly calendarMonth = signal(this.startOfMonth(new Date()));
  protected readonly dates = signal(this.buildMonthDates(this.calendarMonth()));
  protected readonly specialties = signal<SpecialtyOption[]>([]);
  protected readonly specialtiesLoading = signal(true);
  protected readonly clinicServices = signal<ClinicServiceResponse[]>([]);
  protected readonly clinicServicesLoading = signal(true);
  protected readonly availableSlots = signal<TimeSlot[]>([]);
  protected readonly slotsLoading = signal(false);
  protected profiles: PatientProfileItem[] = [];
  protected profilesLoading = true;
  protected busy = false;
  protected error = '';

  protected readonly form = this.formBuilder.nonNullable.group({
    specialty: ['', [Validators.required, Validators.maxLength(120)]],
    doctorName: ['Bác sĩ chuyên khoa', [Validators.required, Validators.maxLength(120)]],
    doctorId: [''],
    appointmentDate: ['', [Validators.required]],
    startTime: ['', [Validators.required]],
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
    profileId: [''],
  });

  ngOnInit(): void {
    this.authApi.getSpecialties().subscribe({
      next: (specialties) => { this.specialties.set(specialties); this.specialtiesLoading.set(false); },
      error: (response) => { this.specialtiesLoading.set(false); this.handleAuthError(response); },
    });
    this.authApi.getActiveClinicServices().subscribe({
      next: (services) => { this.clinicServices.set(services); this.clinicServicesLoading.set(false); },
      error: (response) => { this.clinicServicesLoading.set(false); this.handleAuthError(response); },
    });
    this.authApi.getPatientProfiles().subscribe({
      next: (profiles) => {
        this.profiles = profiles;
        const primary = profiles.find((profile) => profile.primaryProfile) ?? profiles[0];
        if (primary) this.form.controls.profileId.setValue(primary.id);
        this.profilesLoading = false;
      },
      error: (response) => {
        this.profilesLoading = false;
        this.handleAuthError(response);
      },
    });
  }

  protected filteredSpecialties(): SpecialtyOption[] {
    const query = this.specialtySearch().trim().toLocaleLowerCase('vi-VN');
    return query ? this.specialties().filter((item) => `${item.name} ${item.description}`.toLocaleLowerCase('vi-VN').includes(query)) : this.specialties();
  }

  protected filteredClinicServices(): ClinicServiceResponse[] {
    const query = this.specialtySearch().trim().toLocaleLowerCase('vi-VN');
    return query
      ? this.clinicServices().filter((item) => `${item.name} ${item.specialty} ${item.visitType}`.toLocaleLowerCase('vi-VN').includes(query))
      : this.clinicServices();
  }

  protected chooseClinicService(service: ClinicServiceResponse): void {
    this.clearError();
    this.selectedClinicService.set(service);
    this.selectedSpecialty.set(service.specialty);
    this.form.controls.specialty.setValue(service.specialty);
    this.form.controls.appointmentDate.reset('');
    this.form.controls.startTime.reset('');
    this.selectedDate.set('');
    this.selectedSlot.set('');
    this.holdId.set(null);
    this.monthSlots.set([]);
    this.step.set(2);
    this.loadMonthAvailability();
  }

  protected chooseSpecialty(specialty: SpecialtyOption): void {
    this.clearError();
    this.selectedClinicService.set(null);
    this.selectedSpecialty.set(specialty.name);
    this.form.controls.specialty.setValue(specialty.name);
    this.form.controls.appointmentDate.reset('');
    this.form.controls.startTime.reset('');
    this.selectedDate.set('');
    this.selectedSlot.set('');
    this.holdId.set(null);
    this.monthSlots.set([]);
    this.step.set(2);
    this.loadMonthAvailability();
  }

  protected readonly monthSlots = signal<AppointmentSlotResponse[]>([]);

  protected monthLabel(): string {
    return new Intl.DateTimeFormat('vi-VN', { month: 'long', year: 'numeric' }).format(this.calendarMonth());
  }

  protected isPreviousMonthDisabled(): boolean {
    return this.calendarMonth().getTime() <= this.startOfMonth(new Date()).getTime();
  }

  protected previousMonth(): void {
    if (this.isPreviousMonthDisabled()) return;
    const month = this.calendarMonth();
    this.setCalendarMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1));
  }

  protected nextMonth(): void {
    const month = this.calendarMonth();
    this.setCalendarMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1));
  }

  protected hasAvailability(date: DateOption): boolean {
    return this.monthSlots().some((slot) => slot.appointmentDate === date.iso);
  }

  protected chooseDate(date: DateOption): void {
    if (!date.inCurrentMonth || date.iso < this.today) return;
    this.clearError();
    this.selectedDate.set(date.iso);
    this.selectedSlot.set('');
    this.availableSlots.set([]);
    this.form.controls.appointmentDate.setValue(date.iso);
    this.form.controls.startTime.reset('');
    const slotsForDate = this.monthSlots().filter((slot) => slot.appointmentDate === date.iso);
    this.availableSlots.set(slotsForDate.map((slot) => this.toTimeSlot(slot)));
  }

  protected chooseSlot(slot: TimeSlot): void {
    this.clearError();
    if (this.selectedSlot() !== slot.key) {
      this.holdId.set(null);
    }
    this.selectedSlot.set(slot.key);
    this.form.controls.startTime.setValue(slot.startTime);
    this.form.controls.doctorName.setValue(slot.doctorName);
    this.form.controls.doctorId.setValue(slot.doctorId ?? '');
  }

  protected selectedSlotLabel(): string {
    const selected = this.availableSlots().find((slot) => slot.key === this.selectedSlot());
    return selected ? `${selected.label} · ${selected.doctorName}${selected.roomCode ? ` · ${selected.roomCode}` : ''}` : '';
  }

  protected selectedSlotDetails(): TimeSlot | null {
    return this.availableSlots().find((slot) => slot.key === this.selectedSlot()) ?? null;
  }

  protected slotsFor(period: TimeSlot['period']): TimeSlot[] {
    return this.availableSlots().filter((slot) => slot.period === period);
  }

  protected continueToDetails(): void {
    this.clearError();
    if (!this.selectedDate() || !this.selectedSlot()) {
      this.error = 'Vui lòng chọn ngày và khung giờ khám.';
      return;
    }
    if (this.holdId()) {
      this.step.set(3);
      return;
    }
    const value = this.form.getRawValue();
    this.holdBusy.set(true);
    this.authApi.holdAppointmentSlot({
      specialty: value.specialty,
      doctorName: value.doctorName,
      doctorId: value.doctorId || undefined,
      appointmentDate: value.appointmentDate,
      startTime: value.startTime,
      serviceId: this.selectedClinicService()?.id,
    }).subscribe({
      next: (hold) => {
        this.holdBusy.set(false);
        this.holdId.set(hold.id);
        this.step.set(3);
      },
      error: (response) => {
        this.holdBusy.set(false);
        this.handleAuthError(response);
      },
    });
  }

  protected back(): void {
    this.clearError();
    if (this.step() === 1) {
      void this.router.navigateByUrl('/dashboard');
      return;
    }
    this.step.update((current) => (current === 3 ? 2 : 1));
  }

  protected submit(): void {
    this.clearError();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error = 'Vui lòng chọn người đi khám và nhập lý do khám.';
      return;
    }

    this.busy = true;
    const value = this.form.getRawValue();
    this.authApi.createAppointment({ ...value, profileId: value.profileId || undefined,
      holdId: this.holdId() ?? undefined, serviceId: this.selectedClinicService()?.id }).subscribe({
      next: () => void this.router.navigateByUrl('/dashboard'),
      error: (response) => {
        this.busy = false;
        this.handleAuthError(response);
      },
    });
  }

  protected dateLabel(date: DateOption): string {
    return `${date.weekday}, ${date.day}/${date.month}`;
  }

  protected selectedProfileName(): string {
    const profile = this.profiles.find((item) => item.id === this.form.controls.profileId.value);
    return profile?.fullName ?? 'Hồ sơ tài khoản';
  }

  protected formatDate(value: string): string {
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN').format(new Date(year, month - 1, day));
  }

  private clearError(): void {
    this.error = '';
  }

  private toTimeSlot(slot: AppointmentSlotResponse): TimeSlot {
    const startTime = slot.startTime.slice(0, 5);
    const endTime = slot.endTime.slice(0, 5);
    return { label: `${startTime} - ${endTime}`, key: `${slot.doctorId ?? slot.doctorName}|${startTime}`, startTime, period: Number(startTime.slice(0, 2)) < 12 ? 'Buổi sáng' : 'Buổi chiều', doctorName: slot.doctorName, doctorId: slot.doctorId ?? null, roomCode: slot.roomCode ?? null };
  }

  private handleAuthError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOnePatientName');
      void this.router.navigateByUrl('/login');
      return;
    }
    this.error = apiErrorMessage(response);
  }

  private setCalendarMonth(month: Date): void {
    this.calendarMonth.set(this.startOfMonth(month));
    this.dates.set(this.buildMonthDates(this.calendarMonth()));
    this.selectedDate.set('');
    this.selectedSlot.set('');
    this.holdId.set(null);
    this.availableSlots.set([]);
    this.form.controls.appointmentDate.reset('');
    this.form.controls.startTime.reset('');
    this.monthSlots.set([]);
    this.loadMonthAvailability();
  }

  private loadMonthAvailability(): void {
    const specialty = this.form.controls.specialty.value;
    if (!specialty) return;
    const month = this.calendarMonth();
    const monthStart = this.toIsoDate(month);
    const monthEnd = this.toIsoDate(new Date(month.getFullYear(), month.getMonth() + 1, 0));
    this.slotsLoading.set(true);
    this.authApi.getAppointmentSlots(specialty, monthStart, monthEnd, this.selectedClinicService()?.id).subscribe({
      next: (slots) => { this.monthSlots.set(slots); this.slotsLoading.set(false); },
      error: (response) => { this.slotsLoading.set(false); this.handleAuthError(response); },
    });
  }

  private buildMonthDates(month: Date): DateOption[] {
    const dates: DateOption[] = [];
    const firstDay = this.startOfMonth(month);
    const mondayOffset = (firstDay.getDay() + 6) % 7;
    const gridStart = new Date(firstDay);
    gridStart.setDate(firstDay.getDate() - mondayOffset);
    for (let offset = 0; offset < 42; offset += 1) {
      const date = new Date(gridStart);
      date.setDate(gridStart.getDate() + offset);
      dates.push({
        iso: this.toIsoDate(date),
        day: String(date.getDate()).padStart(2, '0'),
        month: String(date.getMonth() + 1).padStart(2, '0'),
        weekday: new Intl.DateTimeFormat('vi-VN', { weekday: 'short' }).format(date).replace('.', ''),
        inCurrentMonth: date.getMonth() === month.getMonth() && date.getFullYear() === month.getFullYear(),
      });
    }
    return dates;
  }

  private startOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }

  private toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
