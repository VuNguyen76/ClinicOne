import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
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
  isCollapsed = signal(false);

  navItems = [
    { icon: 'dashboard', label: 'Tổng quan', route: '/dashboard' },
    { icon: 'calendar_today', label: 'Lịch hẹn', route: '/appointment' },
    { icon: 'people', label: 'Bệnh nhân', route: '/patient' },
    { icon: 'queue_play_next', label: 'Hàng đợi', route: '/queue' },
    { icon: 'medical_services', label: 'Phiếu khám', route: '/examination' },
  ];

  toggleSidebar() {
    this.isCollapsed.update(v => !v);
  }
}
