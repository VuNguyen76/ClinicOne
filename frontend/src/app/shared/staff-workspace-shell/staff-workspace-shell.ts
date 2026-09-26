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
  exact?: boolean;
};

type StaffNavigationGroup = {
  label: string;
  items: StaffNavigationItem[];
};

const NAVIGATION: StaffNavigationGroup[] = [
  {
    label: 'Tiếp nhận',
    items: [
      { label: 'Tổng quan', route: '/reception', icon: 'space_dashboard', roles: ['RECEPTIONIST'], exact: true },
      { label: 'Lịch hẹn', route: '/reception/appointments', icon: 'event_available', roles: ['RECEPTIONIST'] },
      { label: 'Đón tiếp tại quầy', route: '/reception/walk-in', icon: 'how_to_reg', roles: ['RECEPTIONIST'] },
      { label: 'Hàng đợi quầy', route: '/reception/queue', icon: 'confirmation_number', roles: ['RECEPTIONIST'] },
      { label: 'Xử lý ngoại lệ', route: '/reception/exceptions', icon: 'warning_amber', roles: ['RECEPTIONIST'] },
      { label: 'Hồ sơ bệnh nhân', route: '/reception/profiles', icon: 'folder_shared', roles: ['RECEPTIONIST'] },
    ],
  },
  {
    label: 'Khám bệnh',
    items: [
      { label: 'Phòng khám của tôi', route: '/doctor', icon: 'stethoscope', roles: ['DOCTOR'] },
    ],
  },
  {
    label: 'Lịch làm việc',
    items: [
      { label: 'Thời khóa biểu', route: '/admin/schedule-templates', icon: 'calendar_month', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Báo vắng & Nghỉ phép', route: '/admin/doctor-time-off', icon: 'event_busy', roles: ['ADMIN', 'COORDINATOR', 'DOCTOR'] },
    ],
  },
  {
    label: 'Vận hành',
    items: [
      { label: 'Điều phối hàng đợi', route: '/admin/queues', icon: 'alt_route', roles: ['COORDINATOR'] },
      { label: 'Phòng khám', route: '/admin/rooms', icon: 'meeting_room', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Bác sĩ', route: '/admin/doctors', icon: 'medical_services', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Điều chuyển lịch hẹn', route: '/admin/rescheduling', icon: 'edit_calendar', roles: ['ADMIN', 'COORDINATOR'] },
    ],
  },
  {
    label: 'Chuyên môn',
    items: [
      { label: 'Dược phẩm & Thuốc', route: '/admin/medications', icon: 'medication', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Chẩn đoán', route: '/admin/diagnoses', icon: 'clinical_notes', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Mẫu bệnh án', route: '/admin/medical-record-templates', icon: 'assignment', roles: ['ADMIN', 'COORDINATOR', 'DOCTOR'] },
    ],
  },
  {
    label: 'Dịch vụ & Khoa',
    items: [
      { label: 'Dịch vụ', route: '/admin/services', icon: 'medical_information', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Chuyên khoa', route: '/admin/specialties', icon: 'local_hospital', roles: ['ADMIN', 'COORDINATOR'] },
    ],
  },
  {
    label: 'Báo cáo & Kiểm soát',
    items: [
      { label: 'Báo cáo thống kê', route: '/admin/statistics', icon: 'analytics', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Đối soát dữ liệu', route: '/admin/reconciliations', icon: 'fact_check', roles: ['ADMIN', 'COORDINATOR'] },
      { label: 'Nhật ký thao tác', route: '/admin/business-audit', icon: 'history', roles: ['ADMIN'] },
      { label: 'Nhật ký bảo mật', route: '/admin/access-audit', icon: 'security', roles: ['ADMIN'] },
    ],
  },
  {
    label: 'Hệ thống',
    items: [
      { label: 'Nhân sự & Phân quyền', route: '/admin/staff', icon: 'manage_accounts', roles: ['ADMIN'] },
      { label: 'Nhật ký SMS', route: '/admin/sms-deliveries', icon: 'sms', roles: ['ADMIN'] },
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

  protected handleNavigationClick(event: MouseEvent): void {
    // Pointer navigation must not leave an item focused after the destination
    // view is rendered; that focus restoration can pull the page unexpectedly.
    const item = event.currentTarget;
    if (event.detail > 0 && item instanceof HTMLElement) item.blur();
    this.closeNavigation();
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
  if (roles.includes('COORDINATOR')) return '/admin/queues';
  if (roles.includes('ADMIN')) return '/admin/rooms';
  return '/reception';
}
