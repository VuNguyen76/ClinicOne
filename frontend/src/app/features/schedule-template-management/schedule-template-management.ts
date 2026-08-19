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
  protected readonly activeTab = signal<'grid' | 'list' | 'form'>('grid');

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
  protected readonly hasBreak = signal(false);

  protected applyWeekdayPreset(preset: 'WEEKDAYS' | 'ALL_EXCEPT_SUNDAY' | 'ALL'): void {
    if (preset === 'WEEKDAYS') {
      this.selectedWeekdays.set(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']);
    } else if (preset === 'ALL_EXCEPT_SUNDAY') {
      this.selectedWeekdays.set(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']);
    } else {
      this.selectedWeekdays.set(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']);
    }
  }

  protected applyPeriodPreset(days: number): void {
    this.startDate.set(this.today);
    this.endDate.set(this.addDays(this.today, days));
  }

  protected applyTimePreset(start: string, end: string): void {
    this.dayStart.set(start);
    this.dayEnd.set(end);
  }

  protected toggleBreakOption(enabled: boolean): void {
    this.hasBreak.set(enabled);
    if (enabled) {
      this.breakStart.set('12:00');
      this.breakEnd.set('13:00');
    } else {
      this.breakStart.set('');
      this.breakEnd.set('');
    }
  }

  protected filteredTemplates(): ScheduleTemplateResponse[] {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.templates();
    return this.templates().filter((t) =>
      t.serviceName.toLowerCase().includes(q) ||
      t.doctorName.toLowerCase().includes(q) ||
      t.roomCode.toLowerCase().includes(q)
    );
  }

  protected readonly filterSpecialty = signal('');
  protected readonly selectedDateRange = signal('Tất cả thời gian');

  protected selectedDoctor(): DoctorAccountResponse | undefined {
    return this.doctors().find((doc) => doc.staffId === this.selectedDoctorId());
  }

  protected previewSlots(): { time: string; isBreak?: boolean; label?: string }[] {
    const start = this.dayStart() || '08:00';
    const end = this.dayEnd() || '17:00';
    const step = Number(this.durationMinutes()) || 30;
    const hasBrk = this.hasBreak();
    const brkStart = this.breakStart() || '12:00';
    const brkEnd = this.breakEnd() || '13:00';

    const parseMinutes = (t: string) => {
      const parts = t.split(':');
      return (Number(parts[0]) || 0) * 60 + (Number(parts[1]) || 0);
    };

    const formatMinutes = (mins: number) => {
      const h = Math.floor(mins / 60).toString().padStart(2, '0');
      const m = (mins % 60).toString().padStart(2, '0');
      return `${h}:${m}`;
    };

    const startMins = parseMinutes(start);
    const endMins = parseMinutes(end);
    const brkStartMins = parseMinutes(brkStart);
    const brkEndMins = parseMinutes(brkEnd);

    const result: { time: string; isBreak?: boolean; label?: string }[] = [];
    let insertedBreak = false;

    for (let cur = startMins; cur <= endMins; cur += step) {
      if (hasBrk && cur >= brkStartMins && cur < brkEndMins) {
        if (!insertedBreak) {
          result.push({ time: `${brkStart} - ${brkEnd}`, isBreak: true, label: `Nghỉ trưa (${brkStart} - ${brkEnd})` });
          insertedBreak = true;
        }
        continue;
      }
      result.push({ time: formatMinutes(cur) });
    }

    return result;
  }

  protected getTemplatesForDayAndShift(day: string, shift: 'morning' | 'afternoon'): ScheduleTemplateResponse[] {
    const specialty = this.filterSpecialty().toLowerCase();
    return this.templates().filter((t) => {
      if (!t.weekdays.includes(day)) return false;
      if (specialty && !t.serviceName.toLowerCase().includes(specialty)) return false;
      const startHour = Number(t.dayStart.split(':')[0]) || 0;
      const endHour = Number(t.dayEnd.split(':')[0]) || 0;
      if (shift === 'morning') {
        return startHour < 12;
      } else {
        return endHour > 12 || startHour >= 12;
      }
    });
  }

  protected readonly displayWeekdays = [
    { value: 'MONDAY', label: 'Thứ 2' },
    { value: 'TUESDAY', label: 'Thứ 3' },
    { value: 'WEDNESDAY', label: 'Thứ 4' },
    { value: 'THURSDAY', label: 'Thứ 5' },
    { value: 'FRIDAY', label: 'Thứ 6' },
    { value: 'SATURDAY', label: 'Thứ 7' },
  ];

  protected readonly selectedTemplateForDetail = signal<ScheduleTemplateResponse | null>(null);

  protected openTemplateDetail(template: ScheduleTemplateResponse): void {
    this.selectedTemplateForDetail.set(template);
  }

  protected closeTemplateDetail(): void {
    this.selectedTemplateForDetail.set(null);
  }

  protected filteredRooms(): ClinicRoomResponse[] {
    const spec = this.filterSpecialty().trim().toLowerCase();
    const q = this.searchTerm().trim().toLowerCase();
    return this.rooms().filter((r) => {
      if (!r.active) return false;
      if (spec && !r.specialty.toLowerCase().includes(spec)) return false;
      if (q && !r.name.toLowerCase().includes(q) && !r.code.toLowerCase().includes(q) && !r.specialty.toLowerCase().includes(q)) return false;
      return true;
    });
  }

  protected getTemplatesForRoomAndDay(roomId: string, day: string): ScheduleTemplateResponse[] {
    return this.filteredTemplates().filter((t) => t.roomId === roomId && t.weekdays.includes(day));
  }

  protected scheduleForRoomAndDay(room: ClinicRoomResponse, day: string): void {
    if (!this.canManageSchedule()) return;
    this.selectedRoomId.set(room.id);
    const matchService = this.services().find((s) => s.specialty.toLowerCase() === room.specialty.toLowerCase()) || this.services()[0];
    if (matchService) {
      this.selectService(matchService.id);
    }
    this.selectedWeekdays.set([day]);
    this.startCreate();
  }

  protected applyShiftPreset(preset: 'FULL_DAY' | 'MORNING' | 'AFTERNOON'): void {
    if (preset === 'FULL_DAY') {
      this.dayStart.set('08:00');
      this.dayEnd.set('17:00');
      this.hasBreak.set(true);
      this.breakStart.set('12:00');
      this.breakEnd.set('13:00');
    } else if (preset === 'MORNING') {
      this.dayStart.set('08:00');
      this.dayEnd.set('12:00');
      this.hasBreak.set(false);
      this.breakStart.set('');
      this.breakEnd.set('');
    } else if (preset === 'AFTERNOON') {
      this.dayStart.set('13:00');
      this.dayEnd.set('17:00');
      this.hasBreak.set(false);
      this.breakStart.set('');
      this.breakEnd.set('');
    }
  }

  protected getShiftBadge(template: ScheduleTemplateResponse): { label: string; class: string } {
    const startHour = Number(template.dayStart.split(':')[0]) || 0;
    const endHour = Number(template.dayEnd.split(':')[0]) || 0;
    if (startHour < 12 && endHour >= 16) {
      return { label: 'Cả ngày (08:00-17:00)', class: 'border-teal-200 bg-teal-50 text-teal-800' };
    }
    if (startHour < 12) {
      return { label: 'Ca sáng (08:00-12:00)', class: 'border-sky-200 bg-sky-50 text-sky-800' };
    }
    return { label: 'Ca chiều (13:00-17:00)', class: 'border-purple-200 bg-purple-50 text-purple-800' };
  }

  private readonly doctorAvatarMap: Record<string, string> = {
    'nguyễn an': 'https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=150&auto=format&fit=crop&q=80',
    'trần minh': 'https://images.unsplash.com/photo-1537368910025-700350fe46c7?w=150&auto=format&fit=crop&q=80',
    'lê thu hà': 'https://images.unsplash.com/photo-1594824813589-32212356c382?w=150&auto=format&fit=crop&q=80',
    'phạm quốc dũng': 'https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?w=150&auto=format&fit=crop&q=80',
    'hoàng thanh nga': 'https://images.unsplash.com/photo-1559839734-2b71ea197ec2?w=150&auto=format&fit=crop&q=80',
    'vũ đình toàn': 'https://images.unsplash.com/photo-1582750433449-648ed127bb54?w=150&auto=format&fit=crop&q=80',
    'đặng mai lan': 'https://images.unsplash.com/photo-1651008376811-b90baee60c1f?w=150&auto=format&fit=crop&q=80',
  };

  protected getDoctorAvatar(doctorName: string, directAvatar?: string | null): string {
    if (directAvatar) return directAvatar;
    if (!doctorName) return 'https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=150&auto=format&fit=crop&q=80';
    const cleanName = doctorName.replace(/^(bs\.|ths\.|ckii|cki|bác sĩ|tiến sĩ|ts\.)\s*/i, '').trim().toLowerCase();
    const doc = this.doctors().find((d) => {
      const dClean = d.fullName.replace(/^(bs\.|ths\.|ckii|cki|bác sĩ|tiến sĩ|ts\.)\s*/i, '').trim().toLowerCase();
      return d.fullName.toLowerCase() === doctorName.toLowerCase()
        || (cleanName.length >= 3 && dClean.includes(cleanName))
        || (dClean.length >= 3 && cleanName.includes(dClean));
    });
    if (doc?.avatarUrl) return doc.avatarUrl;
    for (const [name, url] of Object.entries(this.doctorAvatarMap)) {
      if (cleanName.includes(name) || name.includes(cleanName)) return url;
    }
    return 'https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=150&auto=format&fit=crop&q=80';
  }

  protected getDoctorSpecialty(doctorName: string): string {
    const doc = this.doctors().find((d) => d.fullName.toLowerCase() === doctorName.toLowerCase());
    return doc?.specialty ?? 'Đa khoa';
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
    const breaks: ScheduleBreakRequest[] = this.hasBreak() && this.breakStart() && this.breakEnd()
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
        this.notice.set(`Đã thiết lập lịch trực và sinh ${template.generatedSlotCount} lượt khám.`);
        this.saving.set(false);
        this.modalOpen.set(false);
        this.activeTab.set('grid');
      },
      error: (response) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected startCreate(): void {
    if (!this.canManageSchedule()) return;
    this.error.set('');
    this.notice.set('');
    this.modalOpen.set(true);
    this.activeTab.set('form');
  }

  protected cancelEdit(): void {
    if (this.saving()) return;
    this.modalOpen.set(false);
    this.activeTab.set('grid');
  }

  protected openCreate(): void {
    this.startCreate();
  }

  protected closeModal(): void {
    this.cancelEdit();
  }

  protected regenerate(template: ScheduleTemplateResponse): void {
    if (!this.canManageSchedule()) {
      this.error.set('Chỉ điều phối viên được đồng bộ khung giờ.');
      return;
    }
    this.authApi.regenerateScheduleTemplate(template.id).subscribe({
      next: (updated) => {
        this.templates.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.notice.set(`Đã đồng bộ đầy đủ lượt khám cho ${updated.doctorName} (${updated.serviceName}).`);
      },
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected deleteTemplate(template: ScheduleTemplateResponse): void {
    if (!this.canManageSchedule()) return;
    this.authApi.deleteScheduleTemplate(template.id).subscribe({
      next: () => {
        this.templates.update((items) => items.filter((item) => item.id !== template.id));
        this.notice.set(`Đã xóa lịch trực của ${template.doctorName}.`);
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
