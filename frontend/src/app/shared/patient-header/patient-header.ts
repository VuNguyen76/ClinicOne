import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AccountMenu } from '../account-menu/account-menu';

@Component({
  selector: 'app-patient-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatIconModule, AccountMenu],
  templateUrl: './patient-header.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientHeader {
  readonly printHidden = input(false);
  readonly mobileMenuOpen = signal(false);

  readonly publicLinks = [
    { label: 'Trang chủ', route: '/home' },
    { label: 'Giới thiệu', route: '/about' },
    { label: 'Quy trình', route: '/process' },
    { label: 'Hướng dẫn', route: '/common-issues' },
    { label: 'Thắc mắc', route: '/support' },
    { label: 'Liên hệ', route: '/contact' },
  ];

  toggleMenu(): void {
    this.mobileMenuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.mobileMenuOpen.set(false);
  }
}
