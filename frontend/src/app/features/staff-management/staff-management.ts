import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import {
  ApiErrorResponse,
  AuthApiService,
  StaffAccountResponse,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-staff-management',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
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
  protected readonly createOpen = signal(false);
  protected readonly createdCredentials = signal<{ username: string; password: string } | null>(null);
  protected readonly newFullName = signal('');
  protected readonly newEmployeeCode = signal('');
  protected readonly newUnitName = signal('');
  protected readonly newDepartmentName = signal('');
  protected readonly newRoles = signal<string[]>([]);
  protected readonly roleOptions = ['DOCTOR', 'RECEPTIONIST', 'COORDINATOR'];
  protected readonly searchTerm = signal('');
  protected readonly editingAccount = signal<StaffAccountResponse | null>(null);
  protected readonly editingRoles = signal<string[]>([]);

  protected filteredAccounts(): StaffAccountResponse[] {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.accounts();
    return this.accounts().filter((acc) =>
      acc.fullName.toLowerCase().includes(q) ||
      acc.username.toLowerCase().includes(q) ||
      (acc.employeeCode && acc.employeeCode.toLowerCase().includes(q)) ||
      (acc.departmentName && acc.departmentName.toLowerCase().includes(q)) ||
      (acc.roles || [acc.role]).some((r) => r.toLowerCase().includes(q))
    );
  }

  protected totalAccountsCount(): number {
    return this.accounts().length;
  }

  protected activeAccountsCount(): number {
    return this.accounts().filter((acc) => acc.status === 'ACTIVE').length;
  }

  protected lockedAccountsCount(): number {
    return this.accounts().filter((acc) => acc.status === 'LOCKED').length;
  }

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

  protected openCreate(): void {
    this.createdCredentials.set(null);
    this.newFullName.set('');
    this.newEmployeeCode.set('');
    this.newUnitName.set('');
    this.newDepartmentName.set('');
    this.newRoles.set([]);
    this.error.set('');
    this.createOpen.set(true);
  }

  protected closeCreate(): void {
    if (!this.updatingId()) this.createOpen.set(false);
  }

  protected openRoleEditor(account: StaffAccountResponse): void {
    this.error.set('');
    this.editingAccount.set(account);
    this.editingRoles.set([...(account.roles?.length ? account.roles : [account.role])]);
  }

  protected closeRoleEditor(): void {
    if (!this.updatingId()) this.editingAccount.set(null);
  }

  protected toggleEditingRole(role: string): void {
    this.editingRoles.update((roles) => roles.includes(role)
      ? roles.filter((item) => item !== role)
      : [...roles, role]);
  }

  protected hasEditingRole(role: string): boolean {
    return this.editingRoles().includes(role);
  }

  protected saveRoles(): void {
    const account = this.editingAccount();
    const roles = this.editingRoles();
    if (!account || roles.length === 0 || roles.length > 3) {
      this.error.set('Chọn từ 1 đến 3 vai trò.');
      return;
    }
    this.error.set('');
    this.updatingId.set(`roles:${account.staffId}`);
    this.authApi.updateStaffRoles(account.staffId, roles).subscribe({
      next: (updated) => {
        this.accounts.update((items) => items.map((item) => item.staffId === updated.staffId ? updated : item));
        this.updatingId.set(null);
        this.editingAccount.set(null);
        this.notice.set(`Đã cập nhật vai trò cho ${updated.fullName}.`);
      },
      error: (response: ApiErrorResponse) => { this.updatingId.set(null); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected toggleRole(role: string): void {
    this.newRoles.update((roles) => roles.includes(role) ? roles.filter((item) => item !== role) : [...roles, role]);
  }

  protected hasRole(role: string): boolean {
    return this.newRoles().includes(role);
  }

  protected createAccount(): void {
    const request = {
      fullName: this.newFullName().trim(), employeeCode: this.newEmployeeCode().trim(),
      unitName: this.newUnitName().trim(), departmentName: this.newDepartmentName().trim(), roles: this.newRoles(),
    };
    if (!request.fullName || !request.employeeCode || !request.unitName || !request.departmentName || request.roles.length === 0 || request.roles.length > 3) {
      this.error.set('Nhập đủ thông tin và chọn từ 1 đến 3 vai trò.');
      return;
    }
    this.error.set('');
    this.notice.set('');
    this.updatingId.set('creating');
    this.authApi.createStaffAccount(request).subscribe({
      next: (created) => {
        this.accounts.update((items) => [...items, created.account].sort((a, b) => a.fullName.localeCompare(b.fullName)));
        this.createdCredentials.set({ username: created.account.username, password: created.initialPassword });
        this.notice.set('Đã tạo tài khoản. Hãy bàn giao mật khẩu khởi tạo một lần cho nhân viên.');
        this.updatingId.set(null);
      },
      error: (response: ApiErrorResponse) => { this.error.set(apiErrorMessage(response)); this.updatingId.set(null); },
    });
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

  protected roleLabels(account: StaffAccountResponse): string {
    return (account.roles?.length ? account.roles : [account.role]).map((role) => this.roleLabel(role)).join(' · ');
  }

  protected statusLabel(status: string): string {
    return status === 'LOCKED' ? 'Đang khóa' : 'Đang hoạt động';
  }
}
