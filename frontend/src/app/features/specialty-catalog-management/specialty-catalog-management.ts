import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, SpecialtyOption, apiErrorMessage } from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';

@Component({
  selector: 'app-specialty-catalog-management',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './specialty-catalog-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpecialtyCatalogManagement implements OnInit {
  private readonly api = inject(AuthApiService);
  protected readonly items = signal<SpecialtyOption[]>([]);
  protected readonly editingCode = signal<string | null>(null);
  protected readonly code = signal('');
  protected readonly name = signal('');
  protected readonly description = signal('');
  protected readonly searchTerm = signal('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly modalOpen = signal(false);

  protected filteredItems(): SpecialtyOption[] {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.items();
    return this.items().filter((item) =>
      item.code.toLowerCase().includes(q) ||
      item.name.toLowerCase().includes(q) ||
      (item.description && item.description.toLowerCase().includes(q))
    );
  }

  protected totalCount(): number {
    return this.items().length;
  }

  protected withDescriptionCount(): number {
    return this.items().filter((item) => item.description && item.description.trim().length > 0).length;
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.getSpecialties().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: (response) => {
        this.error.set(apiErrorMessage(response));
        this.loading.set(false);
      },
    });
  }

  protected openCreate(): void {
    this.editingCode.set(null);
    this.code.set('');
    this.name.set('');
    this.description.set('');
    this.error.set('');
    this.notice.set('');
    this.modalOpen.set(true);
  }

  protected openEdit(item: SpecialtyOption): void {
    this.editingCode.set(item.code);
    this.code.set(item.code);
    this.name.set(item.name);
    this.description.set(item.description || '');
    this.error.set('');
    this.notice.set('');
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    if (this.saving()) return;
    this.modalOpen.set(false);
    this.editingCode.set(null);
  }

  protected save(): void {
    if (!this.code().trim() || !this.name().trim()) {
      this.error.set('Vui lòng nhập mã và tên chuyên khoa.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const request = {
      code: this.code().trim().toUpperCase(),
      name: this.name().trim(),
      description: this.description().trim(),
    };

    const operation = this.editingCode()
      ? this.api.updateSpecialty(this.editingCode()!, request)
      : this.api.createSpecialty(request);

    operation.subscribe({
      next: () => {
        this.notice.set(this.editingCode() ? 'Đã cập nhật chuyên khoa.' : 'Đã thêm chuyên khoa mới.');
        this.saving.set(false);
        this.modalOpen.set(false);
        this.editingCode.set(null);
        this.load();
      },
      error: (response) => {
        this.error.set(apiErrorMessage(response));
        this.saving.set(false);
      },
    });
  }

  protected deactivate(item: SpecialtyOption): void {
    if (!confirm(`Tạm dừng chuyên khoa ${item.name}?`)) return;
    this.api.deactivateSpecialty(item.code).subscribe({
      next: () => {
        this.notice.set(`Đã tạm dừng chuyên khoa ${item.name}.`);
        this.load();
      },
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }
}
