import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, BusinessLogResponse, apiErrorMessage } from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-business-audit',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './business-audit.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BusinessAudit implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly entityType = signal('APPOINTMENT');
  protected readonly entityId = signal('');
  protected readonly items = signal<BusinessLogResponse[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(false);
  protected readonly searched = signal(false);
  protected readonly error = signal('');

  ngOnInit(): void {}

  protected search(reset = true): void {
    const id = this.entityId().trim();
    if (!id) {
      this.error.set('Nhập mã đối tượng cần tra cứu.');
      this.searched.set(false);
      return;
    }
    if (reset) this.page.set(0);
    this.loading.set(true);
    this.error.set('');
    this.authApi.getBusinessLogPage(this.entityType(), id, this.page(), 50).subscribe({
      next: (result) => {
        this.items.set(result.items);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
        this.searched.set(true);
      },
      error: (response) => {
        this.loading.set(false);
        this.searched.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected previous(): void { if (this.page() > 0) { this.page.update((value) => value - 1); this.search(false); } }
  protected next(): void { if (this.page() + 1 < this.totalPages()) { this.page.update((value) => value + 1); this.search(false); } }
  protected formatDate(value: string): string { return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)); }
}
