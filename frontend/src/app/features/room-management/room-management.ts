import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AccountMenu } from '../../shared/account-menu/account-menu';
import { ApiErrorResponse, AuthApiService, ClinicRoomResponse, apiErrorMessage } from '../../core/auth/auth-api.service';

type RoomStatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

@Component({
  selector: 'app-room-management',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './room-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoomManagement implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly rooms = signal<ClinicRoomResponse[]>([]);
  protected readonly searchTerm = signal('');
  protected readonly statusFilter = signal<RoomStatusFilter>('ALL');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly activeCount = computed(() => this.rooms().filter((room) => room.active).length);
  protected readonly inactiveCount = computed(() => this.rooms().filter((room) => !room.active).length);
  protected readonly visibleRooms = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const status = this.statusFilter();
    return this.rooms().filter((room) => {
      const matchesSearch = !term || [room.code, room.name, room.specialty].some((value) => value.toLowerCase().includes(term));
      const matchesStatus = status === 'ALL' || (status === 'ACTIVE' ? room.active : !room.active);
      return matchesSearch && matchesStatus;
    });
  });
  protected readonly form = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(32), Validators.pattern(/^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$/)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    specialty: ['', [Validators.required, Validators.maxLength(120)]],
  });

  ngOnInit(): void {
    this.loadRooms();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const request = this.form.getRawValue();
    const operation = this.editingId() ? this.authApi.updateRoom(this.editingId()!, request) : this.authApi.createRoom(request);
    operation.subscribe({
      next: (room) => {
        this.rooms.update((items) => this.editingId() ? items.map((item) => item.id === room.id ? room : item) : [...items, room].sort((a, b) => a.code.localeCompare(b.code)));
        this.resetForm();
        this.saving.set(false);
      },
      error: (response) => { this.saving.set(false); this.handleError(response); },
    });
  }

  protected edit(room: ClinicRoomResponse): void {
    this.editingId.set(room.id);
    this.form.setValue({ code: room.code, name: room.name, specialty: room.specialty });
    this.error.set('');
  }

  protected resetForm(): void {
    this.editingId.set(null);
    this.form.reset({ code: '', name: '', specialty: '' });
  }

  protected updateSearch(event: Event): void {
    this.searchTerm.set((event.target as HTMLInputElement).value);
  }

  protected updateStatus(event: Event): void {
    this.statusFilter.set((event.target as HTMLSelectElement).value as RoomStatusFilter);
  }

  protected toggleActive(room: ClinicRoomResponse): void {
    this.authApi.setRoomActive(room.id, !room.active).subscribe({
      next: (updated) => this.rooms.update((items) => items.map((item) => item.id === updated.id ? updated : item)),
      error: (response) => this.handleError(response),
    });
  }

  private loadRooms(): void {
    this.authApi.getRooms().subscribe({
      next: (rooms) => { this.rooms.set(rooms); this.loading.set(false); },
      error: (response) => { this.loading.set(false); this.handleError(response); },
    });
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    if (response.status === 401) {
      sessionStorage.removeItem('clinicOneAccessToken');
      sessionStorage.removeItem('clinicOnePatientName');
      void this.router.navigateByUrl('/login');
      return;
    }
    if (response.status === 403) {
      this.error.set('Bạn không có quyền quản lý phòng.');
      return;
    }
    this.error.set(apiErrorMessage(response));
  }
}
