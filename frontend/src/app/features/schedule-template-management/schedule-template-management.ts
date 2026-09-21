import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
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
import { clinicTodayIso, clinicTodayDate } from '../../core/time/clinic-time';
import { hasStaffRole } from '../../core/auth/auth.guard';
import { DEFAULT_DOCTOR_AVATAR, matchesDoctorIdentity, resolveDoctorAvatar } from '../../shared/doctor-utils';

function getMonday(base: Date): Date {
  const d = new Date(base);
  const day = d.getDay();
  const diff = d.getDate() - (day === 0 ? 6 : day - 1);
  d.setDate(diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

function formatShortDate(d: Date): string {
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const yyyy = d.getFullYear();
  return `${dd}/${mm}/${yyyy}`;
}

function createWeekDays(base: Date): Date[] {
  const monday = getMonday(base);
  const days: Date[] = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(monday);
    d.setDate(d.getDate() + i);
    days.push(d);
  }
  return days;
}

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
  protected readonly selectedDate = signal<Date>(clinicTodayDate());
  protected weekDays = createWeekDays(clinicTodayDate());

  protected readonly dateInputValue = computed<string>(() => {
    const d = this.selectedDate();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  });

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
  protected readonly searchTerm = signal('');
  protected readonly hasBreak = signal(false);
  protected readonly filterSpecialty = signal('');
  protected readonly filterDoctor = signal('');
  protected readonly selectedDateRange = signal('Tất cả thời gian');
  protected readonly selectedTemplateForDetail = signal<ScheduleTemplateResponse | null>(null);
  protected readonly selectedWeekdayForDetail = signal<string>('');

  protected isDoctorRole(): boolean {
    return hasStaffRole('DOCTOR') && !hasStaffRole('COORDINATOR') && !hasStaffRole('ADMIN');
  }

  protected readonly filterOnlyMine = signal(this.isDoctorRole());

  protected canManageSchedule(): boolean {
    return hasStaffRole('COORDINATOR');
  }

  protected readonly weekdays = [
    { value: 'MONDAY', label: 'Thứ 2' }, { value: 'TUESDAY', label: 'Thứ 3' },
    { value: 'WEDNESDAY', label: 'Thứ 4' }, { value: 'THURSDAY', label: 'Thứ 5' },
    { value: 'FRIDAY', label: 'Thứ 6' }, { value: 'SATURDAY', label: 'Thứ 7' },
    { value: 'SUNDAY', label: 'Chủ nhật' },
  ];

  protected readonly displayWeekdays = [
    { value: 'MONDAY', label: 'Thứ 2' },
    { value: 'TUESDAY', label: 'Thứ 3' },
    { value: 'WEDNESDAY', label: 'Thứ 4' },
    { value: 'THURSDAY', label: 'Thứ 5' },
    { value: 'FRIDAY', label: 'Thứ 6' },
    { value: 'SATURDAY', label: 'Thứ 7' },
    { value: 'SUNDAY', label: 'Chủ nhật' },
  ];

  protected readonly weekStartDate = computed<Date>(() => getMonday(this.selectedDate()));
  protected readonly weekEndDate = computed<Date>(() => {
    const d = new Date(this.weekStartDate());
    d.setDate(d.getDate() + 6);
    return d;
  });

  protected readonly weekRangeLabel = computed<string>(() => {
    const s = this.weekStartDate();
    const e = this.weekEndDate();
    const sDd = String(s.getDate()).padStart(2, '0');
    const sMm = String(s.getMonth() + 1).padStart(2, '0');
    const eDd = String(e.getDate()).padStart(2, '0');
    const eMm = String(e.getMonth() + 1).padStart(2, '0');
    const yyyy = e.getFullYear();
    return `${sDd}/${sMm} – ${eDd}/${eMm}/${yyyy}`;
  });

  protected readonly weekTemplates = computed<ScheduleTemplateResponse[]>(() => {
    const ws = this.weekStartDate();
    const we = this.weekEndDate();
    const wsIso = `${ws.getFullYear()}-${String(ws.getMonth()+1).padStart(2,'0')}-${String(ws.getDate()).padStart(2,'0')}`;
    const weIso = `${we.getFullYear()}-${String(we.getMonth()+1).padStart(2,'0')}-${String(we.getDate()).padStart(2,'0')}`;
    const isDoc = this.isDoctorRole();
    const onlyMine = isDoc || this.filterOnlyMine();
    const docFilter = this.filterDoctor().trim().toLowerCase();
    const myStaffId = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('clinicOneStaffId') : null;
    const myName = typeof sessionStorage !== 'undefined' ? (sessionStorage.getItem('clinicOneStaffName') || sessionStorage.getItem('clinicOnePatientName') || '') : '';

    return this.templates().filter((t) => {
      if (t.startDate > weIso || t.endDate < wsIso) return false;
      if (onlyMine) {
        if (!matchesDoctorIdentity({ myStaffId, myName, doctorId: t.doctorId, doctorName: t.doctorName })) return false;
      }
      if (docFilter && !t.doctorName.toLowerCase().includes(docFilter)) return false;
      return true;
    });
  });

  protected readonly filteredRooms = computed<ClinicRoomResponse[]>(() => {
    const spec = this.filterSpecialty().trim().toLowerCase();
    const q = this.searchTerm().trim().toLowerCase();
    return this.rooms().filter((r) => {
      if (!r.active) return false;
      if (spec && !r.specialty.toLowerCase().includes(spec)) return false;
      if (q && !r.name.toLowerCase().includes(q) && !r.code.toLowerCase().includes(q) && !r.specialty.toLowerCase().includes(q)) return false;
      return true;
    });
  });

  protected readonly weekFilteredRooms = computed<ClinicRoomResponse[]>(() => {
    const roomIds = new Set(this.weekTemplates().map((t) => t.roomId));
    return this.filteredRooms().filter((r) => roomIds.has(r.id));
  });

  protected readonly filteredTemplates = computed<ScheduleTemplateResponse[]>(() => {
    const q = this.searchTerm().trim().toLowerCase();
    const spec = this.filterSpecialty().trim().toLowerCase();
    const docFilter = this.filterDoctor().trim().toLowerCase();
    const isDoc = this.isDoctorRole();
    const onlyMine = isDoc || this.filterOnlyMine();
    const myStaffId = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('clinicOneStaffId') : null;
    const myName = typeof sessionStorage !== 'undefined' ? (sessionStorage.getItem('clinicOneStaffName') || sessionStorage.getItem('clinicOnePatientName') || '') : '';

    return this.templates().filter((t) => {
      if (onlyMine) {
        if (!matchesDoctorIdentity({ myStaffId, myName, doctorId: t.doctorId, doctorName: t.doctorName })) return false;
      }
      if (docFilter && !t.doctorName.toLowerCase().includes(docFilter)) return false;
      if (spec && !t.serviceName.toLowerCase().includes(spec) && !t.specialty.toLowerCase().includes(spec)) return false;
      if (q && !t.serviceName.toLowerCase().includes(q) && !t.doctorName.toLowerCase().includes(q) && !t.roomCode.toLowerCase().includes(q)) return false;
      return true;
    });
  });

  protected readonly templateMatrix = computed<Map<string, ScheduleTemplateResponse[]>>(() => {
    const map = new Map<string, ScheduleTemplateResponse[]>();
    for (const t of this.weekTemplates()) {
      for (const w of t.weekdays) {
        const key = `${t.roomId}_${w}`;
        let list = map.get(key);
        if (!list) {
          list = [];
          map.set(key, list);
        }
        list.push(t);
      }
    }
    return map;
  });

  protected getTemplatesForRoomAndDate(roomId: string, date: Date, weekday: string): ScheduleTemplateResponse[] {
    if (!date) return [];
    const dateIso = clinicTodayIso(date);
    return this.weekTemplates().filter((t) => {
      if (t.roomId !== roomId) return false;
      if (!t.weekdays.includes(weekday)) return false;
      if (t.startDate && dateIso < t.startDate) return false;
      if (t.endDate && dateIso > t.endDate) return false;
      if (t.exceptionDates && t.exceptionDates.includes(dateIso)) return false;
      return true;
    });
  }

  protected getTemplatesForRoomAndDay(roomId: string, day: string): ScheduleTemplateResponse[] {
    return this.templateMatrix().get(`${roomId}_${day}`) ?? [];
  }

  protected generateWeekDays(baseDate: Date): void {
    this.weekDays = createWeekDays(baseDate);
  }

  protected onPreviousWeek(): void {
    const d = new Date(this.selectedDate());
    d.setDate(d.getDate() - 7);
    this.selectedDate.set(d);
    this.onWeekChanged();
  }

  protected onNextWeek(): void {
    const d = new Date(this.selectedDate());
    d.setDate(d.getDate() + 7);
    this.selectedDate.set(d);
    this.onWeekChanged();
  }

  protected onToday(): void {
    this.selectedDate.set(clinicTodayDate());
    this.onWeekChanged();
  }

  protected onDateChange(value: string): void {
    if (!value) return;
    const [y, m, day] = value.split('-').map(Number);
    this.selectedDate.set(new Date(y, m - 1, day));
    this.onWeekChanged();
  }

  protected isToday(date: Date): boolean {
    return clinicTodayIso(date) === clinicTodayIso();
  }

  protected weekDayLabel(dateIndex: number): string {
    const d = this.weekDays[dateIndex];
    if (!d) return '';
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  private onWeekChanged(): void {
    this.generateWeekDays(this.selectedDate());
  }

  ngOnInit(): void {
    this.generateWeekDays(this.selectedDate());
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

  protected readonly availableDoctors = computed<DoctorAccountResponse[]>(() => {
    const service = this.services().find((item) => item.id === this.selectedServiceId());
    const eligible = new Set(service?.eligibleDoctors.map((item) => item.staffId) ?? []);
    return this.doctors().filter((doctor) => doctor.assigned && doctor.active && eligible.has(doctor.staffId));
  });

  protected readonly availableRooms = computed<ClinicRoomResponse[]>(() => {
    const doctor = this.doctors().find((item) => item.staffId === this.selectedDoctorId());
    return this.rooms().filter((room) => room.active && (!doctor?.roomId || room.id === doctor.roomId));
  });

  protected readonly selectedDoctor = computed<DoctorAccountResponse | undefined>(() => {
    return this.doctors().find((doc) => doc.staffId === this.selectedDoctorId());
  });

  protected readonly previewSlots = computed<{ time: string; isBreak?: boolean; label?: string }[]>(() => {
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
  });

  protected getWeekdayLabel(day: string): string {
    return this.weekdays.find((w) => w.value === day)?.label ?? day;
  }

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

  protected openTemplateDetail(template: ScheduleTemplateResponse, weekday?: string): void {
    this.selectedTemplateForDetail.set(template);
    this.selectedWeekdayForDetail.set(weekday || '');
  }

  protected closeTemplateDetail(): void {
    this.selectedTemplateForDetail.set(null);
    this.selectedWeekdayForDetail.set('');
  }

  protected scheduleForRoomAndDay(room: ClinicRoomResponse, day: string): void {
    if (!this.canManageSchedule()) return;
    this.selectedRoomId.set(room.id);
    const docInRoom = this.doctors().find((d) => d.roomId === room.id);
    if (docInRoom) {
      this.selectedDoctorId.set(docInRoom.staffId);
      const matchService = this.services().find((s) => s.specialty?.toLowerCase() === docInRoom.specialty?.toLowerCase()) || this.services()[0];
      if (matchService) {
        this.selectedServiceId.set(matchService.id);
        this.durationMinutes.set(matchService.durationMinutes ?? 30);
      }
    } else {
      const matchDoc = this.doctors().find((d) => d.specialty?.toLowerCase() === room.specialty?.toLowerCase()) || this.doctors()[0];
      if (matchDoc) {
        this.selectedDoctorId.set(matchDoc.staffId);
      }
      const matchService = this.services().find((s) => s.specialty?.toLowerCase() === room.specialty?.toLowerCase()) || this.services()[0];
      if (matchService) {
        this.selectedServiceId.set(matchService.id);
        this.durationMinutes.set(matchService.durationMinutes ?? 30);
      }
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
    const s = template.dayStart ? template.dayStart.slice(0, 5) : '';
    const e = template.dayEnd ? template.dayEnd.slice(0, 5) : '';
    const startHour = Number(s.split(':')[0]) || 0;
    const endHour = Number(e.split(':')[0]) || 0;
    if (startHour < 12 && endHour >= 16) {
      return { label: `Cả ngày (${s} - ${e})`, class: 'border-teal-200 bg-teal-50 text-teal-800' };
    }
    if (startHour < 12) {
      return { label: `Ca sáng (${s} - ${e})`, class: 'border-sky-200 bg-sky-50 text-sky-800' };
    }
    return { label: `Ca chiều (${s} - ${e})`, class: 'border-purple-200 bg-purple-50 text-purple-800' };
  }

  protected getDoctorSvgAvatar(_doctorName?: string): string {
    return DEFAULT_DOCTOR_AVATAR;
  }

  protected handleAvatarError(event: Event, _doctorName?: string): void {
    const target = event.target as HTMLImageElement;
    if (target) {
      target.src = DEFAULT_DOCTOR_AVATAR;
    }
  }

  protected getDoctorAvatar(doctorName: string, directAvatar?: string | null): string {
    if (directAvatar) return resolveDoctorAvatar(directAvatar);
    const doc = this.doctors().find((d) => matchesDoctorIdentity({ doctorId: null, doctorName: d.fullName, myName: doctorName }));
    if (doc?.avatarUrl) return resolveDoctorAvatar(doc.avatarUrl);
    return DEFAULT_DOCTOR_AVATAR;
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
    const doc = this.doctors().find((item) => item.staffId === doctorId);
    if (doc) {
      if (doc.roomId) this.selectedRoomId.set(doc.roomId);
      const matchSvc = this.services().find((s) => s.specialty?.toLowerCase() === doc.specialty?.toLowerCase());
      if (matchSvc) {
        this.selectedServiceId.set(matchSvc.id);
        this.durationMinutes.set(matchSvc.durationMinutes ?? 30);
      }
    }
  }

  protected selectRoom(roomId: string): void {
    this.selectedRoomId.set(roomId);
    const room = this.rooms().find((r) => r.id === roomId);
    if (room) {
      const matchDoc = this.doctors().find((d) => d.roomId === room.id || d.specialty?.toLowerCase() === room.specialty?.toLowerCase());
      if (matchDoc) {
        this.selectedDoctorId.set(matchDoc.staffId);
        const matchSvc = this.services().find((s) => s.specialty?.toLowerCase() === matchDoc.specialty?.toLowerCase());
        if (matchSvc) {
          this.selectedServiceId.set(matchSvc.id);
          this.durationMinutes.set(matchSvc.durationMinutes ?? 30);
        }
      }
    }
  }

  protected readonly previewSlotCount = computed<number>(() => {
    return this.previewSlots().filter((s) => !s.isBreak).length;
  });

  protected readonly selectedDoctorObj = computed<DoctorAccountResponse | undefined>(() => {
    return this.doctors().find((d) => d.staffId === this.selectedDoctorId());
  });

  protected readonly selectedRoomObj = computed<ClinicRoomResponse | undefined>(() => {
    return this.rooms().find((r) => r.id === this.selectedRoomId());
  });

  protected readonly selectedServiceObj = computed<ClinicServiceResponse | undefined>(() => {
    return this.services().find((s) => s.id === this.selectedServiceId());
  });

  protected currentShiftPreset(): 'FULL_DAY' | 'MORNING' | 'AFTERNOON' | 'CUSTOM' {
    const s = this.dayStart();
    const e = this.dayEnd();
    const brk = this.hasBreak();
    if (s === '08:00' && e === '17:00' && brk) return 'FULL_DAY';
    if (s === '08:00' && e === '12:00' && !brk) return 'MORNING';
    if (s === '13:00' && e === '17:00' && !brk) return 'AFTERNOON';
    return 'CUSTOM';
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
        this.notice.set(`Đã kích hoạt lịch trực cho ${template.doctorName} (${template.generatedSlotCount} khung giờ khám).`);
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

  protected deleteTemplate(template: ScheduleTemplateResponse, weekday?: string): void {
    if (!this.canManageSchedule()) return;
    this.closeTemplateDetail();
    this.authApi.deleteScheduleTemplate(template.id, weekday).subscribe({
      next: () => {
        if (weekday && template.weekdays.length > 1) {
          this.templates.update((items) =>
            items.map((item) =>
              item.id === template.id
                ? { ...item, weekdays: item.weekdays.filter((w) => w !== weekday) }
                : item
            )
          );
          this.notice.set(`Đã xóa ca trực ${this.getWeekdayLabel(weekday)} của ${template.doctorName}.`);
        } else {
          this.templates.update((items) => items.filter((item) => item.id !== template.id));
          this.notice.set(`Đã xóa lịch trực của ${template.doctorName}.`);
        }
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
