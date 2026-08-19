import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import {
  AuthApiService,
  ClinicRoomResponse,
  ClinicServiceResponse,
  DoctorAccountResponse,
  ScheduleBreakRequest,
  ScheduleTemplateRequest,
  ScheduleTemplateResponse,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { clinicTodayIso } from '../../core/time/clinic-time';
import { hasStaffRole } from '../../core/auth/auth.guard';

@Component({
  selector: 'app-schedule-template-management',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './schedule-template-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScheduleTemplateManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly today = clinicTodayIso();

  protected readonly services = signal<ClinicServiceResponse[]>([]);
  protected readonly doctors = signal<DoctorAccountResponse[]>([]);
  protected readonly rooms = signal<ClinicRoomResponse[]>([]);
  protected readonly templates = signal<ScheduleTemplateResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly modalOpen = signal(false);

  protected readonly selectedServiceId = signal('');
  protected readonly selectedDoctorId = signal('');
  protected readonly selectedRoomId = signal('');
  protected readonly startDate = signal(this.today);
  protected readonly endDate = signal(this.addDays(this.today, 30));
  protected readonly dayStart = signal('08:00');
  protected readonly dayEnd = signal('17:00');
  protected readonly durationMinutes = signal(30);
  protected readonly selectedWeekdays = signal<string[]>(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']);
  protected readonly breakStart = signal('');
  protected readonly breakEnd = signal('');
  protected readonly exceptionDatesText = signal('');

  protected canManageSchedule(): boolean {
    return hasStaffRole('COORDINATOR');
  }

  protected readonly weekdays = [
    { value: 'MONDAY', label: 'Thứ 2' }, { value: 'TUESDAY', label: 'Thứ 3' },
    { value: 'WEDNESDAY', label: 'Thứ 4' }, { value: 'THURSDAY', label: 'Thứ 5' },
    { value: 'FRIDAY', label: 'Thứ 6' }, { value: 'SATURDAY', label: 'Thứ 7' },
    { value: 'SUNDAY', label: 'Chủ nhật' },
  ];

  protected readonly searchTerm = signal('');

  protected filteredTemplates(): ScheduleTemplateResponse[] {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.templates();
    return this.templates().filter((t) =>
      t.serviceName.toLowerCase().includes(q) ||
      t.doctorName.toLowerCase().includes(q) ||
      t.roomCode.toLowerCase().includes(q)
    );
  }

  protected totalTemplatesCount(): number {
    return this.templates().length;
  }

  protected activeServicesCount(): number {
    return new Set(this.templates().map((t) => t.clinicServiceId)).size;
  }

  protected assignedRoomsCount(): number {
    return new Set(this.templates().map((t) => t.roomId)).size;
  }

  ngOnInit(): void {
    this.loadData();
  }

  protected loadData(): void {
    this.loading.set(true);
    forkJoin({
      services: this.authApi.getClinicServices(true),
      doctors: this.authApi.getDoctors(),
      rooms: this.authApi.getRooms(),
      templates: this.authApi.getScheduleTemplates(),
    }).subscribe({
      next: (data) => {
        this.services.set(data.services);
        this.doctors.set(data.doctors);
        this.rooms.set(data.rooms);
        this.templates.set(data.templates);
        const first = data.services[0];
        if (first && !this.selectedServiceId()) this.selectService(first.id);
        this.loading.set(false);
      },
      error: (response) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected selectService(serviceId: string): void {
    this.selectedServiceId.set(serviceId);
    const service = this.services().find((item) => item.id === serviceId);
    this.durationMinutes.set(service?.durationMinutes ?? 30);
    const firstDoctor = this.availableDoctors()[0];
    this.selectedDoctorId.set(firstDoctor?.staffId ?? '');
    this.selectedRoomId.set(firstDoctor?.roomId ?? '');
  }

  protected selectDoctor(doctorId: string): void {
    this.selectedDoctorId.set(doctorId);
    this.selectedRoomId.set(this.doctors().find((item) => item.staffId === doctorId)?.roomId ?? '');
  }

  protected availableDoctors(): DoctorAccountResponse[] {
    const service = this.services().find((item) => item.id === this.selectedServiceId());
    const eligible = new Set(service?.eligibleDoctors.map((item) => item.staffId) ?? []);
    return this.doctors().filter((doctor) => doctor.assigned && doctor.active && eligible.has(doctor.staffId));
  }

  protected availableRooms(): ClinicRoomResponse[] {
    const doctor = this.doctors().find((item) => item.staffId === this.selectedDoctorId());
    return this.rooms().filter((room) => room.active && (!doctor?.roomId || room.id === doctor.roomId));
  }

  protected isWeekdaySelected(day: string): boolean {
    return this.selectedWeekdays().includes(day);
  }

  protected toggleWeekday(day: string, checked: boolean): void {
    this.selectedWeekdays.update((days) => checked
      ? (days.includes(day) ? days : [...days, day])
      : days.filter((item) => item !== day));
  }

  protected submit(): void {
    if (!this.canManageSchedule()) {
      this.error.set('Chỉ điều phối viên được thay đổi lịch làm việc.');
      return;
    }
    const breaks: ScheduleBreakRequest[] = this.breakStart() && this.breakEnd()
      ? [{ startTime: this.breakStart(), endTime: this.breakEnd() }] : [];
    const request: ScheduleTemplateRequest = {
      clinicServiceId: this.selectedServiceId(),
      doctorId: this.selectedDoctorId(),
      roomId: this.selectedRoomId(),
      startDate: this.startDate(),
      endDate: this.endDate(),
      weekdays: this.selectedWeekdays(),
      dayStart: this.dayStart(),
      dayEnd: this.dayEnd(),
      durationMinutes: Number(this.durationMinutes()),
      breaks,
      exceptionDates: this.exceptionDatesText().split(',').map((value) => value.trim()).filter(Boolean),
    };
    if (!request.clinicServiceId || !request.doctorId || !request.roomId || !request.startDate || !request.endDate
      || request.weekdays.length === 0 || request.dayStart >= request.dayEnd) {
      this.error.set('Chọn đủ dịch vụ, bác sĩ, phòng, ngày làm và giờ làm.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.authApi.createScheduleTemplate(request).subscribe({
      next: (template) => {
        this.templates.update((items) => [template, ...items]);
        this.notice.set(`Đã sinh ${template.generatedSlotCount} khung giờ.`);
        this.saving.set(false);
        this.modalOpen.set(false);
      },
      error: (response) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected openCreate(): void {
    if (!this.canManageSchedule()) return;
    this.error.set('');
    this.notice.set('');
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    if (this.saving()) return;
    this.modalOpen.set(false);
  }

  protected regenerate(template: ScheduleTemplateResponse): void {
    if (!this.canManageSchedule()) {
      this.error.set('Chỉ điều phối viên được sinh bổ sung khung giờ.');
      return;
    }
    this.authApi.regenerateScheduleTemplate(template.id).subscribe({
      next: (updated) => {
        this.templates.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.notice.set(`Đã kiểm tra và bổ sung khung giờ cho ${updated.serviceName}.`);
      },
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected formatWeekdays(days: string[]): string {
    return days.map((day) => this.weekdays.find((item) => item.value === day)?.label ?? day).join(', ');
  }

  private addDays(value: string, days: number): string {
    const date = new Date(`${value}T00:00:00`);
    date.setDate(date.getDate() + days);
    return date.toISOString().slice(0, 10);
  }
}
