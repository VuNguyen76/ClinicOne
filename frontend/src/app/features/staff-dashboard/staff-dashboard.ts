import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AccountMenu } from '../../shared/account-menu/account-menu';
import { clinicTodayIso } from '../../core/time/clinic-time';
import {
  ApiErrorResponse,
  AuthApiService,
  ClinicRoomResponse,
  QueueTicketResponse,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';

type QueueAction = 'skip' | 'start';

@Component({
  selector: 'app-staff-dashboard',
  standalone: true,
  imports: [RouterLink, MatIconModule, AccountMenu],
  templateUrl: './staff-dashboard.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StaffDashboard implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly rooms = signal<ClinicRoomResponse[]>([]);
  protected readonly selectedRoomCode = signal('');
  protected readonly selectedDate = signal(clinicTodayIso());
  protected readonly queue = signal<QueueTicketResponse[]>([]);
  protected readonly loadingRooms = signal(true);
  protected readonly loadingQueue = signal(false);
  protected readonly doctorRoomName = signal('');
  protected readonly busyTicketId = signal('');
  protected readonly error = signal('');
  protected readonly role = signal(sessionStorage.getItem('clinicOneStaffRole') ?? '');
  protected readonly activeRooms = computed(() => this.rooms().filter((room) => room.active));
  protected readonly waitingCount = computed(() => this.queue().filter((ticket) => ticket.status === 'WAITING').length);
  protected readonly calledCount = computed(() => this.queue().filter((ticket) => ticket.status === 'CALLED').length);
  protected readonly inServiceCount = computed(() => this.queue().filter((ticket) => ticket.status === 'IN_SERVICE').length);
  protected readonly completedCount = computed(() => this.queue().filter((ticket) => ticket.status === 'COMPLETED').length);
  protected readonly nextWaitingTicket = computed(() => this.queue().find((ticket) =>
    (ticket.status === 'WAITING' && ticket.presenceStatus !== 'RETURN_REQUIRED') || ticket.status === 'SKIPPED'));

  protected readonly isOwnDoctor = computed(() => this.role() === 'DOCTOR');
  protected readonly canManageRooms = computed(() => ['ADMIN', 'COORDINATOR'].includes(this.role()));

  ngOnInit(): void {
    if (this.isOwnDoctor()) this.loadDoctorQueue();
    else this.loadRooms();
  }

  protected loadRooms(): void {
    this.loadingRooms.set(true);
    this.error.set('');
    this.authApi.getRooms().subscribe({
      next: (rooms) => {
        this.rooms.set(rooms);
        const firstRoom = this.activeRooms()[0];
        if (!this.activeRooms().some((room) => room.code === this.selectedRoomCode())) {
          this.selectedRoomCode.set(firstRoom?.code ?? '');
        }
        this.loadingRooms.set(false);
        if (this.selectedRoomCode()) this.loadQueue();
      },
      error: (response) => {
        this.loadingRooms.set(false);
        this.handleError(response);
      },
    });
  }

  protected loadQueue(): void {
    if (this.isOwnDoctor()) {
      this.loadDoctorQueue();
      return;
    }
    const roomCode = this.selectedRoomCode();
    if (!roomCode) {
      this.queue.set([]);
      return;
    }
    this.loadingQueue.set(true);
    this.error.set('');
    this.authApi.getRoomQueue(roomCode, this.selectedDate()).subscribe({
      next: (tickets) => {
        this.queue.set(tickets);
        this.loadingQueue.set(false);
      },
      error: (response) => {
        this.loadingQueue.set(false);
        this.handleError(response);
      },
    });
  }

  private loadDoctorQueue(): void {
    this.loadingRooms.set(false);
    this.loadingQueue.set(true);
    this.error.set('');
    this.authApi.getDoctorQueue(this.selectedDate()).subscribe({
      next: (workspace) => {
        this.selectedRoomCode.set(workspace.roomCode);
        this.doctorRoomName.set(workspace.roomName);
        this.queue.set(workspace.tickets);
        this.loadingQueue.set(false);
      },
      error: (response) => {
        this.loadingQueue.set(false);
        this.handleError(response);
      },
    });
  }

  protected selectRoom(event: Event): void {
    if (this.isOwnDoctor()) return;
    this.selectedRoomCode.set((event.target as HTMLSelectElement).value);
    this.loadQueue();
  }

  protected selectDate(event: Event): void {
    this.selectedDate.set((event.target as HTMLInputElement).value);
    this.loadQueue();
  }

  protected act(ticket: QueueTicketResponse, action: QueueAction): void {
    this.busyTicketId.set(ticket.id);
    this.error.set('');
    const request = action === 'skip'
      ? this.authApi.skipQueueTicket(ticket.id, 'Không có mặt khi được gọi')
      : this.authApi.startQueueTicket(ticket.id);
    request.subscribe({
      next: (updated) => {
        this.queue.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.busyTicketId.set('');
        if (action === 'start') {
          void this.router.navigate(['/doctor/examinations', ticket.id]);
        }
      },
      error: (response) => {
        this.busyTicketId.set('');
        this.handleError(response);
      },
    });
  }

  protected callNextDoctor(): void {
    if (!this.isOwnDoctor() || !this.nextWaitingTicket()) return;
    this.busyTicketId.set('next');
    this.error.set('');
    this.authApi.callNextDoctor(this.selectedDate()).subscribe({
      next: (updated) => {
        this.queue.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.busyTicketId.set('');
      },
      error: (response) => {
        this.busyTicketId.set('');
        this.handleError(response);
      },
    });
  }

  protected roomName(): string {
    if (this.isOwnDoctor()) return this.doctorRoomName() || 'Chưa được phân công phòng';
    return this.rooms().find((room) => room.code === this.selectedRoomCode())?.name ?? 'Chưa chọn phòng';
  }

  protected roleLabel(): string {
    switch (this.role()) {
      case 'ADMIN': return 'Quản trị viên';
      case 'COORDINATOR': return 'Điều phối viên';
      case 'RECEPTIONIST': return 'Tiếp nhận';
      case 'DOCTOR': return 'Bác sĩ';
      default: return 'Nhân viên';
    }
  }

  protected formatTime(value: string): string {
    return value?.slice(0, 5) ?? '';
  }

  protected statusClass(status: string): string {
    if (status === 'CALLED') return 'bg-amber-50 text-amber-700';
    if (status === 'IN_SERVICE') return 'bg-violet-50 text-violet-700';
    if (status === 'COMPLETED') return 'bg-emerald-50 text-emerald-700';
    if (status === 'SKIPPED') return 'bg-slate-100 text-slate-600';
    return 'bg-sky-50 text-sky-700';
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOneStaffRole');
      sessionStorage.removeItem('clinicOneStaffRoles');
      void this.router.navigateByUrl('/staff/login');
      return;
    }
    if (response.status === 403) {
      this.error.set('Tài khoản không có quyền xem hoặc điều phối phòng này.');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }

  private toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
