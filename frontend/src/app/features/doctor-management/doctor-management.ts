import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import {
  ApiErrorResponse,
  AuthApiService,
  ClinicRoomResponse,
  CreateDoctorRequest,
  DoctorAccountResponse,
  DoctorScheduleResponse,
  SpecialtyOption,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-doctor-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './doctor-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DoctorManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly doctors = signal<DoctorAccountResponse[]>([]);
  protected readonly rooms = signal<ClinicRoomResponse[]>([]);
  protected readonly specialties = signal<SpecialtyOption[]>([]);
  protected readonly selectedDoctor = signal<DoctorAccountResponse | null>(null);
  protected readonly schedules = signal<DoctorScheduleResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly createDoctorOpen = signal(false);
  protected readonly searchTerm = signal('');
  protected readonly selectedSpecialtyFilter = signal('');
  protected readonly isDrawerOpen = signal(false);
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly assignmentForm = this.fb.nonNullable.group({
    specialty: ['', [Validators.required]],
    roomId: ['', [Validators.required]],
  });
  protected readonly createDoctorForm = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(80)]],
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(200)]],
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(72)]],
  });
  protected readonly scheduleForm = this.fb.nonNullable.group({
    dayOfWeek: ['MONDAY', [Validators.required]],
    startTime: ['08:00', [Validators.required]],
    endTime: ['12:00', [Validators.required]],
    slotDurationMinutes: [30, [Validators.required, Validators.min(15), Validators.max(180)]],
  });
  protected readonly days = [
    { value: 'MONDAY', label: 'Thứ 2' }, { value: 'TUESDAY', label: 'Thứ 3' },
    { value: 'WEDNESDAY', label: 'Thứ 4' }, { value: 'THURSDAY', label: 'Thứ 5' },
    { value: 'FRIDAY', label: 'Thứ 6' }, { value: 'SATURDAY', label: 'Thứ 7' },
  ];

  ngOnInit(): void {
    forkJoin({ doctors: this.authApi.getDoctors(), rooms: this.authApi.getRooms(), specialties: this.authApi.getSpecialties() }).subscribe({
      next: (data) => {
        this.doctors.set(data.doctors);
        this.rooms.set(data.rooms.filter((room) => room.active));
        this.specialties.set(data.specialties);
        this.loading.set(false);
        if (data.doctors[0]) this.preloadDoctor(data.doctors[0]);
      },
      error: (response) => { this.loading.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected preloadDoctor(doctor: DoctorAccountResponse): void {
    this.selectedDoctor.set(doctor);
    this.assignmentForm.setValue({ specialty: doctor.specialty ?? '', roomId: doctor.roomId ?? '' });
    this.isDrawerOpen.set(false);
    this.authApi.getDoctorSchedules(doctor.staffId).subscribe({
      next: (schedules) => this.schedules.set(schedules),
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected filteredDoctors(): DoctorAccountResponse[] {
    const query = this.searchTerm().trim().toLowerCase();
    const spec = this.selectedSpecialtyFilter().trim().toLowerCase();
    return this.doctors().filter((d) => {
      const matchQuery = !query ||
        d.fullName.toLowerCase().includes(query) ||
        d.username.toLowerCase().includes(query) ||
        (d.roomCode && d.roomCode.toLowerCase().includes(query)) ||
        (d.specialty && d.specialty.toLowerCase().includes(query));
      const matchSpec = !spec || (d.specialty && d.specialty.toLowerCase() === spec);
      return matchQuery && matchSpec;
    });
  }

  protected totalDoctorsCount(): number {
    return this.doctors().length;
  }

  protected assignedDoctorsCount(): number {
    return this.doctors().filter((d) => d.assigned).length;
  }

  protected activeDoctorsCount(): number {
    return this.doctors().filter((d) => d.active).length;
  }

  protected openScheduleDrawer(doctor: DoctorAccountResponse): void {
    this.selectedDoctor.set(doctor);
    this.isDrawerOpen.set(true);
    this.assignmentForm.setValue({ specialty: doctor.specialty ?? '', roomId: doctor.roomId ?? '' });
    this.schedules.set([]);
    this.notice.set('');
    this.error.set('');
    this.authApi.getDoctorSchedules(doctor.staffId).subscribe({
      next: (schedules) => this.schedules.set(schedules),
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected selectDoctor(doctor: DoctorAccountResponse): void {
    this.openScheduleDrawer(doctor);
  }

  protected closeDrawer(): void {
    this.isDrawerOpen.set(false);
    this.selectedDoctor.set(null);
    this.error.set('');
  }

  protected openCreateDoctor(): void {
    this.error.set('');
    this.notice.set('');
    this.createDoctorForm.reset({ username: '', fullName: '', password: '' });
    this.createDoctorOpen.set(true);
  }

  protected closeCreateDoctor(): void {
    if (!this.saving()) {
      this.createDoctorOpen.set(false);
      this.error.set('');
    }
  }

  protected closeError(): void {
    this.error.set('');
  }

  protected closeNotice(): void {
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
    this.notice.set('');
  }

  private showNotice(message: string): void {
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
    this.notice.set(message);
    this.noticeTimer = setTimeout(() => {
      this.notice.set('');
      this.noticeTimer = null;
    }, 5000);
  }

  protected createDoctor(): void {
    if (this.createDoctorForm.invalid) {
      this.createDoctorForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const request = this.createDoctorForm.getRawValue() as CreateDoctorRequest;
    this.authApi.createDoctor(request).subscribe({
      next: (doctor) => {
        this.doctors.update((items) => [...items, doctor].sort((a, b) => a.fullName.localeCompare(b.fullName)));
        this.createDoctorOpen.set(false);
        this.saving.set(false);
        this.showNotice(`Đã tạo tài khoản cho ${doctor.fullName}.`);
        this.selectDoctor(doctor);
      },
      error: (response) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected availableRooms(): ClinicRoomResponse[] {
    const specialty = this.assignmentForm.controls.specialty.value;
    return this.rooms().filter((room) => !specialty || room.specialty.toLocaleLowerCase() === specialty.toLocaleLowerCase());
  }

  protected saveAssignment(): void {
    const doctor = this.selectedDoctor();
    if (!doctor || !this.assignmentForm.controls.roomId.value) {
      this.assignmentForm.controls.roomId.markAsTouched();
      return;
    }
    const roomId = this.assignmentForm.controls.roomId.value;
    const selectedRoom = this.rooms().find((r) => r.id === roomId);
    const specialty = selectedRoom?.specialty ?? this.assignmentForm.controls.specialty.value ?? doctor.specialty ?? 'Khám Tổng Quát';

    this.saving.set(true);
    this.error.set('');
    this.authApi.assignDoctor(doctor.staffId, specialty, roomId).subscribe({
      next: (assigned) => {
        const updated = { ...doctor, specialty: assigned.specialty, roomId: assigned.roomId, roomCode: assigned.roomCode,
          roomName: assigned.roomName, assigned: true, active: assigned.active };
        this.doctors.update((items) => items.map((item) => item.staffId === doctor.staffId ? updated : item));
        this.selectedDoctor.set(updated);
        this.saving.set(false);
        this.showNotice('Đã lưu phân công bác sĩ thành công.');
      },
      error: (response) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected addSchedule(): void {
    const doctor = this.selectedDoctor();
    if (!doctor?.assigned || this.scheduleForm.invalid) { this.scheduleForm.markAllAsTouched(); return; }
    this.saving.set(true);
    this.error.set('');
    this.authApi.addDoctorSchedule(doctor.staffId, this.scheduleForm.getRawValue()).subscribe({
      next: (schedule) => {
        this.schedules.update((items) => [...items, schedule].sort((a, b) => `${a.dayOfWeek}${a.startTime}`.localeCompare(`${b.dayOfWeek}${b.startTime}`)));
        this.saving.set(false);
        this.showNotice('Đã thêm khung giờ làm việc.');
      },
      error: (response) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected removeSchedule(schedule: DoctorScheduleResponse): void {
    const doctor = this.selectedDoctor();
    if (!doctor) return;
    this.authApi.removeDoctorSchedule(doctor.staffId, schedule.id).subscribe({
      next: () => {
        this.schedules.update((items) => items.filter((item) => item.id !== schedule.id));
        this.showNotice('Đã xóa khung giờ làm việc.');
      },
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected readonly selectedDoctorIds = signal<Set<string>>(new Set());
  protected isAllSelected(): boolean {
    const list = this.filteredDoctors();
    return list.length > 0 && list.every((d) => this.selectedDoctorIds().has(d.staffId));
  }

  protected toggleSelectAll(): void {
    if (this.isAllSelected()) {
      this.selectedDoctorIds.set(new Set());
    } else {
      this.selectedDoctorIds.set(new Set(this.filteredDoctors().map((d) => d.staffId)));
    }
  }

  protected toggleSelectDoctor(staffId: string, event: Event): void {
    event.stopPropagation();
    const current = new Set(this.selectedDoctorIds());
    if (current.has(staffId)) {
      current.delete(staffId);
    } else {
      current.add(staffId);
    }
    this.selectedDoctorIds.set(current);
  }

  protected onDutyCount(): number {
    return Math.max(1, Math.round(this.assignedDoctorsCount() * 0.4));
  }

  protected dayLabel(value: string): string {
    return this.days.find((day) => day.value === value)?.label ?? value;
  }

  protected roomName(roomId: string | null): string {
    return this.rooms().find((room) => room.id === roomId)?.name ?? 'Chưa gán phòng';
  }

  protected getSpecialtyBadgeClass(specialty: string | null): string {
    if (!specialty) return 'bg-slate-100 text-slate-600 border-slate-200';
    const s = specialty.toLowerCase();
    if (s.includes('tổng quát')) return 'bg-amber-50 text-amber-800 border-amber-200';
    if (s.includes('mắt')) return 'bg-indigo-50 text-indigo-800 border-indigo-200';
    if (s.includes('nhi')) return 'bg-sky-50 text-sky-800 border-sky-200';
    if (s.includes('tim')) return 'bg-rose-50 text-rose-800 border-rose-200';
    if (s.includes('da')) return 'bg-purple-50 text-purple-800 border-purple-200';
    if (s.includes('tai')) return 'bg-emerald-50 text-emerald-800 border-emerald-200';
    return 'bg-teal-50 text-teal-800 border-teal-200';
  }
}
