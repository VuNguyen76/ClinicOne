import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatIconModule,
    MatButtonModule,
    MatListModule,
    MatMenuModule,
    MatTooltipModule
  ],
  templateUrl: './app-shell.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {
  private readonly router = inject(Router);
  isCollapsed = signal(false);

  navItems = [
    { icon: 'dashboard', label: 'Tổng quan', route: '/dashboard' },
    { icon: 'calendar_today', label: 'Lịch hẹn', route: '/appointments' },
    { icon: 'people', label: 'Hồ sơ người đi khám', route: '/patient-profiles' },
    { icon: 'medical_services', label: 'Phiếu khám', route: '/medical-records' },
    { icon: 'account_circle', label: 'Tài khoản', route: '/account' },
  ];

  toggleSidebar() {
    this.isCollapsed.update(v => !v);
  }

  logout(): void {
    const isStaff = sessionStorage.getItem('clinicOneSessionType') === 'STAFF'
      || Boolean(sessionStorage.getItem('clinicOneStaffRole'))
      || Boolean(sessionStorage.getItem('clinicOneStaffRoles'));
    sessionStorage.clear();
    void this.router.navigateByUrl(isStaff ? '/staff/login' : '/login');
  }
}
