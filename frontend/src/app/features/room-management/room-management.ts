import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AccountMenu } from '../../shared/account-menu/account-menu';
import { ApiErrorResponse, AuthApiService, ClinicRoomResponse, apiErrorMessage } from '../../core/auth/auth-api.service';
import * as QRCode from 'qrcode';

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
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly qrRoom = signal<ClinicRoomResponse | null>(null);
  protected readonly qrImage = signal('');
  protected readonly qrLoading = signal(false);
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
        this.closeForm();
        this.saving.set(false);
      },
      error: (response) => { this.saving.set(false); this.handleError(response); },
    });
  }

  protected edit(room: ClinicRoomResponse): void {
    this.editingId.set(room.id);
    this.form.setValue({ code: room.code, name: room.name, specialty: room.specialty });
    this.error.set('');
    this.formOpen.set(true);
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.form.reset({ code: '', name: '', specialty: '' });
    this.error.set('');
    this.formOpen.set(true);
  }

  protected closeForm(): void {
    this.editingId.set(null);
    this.form.reset({ code: '', name: '', specialty: '' });
    this.formOpen.set(false);
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

  protected openQr(room: ClinicRoomResponse): void {
    this.qrRoom.set(room);
    this.qrImage.set('');
    this.qrLoading.set(true);
    void QRCode.toDataURL(this.roomCheckInUrl(room), {
      width: 360,
      margin: 2,
      errorCorrectionLevel: 'M',
      color: { dark: '#082b35', light: '#ffffff' },
    }).then((image) => {
      if (this.qrRoom()?.id === room.id) this.qrImage.set(image);
    }).catch(() => {
      this.error.set('Không thể tạo mã QR. Vui lòng thử lại.');
      this.closeQr();
    }).finally(() => this.qrLoading.set(false));
  }

  protected closeQr(): void {
    this.qrRoom.set(null);
    this.qrImage.set('');
  }

  protected roomCheckInUrl(room: ClinicRoomResponse): string {
    return `${window.location.origin}/queue/check-in/${encodeURIComponent(room.qrToken)}`;
  }

  protected downloadQr(): void {
    const room = this.qrRoom();
    const image = this.qrImage();
    if (!room || !image) return;
    const link = document.createElement('a');
    link.href = image;
    link.download = `clinicone-${room.code.toLowerCase()}-qr.png`;
    link.click();
  }

  protected printQr(): void {
    const room = this.qrRoom();
    const image = this.qrImage();
    if (!room || !image) return;
    const printWindow = window.open('', '_blank', 'noopener,noreferrer,width=640,height=760');
    if (!printWindow) return;
    const title = printWindow.document.createElement('h1');
    title.textContent = `Phòng ${room.name}`;
    const subtitle = printWindow.document.createElement('p');
    subtitle.textContent = `Quét mã để nhận số thứ tự · ${room.specialty}`;
    const qr = printWindow.document.createElement('img');
    qr.src = image;
    qr.alt = `Mã QR check-in phòng ${room.name}`;
    qr.width = 420;
    const style = printWindow.document.createElement('style');
    style.textContent = 'body{font-family:Arial,sans-serif;text-align:center;padding:32px;color:#082b35}h1{font-size:28px;margin:0 0 12px}p{font-size:16px;color:#536b73;margin:0 0 24px}img{max-width:100%;height:auto}';
    printWindow.document.head.appendChild(style);
    printWindow.document.body.append(title, subtitle, qr);
    printWindow.focus();
    printWindow.print();
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
      sessionStorage.removeItem('clinicOneStaffRole');
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
