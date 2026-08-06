import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

interface AccountNavItem {
  readonly label: string;
  readonly route: string;
  readonly icon: string;
}

@Component({
  selector: 'app-account-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatIconModule],
  templateUrl: './account-nav.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountNav {
  protected readonly items: readonly AccountNavItem[] = [
    { label: 'Lịch hẹn của tôi', route: '/appointments', icon: 'event_available' },
    { label: 'Hồ sơ bệnh nhân', route: '/account', icon: 'badge' },
    { label: 'Đổi mật khẩu', route: '/change-password', icon: 'lock' },
    { label: 'Phiếu khám bệnh', route: '/medical-records', icon: 'receipt_long' },
    { label: 'Thông báo', route: '/notifications', icon: 'notifications_none' },
  ];
}
