import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AccessAuditResponse, AuthApiService, apiErrorMessage } from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-access-audit-management',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
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

  protected totalEventsCount(): number {
    return this.events().length;
  }

  protected successEventsCount(): number {
    return this.events().filter((e) => e.outcome === 'SUCCESS').length;
  }

  protected failureEventsCount(): number {
    return this.events().filter((e) => e.outcome !== 'SUCCESS').length;
  }

  ngOnInit(): void { this.refresh(); }

  protected refresh(): void {
    this.loading.set(true);
    this.error.set('');
    this.authApi.getAccessAudit({ from: this.from() ? `${this.from()}T00:00:00Z` : undefined, to: this.to() ? `${this.to()}T23:59:59Z` : undefined, actor: this.actor(), outcome: this.outcome(), eventType: this.eventType() }).subscribe({
      next: (items) => { this.events.set(items); this.loading.set(false); },
      error: (response) => { this.loading.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected formatActor(actor: string): string {
    if (!actor) return '—';
    if (/^[0-9a-fA-F-]{36}$/.test(actor)) {
      return `Phiên thao tác (${actor.slice(0, 8)})`;
    }
    return actor;
  }

  protected formatDate(value: string): string { return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)); }
}
