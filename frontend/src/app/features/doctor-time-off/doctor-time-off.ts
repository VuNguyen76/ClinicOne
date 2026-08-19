import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, DoctorAccountResponse, DoctorTimeOffResponse, apiErrorMessage } from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { clinicTodayIso } from '../../core/time/clinic-time';
import { hasStaffRole } from '../../core/auth/auth.guard';

@Component({
  selector: 'app-doctor-time-off-management',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './doctor-time-off.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DoctorTimeOffManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly today = clinicTodayIso();
  protected readonly doctors = signal<DoctorAccountResponse[]>([]);
  protected readonly records = signal<DoctorTimeOffResponse[]>([]);
  protected readonly selectedDoctorId = signal('');
  protected readonly startDate = signal(this.today);
  protected readonly endDate = signal(this.today);
  protected readonly reason = signal('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly modalOpen = signal(false);
  protected readonly searchTerm = signal('');

  protected filteredRecords(): DoctorTimeOffResponse[] {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.records();
    return this.records().filter((r) =>
      r.doctorName.toLowerCase().includes(q) ||
      r.reason.toLowerCase().includes(q)
    );
  }

  protected totalRecordsCount(): number {
    return this.records().length;
  }

  protected affectedDoctorsCount(): number {
    return new Set(this.records().map((r) => r.doctorId)).size;
  }

  protected cancelledSlotsTotal(): number {
    return this.records().reduce((sum, r) => sum + (r.affectedAppointmentCount || 0), 0);
  }

  ngOnInit(): void {
    this.loadData();
  }

  protected loadData(): void {
    this.loading.set(true);
    this.authApi.getDoctors().subscribe({
      next: (doctors) => {
        const activeAssigned = doctors.filter((item) => item.active && item.assigned);
        this.doctors.set(activeAssigned);
        if (activeAssigned[0] && !this.selectedDoctorId()) {
          this.selectedDoctorId.set(activeAssigned[0].staffId);
        }
        this.loading.set(false);
      },
      error: (response) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
    this.authApi.getDoctorTimeOffs().subscribe({
      next: (items) => this.records.set(items),
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected submit(): void {
    if (!this.canManageTimeOff()) {
      this.error.set('Chỉ điều phối viên được ghi nhận lịch nghỉ của bác sĩ.');
      return;
    }
    if (!this.selectedDoctorId() || !this.startDate() || !this.endDate() || this.startDate() > this.endDate() || this.reason().trim().length < 10) {
      this.error.set('Chọn bác sĩ, khoảng ngày hợp lệ và nhập lý do từ 10 ký tự.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.authApi.createDoctorTimeOff({
      doctorId: this.selectedDoctorId(),
      startDate: this.startDate(),
      endDate: this.endDate(),
      reason: this.reason().trim(),
    }).subscribe({
      next: (item) => {
        this.records.update((items) => [item, ...items]);
        this.notice.set(`Đã khóa ${item.lockedSlotCount} khung giờ và mở ${item.affectedAppointmentCount} lịch cần sắp xếp lại.`);
        this.reason.set('');
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
    if (!this.canManageTimeOff()) return;
    this.error.set('');
    this.notice.set('');
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    if (this.saving()) return;
    this.modalOpen.set(false);
  }

  protected doctorName(id: string): string {
    return this.doctors().find((item) => item.staffId === id)?.fullName ?? id;
  }

  protected formatDate(value: string): string {
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN').format(new Date(year, month - 1, day));
  }

  protected canManageTimeOff(): boolean {
    return hasStaffRole('COORDINATOR');
  }
}
