import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
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
import { AccountMenu } from '../../shared/account-menu/account-menu';

@Component({
  selector: 'app-doctor-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu],
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
        if (data.doctors[0]) this.selectDoctor(data.doctors[0]);
      },
      error: (response) => { this.loading.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected selectDoctor(doctor: DoctorAccountResponse): void {
    this.selectedDoctor.set(doctor);
    this.assignmentForm.setValue({ specialty: doctor.specialty ?? '', roomId: doctor.roomId ?? '' });
    this.schedules.set([]);
    this.notice.set('');
    this.authApi.getDoctorSchedules(doctor.staffId).subscribe({
      next: (schedules) => this.schedules.set(schedules),
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected openCreateDoctor(): void {
    this.error.set('');
    this.notice.set('');
    this.createDoctorForm.reset({ username: '', fullName: '', password: '' });
    this.createDoctorOpen.set(true);
  }

  protected closeCreateDoctor(): void {
    if (!this.saving()) this.createDoctorOpen.set(false);
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
        this.notice.set(`Đã tạo tài khoản cho ${doctor.fullName}. Tiếp tục phân công chuyên khoa và phòng.`);
        this.selectDoctor(doctor);
      },
      error: (response) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected availableRooms(): ClinicRoomResponse[] {
    const specialty = this.assignmentForm.controls.specialty.value;
    return this.rooms().filter((room) => !specialty || room.specialty.toLocaleLowerCase() === specialty.toLocaleLowerCase());
  }

  protected saveAssignment(): void {
    const doctor = this.selectedDoctor();
    if (!doctor || this.assignmentForm.invalid) { this.assignmentForm.markAllAsTouched(); return; }
    this.saving.set(true);
    this.error.set('');
    const value = this.assignmentForm.getRawValue();
    this.authApi.assignDoctor(doctor.staffId, value.specialty, value.roomId).subscribe({
      next: (assigned) => {
        const updated = { ...doctor, specialty: assigned.specialty, roomId: assigned.roomId, roomCode: assigned.roomCode,
          roomName: assigned.roomName, assigned: true, active: assigned.active };
        this.doctors.update((items) => items.map((item) => item.staffId === doctor.staffId ? updated : item));
        this.selectedDoctor.set(updated);
        this.saving.set(false);
        this.notice.set('Đã lưu phân công bác sĩ.');
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
        this.notice.set('Đã thêm giờ làm.');
      },
      error: (response) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected removeSchedule(schedule: DoctorScheduleResponse): void {
    const doctor = this.selectedDoctor();
    if (!doctor) return;
    this.authApi.removeDoctorSchedule(doctor.staffId, schedule.id).subscribe({
      next: () => this.schedules.update((items) => items.filter((item) => item.id !== schedule.id)),
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected dayLabel(value: string): string {
    return this.days.find((day) => day.value === value)?.label ?? value;
  }

  protected roomName(roomId: string | null): string {
    return this.rooms().find((room) => room.id === roomId)?.name ?? 'Chưa gán phòng';
  }
}
