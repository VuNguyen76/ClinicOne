import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, ReconciliationResponse, apiErrorMessage } from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { hasStaffRole } from '../../core/auth/auth.guard';

@Component({
  selector: 'app-reconciliation-management',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './reconciliation.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReconciliationManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly incidents = signal<ReconciliationResponse[]>([]);
  protected readonly canClose = computed(() => hasStaffRole('COORDINATOR') || hasStaffRole('ADMIN'));
  protected readonly selected = signal<ReconciliationResponse | null>(null);
  protected readonly statusTab = signal<'OPEN' | 'CLOSED' | 'ALL'>('OPEN');

  // Resolution Form
  protected readonly action = signal('RETRY_BUSINESS_ACTION');
  protected readonly referenceType = signal('INCIDENT');
  protected readonly referenceValue = signal('');
  protected readonly resultNote = signal('');

  // State
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly scanning = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly searchTerm = signal('');

  // Manual incident modal
  protected readonly createModalOpen = signal(false);
  protected readonly newEntityType = signal('APPOINTMENT');
  protected readonly newReason = signal('');
  protected readonly newAssignee = signal('admin');

  protected formatEntityType(type: string): string {
    if (!type) return 'Nghiệp vụ';
    const map: Record<string, string> = {
      APPOINTMENT: 'Lịch hẹn',
      QUEUE_TICKET: 'Hàng đợi',
      EXAMINATION: 'Khám bệnh',
      BUSINESS_LOG: 'Nhật ký nghiệp vụ',
      INCIDENT: 'Sự cố đối soát',
    };
    return map[type] ?? type;
  }

  protected cleanReason(reason: string): string {
    if (!reason) return '—';
    if (reason.includes('Chuỗi hash')) {
      return 'Nhật ký hệ thống ghi nhận sai lệch thứ tự hoặc bị gián đoạn dữ liệu.';
    }
    return reason.replace(/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/g, 'tham chiếu hệ thống');
  }

  protected formatIncidentCode(code: string): string {
    if (!code) return 'SC-000';
    if (code.startsWith('INC-INTEGRITY-')) {
      const shortHex = code.replace('INC-INTEGRITY-', '').slice(0, 6).toUpperCase();
      return `SC-TOÀNVẸN-${shortHex}`;
    }
    return code.replace('INC-', 'SC-');
  }

  protected formatDate(val?: string | null): string {
    if (!val) return '—';
    return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(val));
  }

  protected readonly filteredIncidents = computed<ReconciliationResponse[]>(() => {
    const q = this.searchTerm().trim().toLowerCase();
    const tab = this.statusTab();
    return this.incidents().filter((i) => {
      if (tab === 'OPEN' && i.status !== 'OPEN' && !!i.status) return false;
      if (tab === 'CLOSED' && i.status !== 'CLOSED') return false;
      if (!q) return true;
      return (
        i.incidentCode.toLowerCase().includes(q) ||
        i.reason.toLowerCase().includes(q) ||
        i.entityType.toLowerCase().includes(q) ||
        this.formatEntityType(i.entityType).toLowerCase().includes(q) ||
        (i.assignee && i.assignee.toLowerCase().includes(q))
      );
    });
  });

  protected readonly totalIncidentsCount = computed(() => this.incidents().length);
  protected readonly openIncidentsCount = computed(() => this.incidents().filter((i) => i.status === 'OPEN' || !i.status).length);
  protected readonly closedIncidentsCount = computed(() => this.incidents().filter((i) => i.status === 'CLOSED').length);

  ngOnInit(): void { this.load(); }

  protected select(item: ReconciliationResponse): void {
    this.selected.set(item);
    this.referenceType.set(item.referenceType ?? 'INCIDENT');
    this.referenceValue.set(item.referenceValue ?? item.incidentCode);
    this.resultNote.set(item.resultNote ?? '');
    this.error.set('');
  }

  protected setPresetAction(type: 'NO_ACTION' | 'RETRY' | 'REPAIR'): void {
    const cur = this.selected();
    if (!cur) return;
    if (type === 'NO_ACTION') {
      this.action.set('NO_ACTION_REQUIRED');
      this.referenceType.set('INCIDENT');
      this.referenceValue.set(cur.incidentCode);
      this.resultNote.set('Đã kiểm tra xác nhận dữ liệu chính xác và đồng bộ, không cần thao tác thêm.');
    } else if (type === 'RETRY') {
      this.action.set('RETRY_BUSINESS_ACTION');
      this.referenceType.set('BUSINESS_LOG');
      this.referenceValue.set(cur.eventId || cur.incidentCode);
      this.resultNote.set('Đã thực hiện chạy lại tác vụ nghiệp vụ và đồng bộ trạng thái thành công.');
    } else if (type === 'REPAIR') {
      this.action.set('TECHNICAL_REPAIR');
      this.referenceType.set('BUSINESS_LOG');
      this.referenceValue.set(cur.eventId || cur.incidentCode);
      this.resultNote.set('Đã can thiệp kỹ thuật xử lý và xác nhận tính toàn vẹn hoàn tất.');
    }
  }

  protected updateAction(val: string): void {
    this.action.set(val);
    if (val === 'REPLAY_LOG' && (!this.referenceType() || this.referenceType() === 'INCIDENT')) {
      this.referenceType.set('BUSINESS_LOG');
    }
  }

  protected runIntegrityScan(): void {
    this.scanning.set(true);
    this.error.set('');
    this.notice.set('');
    this.authApi.triggerIntegrityCheck().subscribe({
      next: (res) => {
        this.scanning.set(false);
        if (res.incidentsOpened > 0) {
          this.notice.set(`Đã rà soát xong ${res.inspected} bản ghi. Phát hiện ${res.incidentsOpened} sự cố mới cần đối soát.`);
        } else {
          this.notice.set(`Đã rà soát xong ${res.inspected} bản ghi dữ liệu. Toàn bộ hệ thống đồng bộ hoàn hảo, không có sai lệch.`);
        }
        this.load();
      },
      error: (response) => {
        this.scanning.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected openCreateModal(): void {
    this.newEntityType.set('APPOINTMENT');
    this.newReason.set('');
    this.newAssignee.set('admin');
    this.createModalOpen.set(true);
    this.error.set('');
  }

  protected closeCreateModal(): void {
    this.createModalOpen.set(false);
  }

  protected submitCreateIncident(): void {
    if (!this.newReason().trim() || this.newReason().trim().length < 10) {
      this.error.set('Vui lòng nhập mô tả sự cố tối thiểu 10 ký tự.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.authApi.openReconciliation({
      entityType: this.newEntityType(),
      reason: this.newReason().trim(),
      assignee: this.newAssignee().trim() || 'admin',
    }).subscribe({
      next: (created) => {
        this.saving.set(false);
        this.createModalOpen.set(false);
        this.notice.set(`Đã ghi nhận sự cố đối soát ${this.formatIncidentCode(created.incidentCode)}.`);
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err));
      },
    });
  }

  protected close(): void {
    if (!this.canClose()) {
      this.error.set('Chỉ điều phối viên hoặc quản trị viên được đóng đối soát.');
      return;
    }
    const item = this.selected();
    if (!item || !this.referenceValue().trim() || this.resultNote().trim().length < 10) {
      this.error.set('Cần có mã tham chiếu và ghi chú kết quả từ 10 ký tự.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.authApi.closeReconciliation(item.id, {
      action: this.action(),
      referenceType: this.referenceType(),
      referenceValue: this.referenceValue().trim(),
      resultNote: this.resultNote().trim(),
    }).subscribe({
      next: (closed) => {
        this.saving.set(false);
        this.notice.set(`Đã đóng thành công sự cố đối soát ${this.formatIncidentCode(closed.incidentCode)}.`);
        this.load();
      },
      error: (response) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected label(value: string): string {
    return ({
      RETRY_BUSINESS_ACTION: 'Thực hiện lại công việc',
      REPLAY_LOG: 'Đối chiếu lại nhật ký',
      TECHNICAL_REPAIR: 'Sửa lỗi kỹ thuật',
      NO_ACTION_REQUIRED: 'Không cần xử lý thêm',
    } as Record<string, string>)[value] ?? value;
  }

  protected load(): void {
    this.loading.set(true);
    this.authApi.getReconciliations('ALL').subscribe({
      next: (items) => {
        this.incidents.set(items);
        this.loading.set(false);
        const openItem = items.find((i) => i.status === 'OPEN') || items[0];
        if (openItem) this.select(openItem);
      },
      error: (response) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }
}
