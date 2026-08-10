import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AccessAuditResponse, AuthApiService, apiErrorMessage } from '../../core/auth/auth-api.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';

@Component({
  selector: 'app-access-audit-management',
  standalone: true,
  imports: [FormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './access-audit.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessAuditManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly events = signal<AccessAuditResponse[]>([]);
  protected readonly actor = signal('');
  protected readonly outcome = signal('');
  protected readonly eventType = signal('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  ngOnInit(): void { this.refresh(); }

  protected refresh(): void {
    this.loading.set(true);
    this.error.set('');
    this.authApi.getAccessAudit({ from: this.from() ? `${this.from()}T00:00:00Z` : undefined, to: this.to() ? `${this.to()}T23:59:59Z` : undefined, actor: this.actor(), outcome: this.outcome(), eventType: this.eventType() }).subscribe({
      next: (items) => { this.events.set(items); this.loading.set(false); },
      error: (response) => { this.loading.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected formatDate(value: string): string { return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)); }
}
