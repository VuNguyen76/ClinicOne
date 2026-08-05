import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AppointmentSlotResponse, AuthApiService, apiErrorMessage, PatientProfileItem, SpecialtyOption } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';

type BookingStep = 1 | 2 | 3;

interface DateOption {
  iso: string;
  day: string;
  weekday: string;
  month: string;
}

interface TimeSlot {
  label: string;
  value: string;
  period: 'Buổi sáng' | 'Buổi chiều';
  doctorName: string;
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
  protected readonly selectedDate = signal('');
  protected readonly selectedSlot = signal('');
  protected readonly dates = this.buildDates();
  protected readonly specialties = signal<SpecialtyOption[]>([]);
  protected readonly specialtiesLoading = signal(true);
  protected readonly availableSlots = signal<TimeSlot[]>([]);
  protected readonly slotsLoading = signal(false);
  protected profiles: PatientProfileItem[] = [];
  protected profilesLoading = true;
  protected busy = false;
  protected error = '';

  protected readonly form = this.formBuilder.nonNullable.group({
    specialty: ['', [Validators.required, Validators.maxLength(120)]],
    doctorName: ['Bác sĩ chuyên khoa', [Validators.required, Validators.maxLength(120)]],
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

  protected chooseSpecialty(specialty: SpecialtyOption): void {
    this.clearError();
    this.selectedSpecialty.set(specialty.name);
    this.form.controls.specialty.setValue(specialty.name);
    this.form.controls.appointmentDate.reset('');
    this.form.controls.startTime.reset('');
    this.selectedDate.set('');
    this.selectedSlot.set('');
    this.step.set(2);
  }

  protected chooseDate(date: DateOption): void {
    this.clearError();
    this.selectedDate.set(date.iso);
    this.selectedSlot.set('');
    this.availableSlots.set([]);
    this.form.controls.appointmentDate.setValue(date.iso);
    this.form.controls.startTime.reset('');
    this.slotsLoading.set(true);
    this.authApi.getAppointmentSlots(this.form.controls.specialty.value, date.iso, date.iso).subscribe({
      next: (slots) => {
        this.availableSlots.set(slots.map((slot) => this.toTimeSlot(slot)));
        this.slotsLoading.set(false);
      },
      error: (response) => { this.slotsLoading.set(false); this.handleAuthError(response); },
    });
  }

  protected chooseSlot(slot: TimeSlot): void {
    this.clearError();
    this.selectedSlot.set(slot.value);
    this.form.controls.startTime.setValue(slot.value);
    this.form.controls.doctorName.setValue(slot.doctorName);
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
    this.step.set(3);
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
    this.authApi.createAppointment({ ...value, profileId: value.profileId || undefined }).subscribe({
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
    return { label: `${startTime} - ${endTime}`, value: startTime, period: Number(startTime.slice(0, 2)) < 12 ? 'Buổi sáng' : 'Buổi chiều', doctorName: slot.doctorName };
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

  private buildDates(): DateOption[] {
    const dates: DateOption[] = [];
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    for (let offset = 0; offset < 21; offset += 1) {
      const date = new Date(start);
      date.setDate(start.getDate() + offset);
      dates.push({
        iso: this.toIsoDate(date),
        day: String(date.getDate()).padStart(2, '0'),
        month: String(date.getMonth() + 1).padStart(2, '0'),
        weekday: new Intl.DateTimeFormat('vi-VN', { weekday: 'short' }).format(date).replace('.', ''),
      });
    }
    return dates;
  }

  private toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
