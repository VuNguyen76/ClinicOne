import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { PatientHeader } from '../../shared/patient-header/patient-header';
import { AccountNav } from '../../shared/account-nav/account-nav';
import { apiErrorMessage, AuthApiService, PatientNotificationResponse } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [RouterLink, MatIconModule, PatientHeader, AccountNav],
  templateUrl: './notifications.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Notifications implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  protected readonly notifications = signal<PatientNotificationResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly filter = signal<'all' | 'unread'>('all');
  protected readonly markingAll = signal(false);

  protected readonly unreadCount = computed(() => this.notifications().filter((n) => !n.read).length);

  protected readonly filteredNotifications = computed(() => {
    if (this.filter() === 'unread') {
      return this.notifications().filter((n) => !n.read);
    }
    return this.notifications();
  });

  ngOnInit(): void {
    this.authApi.getNotifications().subscribe({
      next: (items) => {
        this.notifications.set(items);
        this.loading.set(false);
      },
      error: (error) => {
        this.error.set(apiErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected markAllRead(): void {
    if (this.unreadCount() === 0 || this.markingAll()) return;
    this.markingAll.set(true);
    this.authApi.markAllNotificationsRead().subscribe({
      next: () => {
        this.notifications.update((items) => items.map((item) => ({ ...item, read: true })));
        this.markingAll.set(false);
      },
      error: () => this.markingAll.set(false),
    });
  }

  protected open(notification: PatientNotificationResponse): void {
    if (!notification.read) {
      this.authApi.markNotificationRead(notification.id).subscribe({
        next: () => this.notifications.update((items) => items.map((item) =>
          item.id === notification.id ? { ...item, read: true } : item)),
      });
    }
    if (notification.targetUrl) {
      void this.router.navigateByUrl(notification.targetUrl);
    }
  }

  protected formatDate(value: string): string {
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
  }
}
