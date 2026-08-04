import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

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
  protected readonly fullName = signal('Tài khoản');

  constructor(private readonly router: Router) {
    const token = typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOneAccessToken');
    const name = typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('clinicOnePatientName');
    this.loggedIn.set(Boolean(token));
    if (name) {
      this.fullName.set(name);
    }
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected logout(): void {
    sessionStorage.removeItem('clinicOneAccessToken');
    sessionStorage.removeItem('clinicOnePatientName');
    this.menuOpen.set(false);
    this.loggedIn.set(false);
    void this.router.navigateByUrl('/home');
  }
}
