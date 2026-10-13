import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, ScheduleTemplateResponse, apiErrorMessage } from '../../core/auth/auth-api.service';
import { clinicTodayDate, clinicTodayIso, formatClinicPadDate, formatClinicTime } from '../../core/time/clinic-time';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { DEFAULT_DOCTOR_AVATAR, resolveDoctorAvatar } from '../../shared/doctor-utils';

const WEEK_DAYS = [
  { key: 'MONDAY', label: 'Thứ 2' },
  { key: 'TUESDAY', label: 'Thứ 3' },
  { key: 'WEDNESDAY', label: 'Thứ 4' },
  { key: 'THURSDAY', label: 'Thứ 5' },
  { key: 'FRIDAY', label: 'Thứ 6' },
  { key: 'SATURDAY', label: 'Thứ 7' },
  { key: 'SUNDAY', label: 'Chủ nhật' },
] as const;

@Component({
  selector: 'app-doctor-schedule',
  standalone: true,
  imports: [MatIconModule, StaffWorkspaceShell],
  templateUrl: './doctor-schedule.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DoctorSchedule implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly schedules = signal<ScheduleTemplateResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly activeSchedules = computed(() => this.schedules().filter((schedule) => schedule.active));
  protected readonly doctorProfile = computed(() => this.activeSchedules()[0] ?? null);
  protected readonly weeklySchedule = computed(() => {
    const monday = clinicTodayDate();
    monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7));
    return WEEK_DAYS.map((day, index) => {
      const date = new Date(monday);
      date.setDate(monday.getDate() + index);
      const dateIso = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
      return {
        ...day,
        dateLabel: formatClinicPadDate(dateIso).slice(0, 5),
        isToday: dateIso === clinicTodayIso(),
        schedules: this.activeSchedules()
          .filter((schedule) => schedule.weekdays.includes(day.key))
          .sort((first, second) => first.dayStart.localeCompare(second.dayStart)),
      };
    });
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    this.authApi.getMyScheduleTemplates().subscribe({
      next: (schedules) => {
        this.schedules.set(schedules ?? []);
        this.loading.set(false);
      },
      error: (response) => {
        this.error.set(apiErrorMessage(response));
        this.loading.set(false);
      },
    });
  }

  protected formatTime(value: string): string {
    return formatClinicTime(value);
  }

  protected formatDateRange(startDate: string, endDate: string): string {
    const start = formatClinicPadDate(startDate);
    const end = formatClinicPadDate(endDate);
    if (!start || !end) return start || end;
    return start.slice(-4) === end.slice(-4) ? `${start.slice(0, 5)}–${end}` : `${start}–${end}`;
  }

  protected doctorAvatar(avatarUrl: string | null | undefined): string {
    return resolveDoctorAvatar(avatarUrl);
  }

  protected handleAvatarError(event: Event): void {
    (event.target as HTMLImageElement | null)?.setAttribute('src', DEFAULT_DOCTOR_AVATAR);
  }
}
