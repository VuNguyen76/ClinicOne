import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AuthApiService, QueueTicketResponse, apiErrorMessage } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';
import { clinicTodayIso } from '../../../core/time/clinic-time';

@Component({
  selector: 'app-queue-board',
  standalone: true,
  imports: [RouterLink, MatIconModule, AccountMenu, DecimalPipe],
  templateUrl: './queue-board.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueueBoard implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authApi = inject(AuthApiService);

  protected readonly roomCode = signal('');
  protected readonly queue = signal<QueueTicketResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly busyTicketId = signal('');
  protected readonly error = signal('');
  protected readonly today = clinicTodayIso();

  ngOnInit(): void {
    this.roomCode.set(this.route.snapshot.paramMap.get('roomCode') ?? '');
    this.refresh();
  }

  protected refresh(): void {
    this.loading.set(true);
    this.error.set('');
    this.authApi.getRoomQueue(this.roomCode(), this.today).subscribe({
      next: (tickets) => { this.queue.set(tickets); this.loading.set(false); },
      error: (response) => { this.loading.set(false); this.handleError(response); },
    });
  }

  protected act(ticket: QueueTicketResponse, action: 'call' | 'skip' | 'start' | 'complete'): void {
    this.busyTicketId.set(ticket.id);
    this.error.set('');
    const request = action === 'call' ? this.authApi.callQueueTicket(ticket.id)
      : action === 'skip' ? this.authApi.skipQueueTicket(ticket.id, 'Chưa có mặt khi được gọi')
        : action === 'start' ? this.authApi.startQueueTicket(ticket.id)
          : this.authApi.completeQueueTicket(ticket.id);
    request.subscribe({
      next: (updated) => {
        this.queue.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.busyTicketId.set('');
      },
      error: (response) => { this.busyTicketId.set(''); this.handleError(response); },
    });
  }

  protected formatTime(value: string): string {
    return value.slice(0, 5);
  }

  protected statusClass(status: string): string {
    if (status === 'CALLED') return 'bg-amber-50 text-amber-700';
    if (status === 'IN_SERVICE') return 'bg-violet-50 text-violet-700';
    if (status === 'COMPLETED') return 'bg-emerald-50 text-emerald-700';
    if (status === 'SKIPPED') return 'bg-slate-100 text-slate-600';
    return 'bg-sky-50 text-sky-700';
  }

  protected waitingCount(): number {
    return this.queue().filter((ticket) => ticket.status === 'WAITING').length;
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOnePatientName');
      void this.router.navigateByUrl('/login');
      return;
    }
    if (response.status === 403) {
      this.error.set('Bạn không có quyền điều phối hàng đợi.');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }

  private toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
