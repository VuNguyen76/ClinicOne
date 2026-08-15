import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import {
  ApiErrorResponse,
  AuthApiService,
  ClinicServiceRequest,
  ClinicServiceResponse,
  DoctorAccountResponse,
  SpecialtyOption,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { hasStaffRole } from '../../core/auth/auth.guard';

@Component({
  selector: 'app-service-management',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './service-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServiceManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);

  protected readonly services = signal<ClinicServiceResponse[]>([]);
  protected readonly doctors = signal<DoctorAccountResponse[]>([]);
  protected readonly specialties = signal<SpecialtyOption[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly editingId = signal<string | null>(null);
  protected readonly modalOpen = signal(false);
  protected readonly name = signal('');
  protected readonly specialty = signal('');
  protected readonly visitType = signal('Khám thường');
  protected readonly durationMinutes = signal(30);
  protected readonly selectedDoctorIds = signal<string[]>([]);
  protected readonly requiresMedicalRecord = signal(true);

  protected canManageServices(): boolean {
    return hasStaffRole('COORDINATOR');
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({
      services: this.authApi.getClinicServices(),
      doctors: this.authApi.getDoctors(),
      specialties: this.authApi.getSpecialties(),
    }).subscribe({
      next: (data) => {
        this.services.set(data.services);
        this.doctors.set(data.doctors);
        this.specialties.set(data.specialties);
        this.loading.set(false);
      },
      error: (response) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected availableDoctors(): DoctorAccountResponse[] {
    const specialty = this.specialty().trim().toLocaleLowerCase();
    return this.doctors().filter((doctor) => doctor.assigned && doctor.active
      && (!specialty || doctor.specialty?.toLocaleLowerCase() === specialty));
  }

  protected changeSpecialty(value: string): void {
    this.specialty.set(value);
    const available = new Set(this.availableDoctors().map((doctor) => doctor.staffId));
    this.selectedDoctorIds.update((ids) => ids.filter((id) => available.has(id)));
  }

  protected toggleDoctor(staffId: string, checked: boolean): void {
    this.selectedDoctorIds.update((ids) => checked
      ? (ids.includes(staffId) ? ids : [...ids, staffId])
      : ids.filter((id) => id !== staffId));
  }

  protected openCreate(): void {
    if (!this.canManageServices()) {
      this.error.set('Chỉ điều phối viên được thay đổi dịch vụ khám.');
      return;
    }
    this.editingId.set(null);
    this.name.set('');
    this.specialty.set('');
    this.visitType.set('Khám thường');
    this.durationMinutes.set(30);
    this.selectedDoctorIds.set([]);
    this.requiresMedicalRecord.set(true);
    this.error.set('');
    this.notice.set('');
    this.modalOpen.set(true);
  }

  protected edit(service: ClinicServiceResponse): void {
    if (!this.canManageServices()) {
      this.error.set('Chỉ điều phối viên được thay đổi dịch vụ khám.');
      return;
    }
    this.editingId.set(service.id);
    this.name.set(service.name);
    this.specialty.set(service.specialty);
    this.visitType.set(service.visitType);
    this.durationMinutes.set(service.durationMinutes);
    this.selectedDoctorIds.set(service.eligibleDoctors.map((doctor) => doctor.staffId));
    this.requiresMedicalRecord.set(service.requiresMedicalRecord !== false);
    this.error.set('');
    this.notice.set('');
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    if (this.saving()) return;
    this.modalOpen.set(false);
    this.editingId.set(null);
  }

  protected submit(): void {
    if (!this.canManageServices()) {
      this.error.set('Chỉ điều phối viên được lưu dịch vụ khám.');
      return;
    }
    const request: ClinicServiceRequest = {
      name: this.name().trim(),
      specialty: this.specialty().trim(),
      visitType: this.visitType().trim(),
      durationMinutes: Number(this.durationMinutes()),
      doctorIds: this.selectedDoctorIds(),
      requiresMedicalRecord: this.requiresMedicalRecord(),
    };
    if (!request.name || !request.specialty || !request.visitType || request.durationMinutes < 5
      || request.durationMinutes > 120 || request.doctorIds.length === 0) {
      this.error.set('Nhập đủ thông tin và chọn ít nhất một bác sĩ đủ điều kiện.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const request$ = this.editingId()
      ? this.authApi.updateClinicService(this.editingId()!, request)
      : this.authApi.createClinicService(request);
    request$.subscribe({
      next: (service) => {
        this.services.update((items) => this.editingId()
          ? items.map((item) => item.id === service.id ? service : item)
          : [...items, service].sort((a, b) => a.name.localeCompare(b.name)));
        this.saving.set(false);
        this.notice.set(this.editingId() ? 'Đã cập nhật dịch vụ khám.' : 'Đã tạo dịch vụ khám.');
        this.modalOpen.set(false);
        this.editingId.set(null);
      },
      error: (response) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected toggleActive(service: ClinicServiceResponse): void {
    if (!this.canManageServices()) {
      this.error.set('Chỉ điều phối viên được thay đổi trạng thái dịch vụ.');
      return;
    }
    this.authApi.setClinicServiceActive(service.id, !service.active).subscribe({
      next: (updated) => this.services.update((items) => items.map((item) => item.id === updated.id ? updated : item)),
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected isSelectedDoctor(staffId: string): boolean {
    return this.selectedDoctorIds().includes(staffId);
  }

  protected handleError(response: ApiErrorResponse): void {
    this.error.set(apiErrorMessage(response));
  }
}
