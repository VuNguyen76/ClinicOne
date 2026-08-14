import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, SpecialtyOption, apiErrorMessage } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-specialty-catalog-management',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
  templateUrl: './specialty-catalog-management.html',
})
export class SpecialtyCatalogManagement implements OnInit {
  private readonly api = inject(AuthApiService);
  protected readonly items = signal<SpecialtyOption[]>([]);
  protected readonly code = signal('');
  protected readonly name = signal('');
  protected readonly description = signal('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  ngOnInit(): void { this.load(); }

  protected load(): void {
    this.loading.set(true);
    this.api.getSpecialties().subscribe({
      next: (items) => { this.items.set(items); this.loading.set(false); },
      error: (response) => { this.error.set(apiErrorMessage(response)); this.loading.set(false); },
    });
  }

  protected create(): void {
    if (!this.code().trim() || !this.name().trim()) {
      this.error.set('Nhập mã và tên chuyên khoa.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.api.createSpecialty({ code: this.code(), name: this.name(), description: this.description() }).subscribe({
      next: () => { this.code.set(''); this.name.set(''); this.description.set(''); this.notice.set('Đã thêm chuyên khoa.'); this.saving.set(false); this.load(); },
      error: (response) => { this.error.set(apiErrorMessage(response)); this.saving.set(false); },
    });
  }

  protected deactivate(item: SpecialtyOption): void {
    if (!confirm(`Tạm dừng chuyên khoa ${item.name}?`)) return;
    this.api.deactivateSpecialty(item.code).subscribe({
      next: () => { this.notice.set('Đã tạm dừng chuyên khoa.'); this.load(); },
      error: (response) => this.error.set(apiErrorMessage(response)),
    });
  }
}
