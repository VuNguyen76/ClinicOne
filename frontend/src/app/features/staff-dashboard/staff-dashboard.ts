import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
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
  imports: [MatIconModule, StaffWorkspaceShell],
  templateUrl: './staff-dashboard.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StaffDashboard implements OnInit, OnDestroy {
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);
  private doctorQueueRefreshTimer: ReturnType<typeof setInterval> | null = null;
  private doctorQueueHealthTimer: ReturnType<typeof setInterval> | null = null;
  private lastSuccessfulDoctorQueueAt: number | null = null;
  private doctorQueueRequestInFlight = false;
  private readonly startRequestKeys = new Map<string, string>();

  protected readonly rooms = signal<ClinicRoomResponse[]>([]);
  protected readonly selectedRoomCode = signal('');
  protected readonly selectedDate = signal(clinicTodayIso());
  protected readonly queue = signal<QueueTicketResponse[]>([]);
  protected readonly loadingRooms = signal(true);
  protected readonly loadingQueue = signal(false);
  protected readonly doctorRoomName = signal('');
  protected readonly doctorShiftStatus = signal<'ACTIVE' | 'NONE' | 'CONFLICT'>('ACTIVE');
  protected readonly busyTicketId = signal('');
  protected readonly error = signal('');
  protected readonly queueSyncWarning = signal(false);
  protected readonly role = signal(sessionStorage.getItem('clinicOneStaffRole') ?? '');
  protected readonly activeRooms = computed(() => this.rooms().filter((room) => room.active));
  protected readonly waitingCount = computed(() => this.queue().filter((ticket) => ticket.status === 'WAITING').length);
  protected readonly calledCount = computed(() => this.queue().filter((ticket) => ticket.status === 'CALLED').length);
  protected readonly inServiceCount = computed(() => this.queue().filter((ticket) => ticket.status === 'IN_SERVICE').length);
  protected readonly completedCount = computed(() => this.queue().filter((ticket) => ticket.status === 'COMPLETED').length);
  protected readonly currentDoctorTicket = computed(() =>
    this.queue().find((ticket) => ticket.status === 'IN_SERVICE')
      ?? this.queue().find((ticket) => ticket.status === 'CALLED'));
  protected readonly nextWaitingTicket = computed(() => this.doctorShiftStatus() === 'ACTIVE'
    ? this.queue().find((ticket) =>
        (ticket.status === 'WAITING' && ticket.presenceStatus !== 'RETURN_REQUIRED') || ticket.status === 'SKIPPED')
    : undefined);

  protected readonly isOwnDoctor = computed(() => this.role() === 'DOCTOR');
  protected readonly canManageRooms = computed(() => ['ADMIN', 'COORDINATOR'].includes(this.role()));

  ngOnInit(): void {
    if (this.isOwnDoctor()) {
      this.loadDoctorQueue();
      this.startDoctorQueueAutoRefresh();
    } else {
      this.loadRooms();
    }
  }

  ngOnDestroy(): void {
    this.stopDoctorQueueAutoRefresh();
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

  private loadDoctorQueue(showLoading = true): void {
    if (this.doctorQueueRequestInFlight) return;
    this.doctorQueueRequestInFlight = true;
    this.loadingRooms.set(false);
    if (showLoading) {
      this.loadingQueue.set(true);
      this.error.set('');
    }
    this.authApi.getDoctorQueue(this.selectedDate()).subscribe({
      next: (workspace) => {
        this.doctorQueueRequestInFlight = false;
        this.selectedRoomCode.set(workspace.roomCode);
        this.doctorRoomName.set(workspace.roomName);
        this.doctorShiftStatus.set(workspace.shiftStatus ?? 'ACTIVE');
        this.queue.set(workspace.tickets);
        this.lastSuccessfulDoctorQueueAt = Date.now();
        this.queueSyncWarning.set(false);
        if (showLoading) this.loadingQueue.set(false);
      },
      error: (response) => {
        this.doctorQueueRequestInFlight = false;
        if (showLoading) {
          this.loadingQueue.set(false);
          this.handleError(response);
        } else {
          this.refreshQueueSyncWarning();
        }
      },
    });
  }

  private startDoctorQueueAutoRefresh(): void {
    this.stopDoctorQueueAutoRefresh();
    this.doctorQueueRefreshTimer = setInterval(() => this.loadDoctorQueue(false), 3_000);
    this.doctorQueueHealthTimer = setInterval(() => this.refreshQueueSyncWarning(), 1_000);
  }

  private stopDoctorQueueAutoRefresh(): void {
    if (this.doctorQueueRefreshTimer !== null) clearInterval(this.doctorQueueRefreshTimer);
    if (this.doctorQueueHealthTimer !== null) clearInterval(this.doctorQueueHealthTimer);
    this.doctorQueueRefreshTimer = null;
    this.doctorQueueHealthTimer = null;
  }

  private refreshQueueSyncWarning(): void {
    if (this.lastSuccessfulDoctorQueueAt === null) return;
    this.queueSyncWarning.set(Date.now() - this.lastSuccessfulDoctorQueueAt >= 10_000);
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

  protected goToToday(): void {
    this.selectedDate.set(clinicTodayIso());
    this.loadQueue();
  }

  protected goToPreviousDay(): void {
    const current = new Date(this.selectedDate());
    current.setDate(current.getDate() - 1);
    this.selectedDate.set(this.toIsoDate(current));
    this.loadQueue();
  }

  protected goToNextDay(): void {
    const current = new Date(this.selectedDate());
    current.setDate(current.getDate() + 1);
    this.selectedDate.set(this.toIsoDate(current));
    this.loadQueue();
  }

  protected isToday(): boolean {
    return this.selectedDate() === clinicTodayIso();
  }

  protected act(ticket: QueueTicketResponse, action: QueueAction): void {
    this.busyTicketId.set(ticket.id);
    this.error.set('');
    if (action === 'start') {
      this.authApi.startExamination(ticket.id, this.startRequestKey(ticket.id)).subscribe({
        next: () => {
          this.busyTicketId.set('');
          void this.router.navigate(['/doctor/examinations', ticket.id]);
        },
        error: (response) => {
          this.busyTicketId.set('');
          this.handleError(response);
        },
      });
      return;
    }
    this.authApi.skipQueueTicket(ticket.id, 'Không có mặt khi được gọi').subscribe({
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

  private startRequestKey(ticketId: string): string {
    const existing = this.startRequestKeys.get(ticketId);
    if (existing) return existing;
    const generated = `start-${crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`}`;
    this.startRequestKeys.set(ticketId, generated);
    return generated;
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

  protected formatDate(value?: string | null): string {
    if (!value) return 'Chưa cập nhật';
    const [year, month, day] = value.split('-');
    return day && month && year ? `${day}/${month}/${year}` : value;
  }

  protected openExamination(ticket: QueueTicketResponse): void {
    void this.router.navigate(['/doctor/examinations', ticket.id]);
  }

  protected statusClass(status: string): string {
    if (status === 'CALLED') return 'erp-badge-warning';
    if (status === 'IN_SERVICE') return 'erp-badge-info';
    if (status === 'COMPLETED') return 'erp-badge-success';
    if (status === 'SKIPPED') return 'erp-badge-neutral';
    return 'erp-badge-info';
  }

  protected statusDotClass(status: string): string {
    if (status === 'CALLED') return 'erp-dot-warning';
    if (status === 'IN_SERVICE') return 'erp-dot-info';
    if (status === 'COMPLETED') return 'erp-dot-success';
    if (status === 'SKIPPED') return 'erp-dot-neutral';
    return 'erp-dot-info';
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOneSessionType');
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
