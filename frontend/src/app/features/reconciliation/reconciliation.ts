import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, ReconciliationResponse, apiErrorMessage } from '../../core/auth/auth-api.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';

@Component({
  selector: 'app-reconciliation-management',
  standalone: true,
  imports: [FormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './reconciliation.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReconciliationManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly incidents = signal<ReconciliationResponse[]>([]);
  protected canClose = sessionStorage.getItem('clinicOneStaffRole') === 'COORDINATOR';
  protected readonly selected = signal<ReconciliationResponse | null>(null);
  protected readonly action = signal('RETRY_BUSINESS_ACTION');
  protected readonly referenceType = signal('INCIDENT');
  protected readonly referenceValue = signal('');
  protected readonly resultNote = signal('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  ngOnInit(): void { this.load(); }

  protected select(item: ReconciliationResponse): void {
    this.selected.set(item);
    this.referenceType.set(item.referenceType ?? 'INCIDENT');
    this.referenceValue.set(item.referenceValue ?? item.incidentCode);
    this.resultNote.set('');
    this.error.set('');
  }

  protected close(): void {
    const item = this.selected();
    if (!item || !this.referenceValue().trim() || this.resultNote().trim().length < 10) {
      this.error.set('Cần có mã tham chiếu và ghi chú kết quả từ 10 ký tự.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.authApi.closeReconciliation(item.id, {
      action: this.action(), referenceType: this.referenceType(), referenceValue: this.referenceValue().trim(), resultNote: this.resultNote().trim(),
    }).subscribe({
      next: (closed) => { this.incidents.update((items) => items.filter((entry) => entry.id !== closed.id)); this.selected.set(null); this.saving.set(false); this.notice.set(`Đã đóng đối soát ${closed.incidentCode}.`); },
      error: (response) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected label(value: string): string {
    return ({ RETRY_BUSINESS_ACTION: 'Thực hiện lại công việc', REPLAY_LOG: 'Đối chiếu lại nhật ký', TECHNICAL_REPAIR: 'Sửa lỗi kỹ thuật', NO_ACTION_REQUIRED: 'Không cần xử lý thêm' } as Record<string, string>)[value] ?? value;
  }

  private load(): void {
    this.authApi.getReconciliations().subscribe({
      next: (items) => { this.incidents.set(items); this.loading.set(false); if (items[0]) this.select(items[0]); },
      error: (response) => { this.loading.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }
}
