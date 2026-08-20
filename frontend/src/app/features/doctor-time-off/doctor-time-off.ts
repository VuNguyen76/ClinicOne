import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
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

  private readonly doctorAvatarMap: Record<string, string> = {
    'nguyễn an': 'https://images.unsplash.com/photo-1622253692010-333f2da6031d?w=150&auto=format&fit=crop&q=80',
    'trần minh': 'https://images.unsplash.com/photo-1537368910025-700350fe46c7?w=150&auto=format&fit=crop&q=80',
    'lê thu hà': 'https://images.unsplash.com/photo-1594824813589-32212356c382?w=150&auto=format&fit=crop&q=80',
    'phạm quốc dũng': 'https://images.unsplash.com/photo-1612349317150-e413f6a5b16d?w=150&auto=format&fit=crop&q=80',
    'hoàng thanh nga': 'https://images.unsplash.com/photo-1559839734-2b71ea197ec2?w=150&auto=format&fit=crop&q=80',
    'vũ đình toàn': 'https://images.unsplash.com/photo-1582750433449-648ed127bb54?w=150&auto=format&fit=crop&q=80',
    'đặng mai lan': 'https://images.unsplash.com/photo-1651008376811-b90baee60c1f?w=150&auto=format&fit=crop&q=80',
  };

  protected readonly filteredRecords = computed<DoctorTimeOffResponse[]>(() => {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.records();
    return this.records().filter((r) =>
      r.doctorName.toLowerCase().includes(q) ||
      r.reason.toLowerCase().includes(q)
    );
  });

  protected readonly totalRecordsCount = computed(() => this.records().length);

  protected readonly affectedDoctorsCount = computed(() => new Set(this.records().map((r) => r.doctorId)).size);

  protected readonly cancelledSlotsTotal = computed(() => this.records().reduce((sum, r) => sum + (r.affectedAppointmentCount || 0), 0));

  protected isFemaleDoctor(doctorName: string): boolean {
    const lower = (doctorName || '').toLowerCase();
    return lower.includes('hà') || lower.includes('nga') || lower.includes('lan') || lower.includes('thảo') || lower.includes('mai');
  }

  protected getDoctorSvgAvatar(doctorName: string): string {
    if (this.isFemaleDoctor(doctorName)) {
      return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120"><circle cx="60" cy="60" r="60" fill="%23e0f2fe"/><circle cx="60" cy="46" r="22" fill="%23fed7aa"/><path d="M38 42c0-12 10-20 22-20s22 8 22 20c0 4-2 10-4 12-2-8-8-12-18-12s-16 4-18 12c-2-2-4-8-4-12z" fill="%23334155"/><path d="M60 72c-20 0-36 14-36 34v14h72v-14c0-20-16-34-36-34z" fill="%23ffffff"/><path d="M48 72l12 24 12-24" fill="%230284c7"/><path d="M42 86c0 10 8 18 18 18s18-8 18-18" fill="none" stroke="%23334155" stroke-width="3" stroke-linecap="round"/><circle cx="60" cy="104" r="3" fill="%230284c7"/><path d="M50 46c2 1 6 1 8 0m4 0c2 1 6 1 8 0" fill="none" stroke="%23334155" stroke-width="1.5" stroke-linecap="round"/><path d="M56 56c2 2 6 2 8 0" fill="none" stroke="%23f43f5e" stroke-width="2" stroke-linecap="round"/></svg>';
    }
    return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120"><circle cx="60" cy="60" r="60" fill="%23ccfbf1"/><circle cx="60" cy="46" r="22" fill="%23fde047" fill-opacity="0.6"/><path d="M38 40c0-14 10-22 22-22s22 8 22 22v4c-6-4-12-6-22-6s-16 2-22 6v-4z" fill="%231e293b"/><path d="M60 72c-20 0-36 14-36 34v14h72v-14c0-20-16-34-36-34z" fill="%23ffffff"/><path d="M48 72l12 24 12-24" fill="%230f766e"/><path d="M42 86c0 10 8 18 18 18s18-8 18-18" fill="none" stroke="%23334155" stroke-width="3" stroke-linecap="round"/><circle cx="60" cy="104" r="3" fill="%230f766e"/><path d="M50 46c2 1 6 1 8 0m4 0c2 1 6 1 8 0" fill="none" stroke="%23334155" stroke-width="1.5" stroke-linecap="round"/><path d="M56 56c2 2 6 2 8 0" fill="none" stroke="%23e11d48" stroke-width="1.5" stroke-linecap="round"/></svg>';
  }

  protected handleAvatarError(event: Event, doctorName: string): void {
    const target = event.target as HTMLImageElement;
    if (target) {
      target.src = this.getDoctorSvgAvatar(doctorName);
    }
  }

  protected getDoctorAvatar(doctorName: string): string {
    const name = (doctorName || '').toLowerCase().replace(/^(bs\.|ths\.|ckii|cki|bác sĩ|tiến sĩ|ts\.)\s*/i, '').trim();
    for (const [key, url] of Object.entries(this.doctorAvatarMap)) {
      if (name.includes(key) || key.includes(name)) return url;
    }
    return this.getDoctorSvgAvatar(doctorName);
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
