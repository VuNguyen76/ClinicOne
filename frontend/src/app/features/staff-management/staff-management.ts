import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import {
  ApiErrorResponse,
  AuthApiService,
  StaffAccountResponse,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';

@Component({
  selector: 'app-staff-management',
  standalone: true,
  imports: [RouterLink, MatIconModule, AccountMenu],
  templateUrl: './staff-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StaffManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);

  protected readonly accounts = signal<StaffAccountResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly updatingId = signal<string | null>(null);
  protected readonly confirming = signal<StaffAccountResponse | null>(null);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    this.authApi.getStaffAccounts().subscribe({
      next: (items) => { this.accounts.set(items); this.loading.set(false); },
      error: (response: ApiErrorResponse) => { this.loading.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected askToLock(account: StaffAccountResponse): void {
    this.confirming.set(account);
    this.error.set('');
  }

  protected closeConfirm(): void {
    if (!this.updatingId()) this.confirming.set(null);
  }

  protected updateStatus(account: StaffAccountResponse): void {
    const action = account.status === 'LOCKED'
      ? this.authApi.unlockStaffAccount(account.staffId)
      : this.authApi.lockStaffAccount(account.staffId);
    this.updatingId.set(account.staffId);
    this.error.set('');
    action.subscribe({
      next: (updated) => {
        this.accounts.update((items) => items.map((item) => item.staffId === updated.staffId ? updated : item));
        this.updatingId.set(null);
        this.confirming.set(null);
        this.notice.set(updated.status === 'LOCKED'
          ? `Đã khóa tài khoản ${updated.fullName} và kết thúc các phiên đang mở.`
          : `Đã mở khóa tài khoản ${updated.fullName}.`);
      },
      error: (response) => { this.updatingId.set(null); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected roleLabel(role: string): string {
    return ({ ADMIN: 'Quản trị viên', COORDINATOR: 'Điều phối viên', RECEPTIONIST: 'Tiếp nhận', DOCTOR: 'Bác sĩ' } as Record<string, string>)[role] ?? role;
  }

  protected statusLabel(status: string): string {
    return status === 'LOCKED' ? 'Đang khóa' : 'Đang hoạt động';
  }
}
