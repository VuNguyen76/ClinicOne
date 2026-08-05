import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, apiErrorMessage, PatientProfileItem } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';

type BookingStep = 1 | 2 | 3;

interface SpecialtyOption {
  name: string;
  description: string;
}

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
  protected readonly specialties: SpecialtyOption[] = [
    { name: 'Khám Tổng Quát', description: 'Khám và tầm soát tổng quát, được hướng dẫn tới đúng chuyên khoa khi cần.' },
    { name: 'Khám Tiêu Hoá - Gan Mật', description: 'Đau bụng, ợ hơi, trào ngược, rối loạn tiêu hoá hoặc bệnh lý gan mật.' },
    { name: 'Khám Thần Kinh', description: 'Đau đầu, chóng mặt, mất ngủ, tê bì hoặc đau cổ vai gáy.' },
    { name: 'Khám Xương Khớp', description: 'Đau khớp, đau lưng, chấn thương thể thao hoặc hạn chế vận động.' },
    { name: 'Khám Da Liễu', description: 'Mụn, ngứa, nổi mẩn, nấm da, rụng tóc hoặc bất thường trên da.' },
    { name: 'Khám Phụ Khoa', description: 'Tư vấn và thăm khám các vấn đề phụ khoa thường gặp.' },
    { name: 'Khám Hô Hấp', description: 'Ho kéo dài, khó thở, khò khè hoặc các vấn đề về phổi.' },
    { name: 'Khám Mắt', description: 'Mờ mắt, đau mắt, đỏ mắt, cộm ngứa hoặc các bệnh lý về mắt.' },
    { name: 'Khám Nhi', description: 'Dành cho người đi khám dưới 16 tuổi.' },
    { name: 'Khám Tai Mũi Họng', description: 'Đau họng, nghẹt mũi, viêm xoang, ù tai hoặc nghe kém.' },
    { name: 'Khám Nội Tiết', description: 'Theo dõi tiểu đường, tuyến giáp và các rối loạn nội tiết.' },
    { name: 'Khám Tim Mạch', description: 'Đau ngực, hồi hộp, khó thở, huyết áp hoặc mỡ máu.' },
  ];
  protected readonly slots: TimeSlot[] = [
    { label: '07:30 - 08:30', value: '07:30', period: 'Buổi sáng' },
    { label: '08:30 - 09:30', value: '08:30', period: 'Buổi sáng' },
    { label: '09:30 - 10:30', value: '09:30', period: 'Buổi sáng' },
    { label: '10:30 - 11:30', value: '10:30', period: 'Buổi sáng' },
    { label: '13:00 - 14:00', value: '13:00', period: 'Buổi chiều' },
    { label: '14:00 - 15:00', value: '14:00', period: 'Buổi chiều' },
    { label: '15:00 - 16:00', value: '15:00', period: 'Buổi chiều' },
  ];
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
    this.authApi.getPatientProfiles().subscribe({
      next: (profiles) => {
        this.profiles = profiles;
        const primary = profiles.find((profile) => profile.primaryProfile) ?? profiles[0];
        if (primary) this.form.controls.profileId.setValue(primary.id);
        this.profilesLoading = false;
      },
      error: (response) => {
        this.profilesLoading = false;
        if (response.status === 401 || response.status === 403) {
          sessionStorage.removeItem('clinicOneAccessToken');
          sessionStorage.removeItem('clinicOnePatientName');
          void this.router.navigateByUrl('/login');
          return;
        }
        this.error = apiErrorMessage(response);
      },
    });
  }

  protected filteredSpecialties(): SpecialtyOption[] {
    const query = this.specialtySearch().trim().toLocaleLowerCase('vi-VN');
    return query ? this.specialties.filter((item) => `${item.name} ${item.description}`.toLocaleLowerCase('vi-VN').includes(query)) : this.specialties;
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
    this.form.controls.appointmentDate.setValue(date.iso);
    this.form.controls.startTime.reset('');
  }

  protected chooseSlot(slot: TimeSlot): void {
    this.clearError();
    this.selectedSlot.set(slot.value);
    this.form.controls.startTime.setValue(slot.value);
  }

  protected slotsFor(period: TimeSlot['period']): TimeSlot[] {
    return this.slots.filter((slot) => slot.period === period);
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
        if (response.status === 401 || response.status === 403) {
          sessionStorage.removeItem('clinicOneAccessToken');
          sessionStorage.removeItem('clinicOnePatientName');
          void this.router.navigateByUrl('/login');
          return;
        }
        this.error = apiErrorMessage(response);
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
