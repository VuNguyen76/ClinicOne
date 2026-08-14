import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AccountMenu } from '../account-menu/account-menu';

type StaffRole = 'ADMIN' | 'COORDINATOR' | 'RECEPTIONIST' | 'DOCTOR';

type StaffNavigationItem = {
  label: string;
  route: string;
  icon: string;
  roles: StaffRole[];
};

type StaffNavigationGroup = {
  label: string;
  items: StaffNavigationItem[];
};

const NAVIGATION: StaffNavigationGroup[] = [
  {
    label: 'Khám bệnh',
    items: [
      { label: 'Hàng đợi khám bệnh', route: '/doctor', icon: 'monitor_heart', roles: ['DOCTOR'] },
      { label: 'Tiếp nhận bệnh nhân', route: '/reception/check-in', icon: 'how_to_reg', roles: ['ADMIN', 'COORDINATOR', 'RECEPTIONIST'] },
    ],
  },
  {
    label: 'Vận hành',
    items: [
      { label: 'Phòng khám', route: '/admin/rooms', icon: 'meeting_room', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Bác sĩ', route: '/admin/doctors', icon: 'medical_services', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Lịch làm việc', route: '/admin/schedule-templates', icon: 'calendar_month', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Sắp xếp lại lịch', route: '/admin/rescheduling', icon: 'event_repeat', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Nghỉ đột xuất', route: '/admin/doctor-time-off', icon: 'event_busy', roles: ['ADMIN', 'COORDINATOR'] },
    ],
  },
  {
    label: 'Danh mục',
    items: [
      { label: 'Dịch vụ khám', route: '/admin/services', icon: 'category', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Chuyên khoa', route: '/admin/specialties', icon: 'domain', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Mẫu phiếu khám', route: '/admin/medical-record-templates', icon: 'description', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Thuốc', route: '/admin/medications', icon: 'medication', roles: ['ADMIN'] },
      { label: 'Chẩn đoán', route: '/admin/diagnoses', icon: 'clinical_notes', roles: ['ADMIN'] },
      { label: 'Lý do vận hành', route: '/admin/reason-catalog', icon: 'list_alt', roles: ['ADMIN'] },
    ],
  },
  {
    label: 'Kiểm soát',
    items: [
      { label: 'Thống kê', route: '/admin/statistics', icon: 'analytics', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Đối soát sai sót', route: '/admin/reconciliations', icon: 'fact_check', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Lịch sử công việc', route: '/admin/business-audit', icon: 'history', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Nhật ký truy cập', route: '/admin/access-audit', icon: 'manage_search', roles: ['ADMIN'] },
    ],
  },
  {
    label: 'Hệ thống',
    items: [
      { label: 'Tài khoản nhân viên', route: '/admin/staff', icon: 'manage_accounts', roles: ['ADMIN'] },
      { label: 'Cấu hình phòng khám', route: '/admin/configuration', icon: 'tune', roles: ['ADMIN'] },
      { label: 'Tin nhắn SMS', route: '/admin/sms-deliveries', icon: 'sms', roles: ['ADMIN'] },
    ],
  },
];

@Component({
  selector: 'app-staff-workspace-shell',
  standalone: true,
  imports: [MatIconModule, RouterLink, RouterLinkActive, AccountMenu],
  templateUrl: './staff-workspace-shell.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StaffWorkspaceShell {
  readonly moduleTitle = input.required<string>();
  readonly pageTitle = input.required<string>();
  protected readonly navigationOpen = signal(false);
  protected readonly functionQuery = signal('');
  private readonly roles = readStaffRoles();
  protected readonly roleLabel = roleLabel(this.roles[0]);
  protected readonly landingRoute = staffLandingRoute(this.roles);
  protected readonly visibleNavigation = computed(() => {
    const query = this.functionQuery().trim().toLocaleLowerCase('vi-VN');
    return NAVIGATION.map((group) => ({
      ...group,
      items: group.items.filter((item) => item.roles.some((role) => this.roles.includes(role)))
        .filter((item) => !query || item.label.toLocaleLowerCase('vi-VN').includes(query)),
    })).filter((group) => group.items.length > 0);
  });

  protected toggleNavigation(): void {
    this.navigationOpen.update((open) => !open);
  }

  protected closeNavigation(): void {
    this.navigationOpen.set(false);
  }

  protected updateFunctionQuery(event: Event): void {
    this.functionQuery.set((event.target as HTMLInputElement).value);
  }
}

function readStaffRoles(): StaffRole[] {
  if (typeof sessionStorage === 'undefined') return [];
  const raw = sessionStorage.getItem('clinicOneStaffRoles');
  const fallback = sessionStorage.getItem('clinicOneStaffRole');
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed.filter(isStaffRole);
    } catch {
      // Fall back to the primary role stored by older sessions.
    }
  }
  return isStaffRole(fallback) ? [fallback] : [];
}

function isStaffRole(value: unknown): value is StaffRole {
  return typeof value === 'string' && ['ADMIN', 'COORDINATOR', 'RECEPTIONIST', 'DOCTOR'].includes(value);
}

function roleLabel(role: StaffRole | undefined): string {
  switch (role) {
    case 'ADMIN': return 'Quản trị viên';
    case 'COORDINATOR': return 'Điều phối viên';
    case 'RECEPTIONIST': return 'Nhân viên tiếp nhận';
    case 'DOCTOR': return 'Bác sĩ';
    default: return 'Nhân viên';
  }
}

function staffLandingRoute(roles: StaffRole[]): string {
  if (roles.includes('DOCTOR')) return '/doctor';
  if (roles.some((role) => role === 'ADMIN' || role === 'COORDINATOR')) return '/admin/rooms';
  return '/reception/check-in';
}
