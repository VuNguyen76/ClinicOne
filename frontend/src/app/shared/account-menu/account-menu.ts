import { ChangeDetectionStrategy, Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-account-menu',
  standalone: true,
  imports: [RouterLink, MatIconModule],
  templateUrl: './account-menu.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountMenu {
  protected readonly loggedIn = signal(false);
  protected readonly menuOpen = signal(false);
  protected readonly staffRole = signal<string | null>(null);
  protected readonly staffRoles = signal<string[]>([]);
  protected readonly fullName = signal('Tài khoản');
  protected readonly unreadNotifications = signal(0);
  private readonly authApi = inject(AuthApiService, { optional: true });

  constructor(
    private readonly router: Router,
    private readonly elementRef: ElementRef<HTMLElement>,
  ) {
    const token = typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneAccessToken');
    const name = typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOnePatientName');
    const role = typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneStaffRole');
    const rolesRaw = typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneStaffRoles');
    this.loggedIn.set(Boolean(token));
    if (name) {
      this.fullName.set(name);
    }
    this.staffRole.set(role);
    this.staffRoles.set(this.parseRoles(rolesRaw, role));
    // The header badge is hydrated on the patient home page. Other pages load
    // their own notification data and should not start an extra request while
    // rendering shared navigation.
    if (token && !role && this.authApi && this.router.url === '/home') {
      this.authApi.getUnreadNotificationCount().subscribe({
        next: (result) => this.unreadNotifications.set(result.count),
      });
    }
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected isActive(url: string): boolean {
    return this.router.isActive(url, {
      paths: 'subset',
      queryParams: 'ignored',
      fragment: 'ignored',
      matrixParams: 'ignored',
    });
  }

  protected isStaff(): boolean {
    return Boolean(this.staffRole());
  }

  protected isDoctor(): boolean {
    return this.staffRoles().includes('DOCTOR');
  }

  protected canManageRooms(): boolean {
    return this.staffRoles().some((role) => role === 'ADMIN' || role === 'COORDINATOR');
  }

  protected canReceivePatients(): boolean {
    return this.staffRoles().some((role) => ['ADMIN', 'COORDINATOR', 'RECEPTIONIST'].includes(role));
  }

  protected staffRoleLabel(): string {
    switch (this.staffRole()) {
      case 'ADMIN': return 'Quản trị viên';
      case 'COORDINATOR': return 'Điều phối viên';
      case 'RECEPTIONIST': return 'Nhân viên tiếp nhận';
      case 'DOCTOR': return 'Bác sĩ';
      default: return 'Nhân viên';
    }
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    this.closeMenu();
  }

  @HostListener('document:pointerdown', ['$event'])
  protected closeOnOutsideClick(event: PointerEvent): void {
    if (this.menuOpen() && !this.elementRef.nativeElement.contains(event.target as Node)) {
      this.closeMenu();
    }
  }

  protected logout(): void {
    const logoutRequest = this.staffRole() ? this.authApi?.logoutStaff() : this.authApi?.logoutPatient();
    if (logoutRequest) {
      logoutRequest.subscribe({ complete: () => this.finishLogout(), error: () => this.finishLogout() });
      return;
    }
    this.finishLogout();
  }

  private finishLogout(): void {
    sessionStorage.removeItem('clinicOneAccessToken');
    sessionStorage.removeItem('clinicOnePatientName');
    sessionStorage.removeItem('clinicOneSessionType');
    sessionStorage.removeItem('clinicOneStaffRole');
    sessionStorage.removeItem('clinicOneStaffRoles');
    sessionStorage.removeItem('clinicOneBookingSession');
    this.menuOpen.set(false);
    this.loggedIn.set(false);
    void this.router.navigateByUrl('/home');
  }

  private parseRoles(raw: string | null, fallback: string | null): string[] {
    if (raw) {
      try {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) return parsed.filter((value): value is string => typeof value === 'string');
      } catch { /* use legacy role */ }
    }
    return fallback ? [fallback] : [];
  }
}
