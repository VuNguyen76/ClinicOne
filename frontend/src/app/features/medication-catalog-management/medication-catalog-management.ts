import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import {
  ApiErrorResponse,
  AuthApiService,
  MedicationDosageOption,
  MedicationSuggestionResponse,
  MedicationUnitOption,
  SpecialtyOption,
  apiErrorMessage,
} from '../../core/auth/auth-api.service';
import { hasStaffRole } from '../../core/auth/auth.guard';

const VI_CURRENCY_FORMATTER = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 });

@Component({
  selector: 'app-medication-catalog-management',
  standalone: true,
  imports: [FormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './medication-catalog-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MedicationCatalogManagement implements OnInit {
  private readonly authApi = inject(AuthApiService);

  protected canManage(): boolean {
    return hasStaffRole('COORDINATOR');
  }

  protected canToggleActive(): boolean {
    return hasStaffRole('COORDINATOR');
  }

  protected isDoctorRole(): boolean {
    return hasStaffRole('DOCTOR') && !hasStaffRole('COORDINATOR') && !hasStaffRole('ADMIN');
  }

  // Active Tab: 'medications' | 'units' | 'dosages'
  protected readonly activeTab = signal<'medications' | 'units' | 'dosages'>('medications');

  protected readonly medications = signal<MedicationSuggestionResponse[]>([]);
  protected readonly specialtiesFromApi = signal<SpecialtyOption[]>([]);
  protected readonly medicationUnits = signal<MedicationUnitOption[]>([]);
  protected readonly medicationDosages = signal<MedicationDosageOption[]>([]);

  protected readonly query = signal('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly code = signal('');
  protected readonly name = signal('');
  protected readonly category = signal('');
  protected readonly specialties = signal('');
  protected readonly defaultDosage = signal('');
  protected readonly defaultInstructions = signal('');
  protected readonly unit = signal('');
  protected readonly unitPrice = signal(0);
  protected readonly metadataModal = signal<'unit' | 'dosage' | null>(null);
  protected readonly metadataEditingId = signal<string | null>(null);
  protected readonly metadataOriginalUnitName = signal('');
  protected readonly metadataCode = signal('');
  protected readonly metadataName = signal('');
  protected readonly metadataDescription = signal('');
  protected readonly metadataFormat = signal('');
  protected readonly metadataUnitName = signal('');
  protected readonly metadataSortOrder = signal(1);

  protected readonly availableSpecialties = computed<string[]>(() => {
    return this.specialtiesFromApi().map((s) => s.name);
  });

  protected readonly standardUnits = computed<string[]>(() => {
    return this.medicationUnits().map((u) => u.name);
  });

  protected isKnownSpecialty(name: string): boolean {
    if (!name) return true;
    return this.availableSpecialties().includes(name);
  }

  protected readonly filteredDosagesForUnit = computed<MedicationDosageOption[]>(() => {
    const rawUnit = this.unit().trim().toLowerCase();
    const all = this.medicationDosages();
    if (!rawUnit) return all;
    return all.filter((d) => d.unitName && d.unitName.trim().toLowerCase() === rawUnit);
  });

  protected readonly standardDosageFormats = computed<string[]>(() => {
    return this.filteredDosagesForUnit().map((d) => d.dosageFormat);
  });

  protected formatUnitPrice(amount: number | null | undefined): string {
    return VI_CURRENCY_FORMATTER.format(amount ?? 0);
  }

  protected selectUnit(u: string): void {
    this.unit.set(u);
  }

  protected selectCategory(cat: string): void {
    this.category.set(cat);
  }

  protected selectDosage(d: string): void {
    this.defaultDosage.set(d);
  }

  protected selectInstruction(ins: string): void {
    this.defaultInstructions.set(ins);
  }

  protected readonly filteredMedications = computed(() => {
    const query = this.query().trim().toLocaleLowerCase();
    if (!query) return this.medications();
    return this.medications().filter((item) =>
      `${item.code} ${item.name} ${item.category || ''} ${item.specialties || ''}`.toLocaleLowerCase().includes(query)
    );
  });

  protected readonly filteredUnits = computed(() => {
    const query = this.query().trim().toLocaleLowerCase();
    if (!query) return this.medicationUnits();
    return this.medicationUnits().filter((item) =>
      `${item.code} ${item.name} ${item.description || ''}`.toLocaleLowerCase().includes(query)
    );
  });

  protected readonly filteredDosages = computed(() => {
    const query = this.query().trim().toLocaleLowerCase();
    if (!query) return this.medicationDosages();
    return this.medicationDosages().filter((item) =>
      `${item.code} ${item.unitName} ${item.dosageFormat} ${item.description || ''}`.toLocaleLowerCase().includes(query)
    );
  });

  protected readonly activeCount = computed(() => this.medications().filter((item) => item.active).length);
  protected readonly inactiveCount = computed(() => this.medications().filter((item) => !item.active).length);

  ngOnInit(): void {
    this.load();
    this.loadMetadata();
  }

  protected setTab(tab: 'medications' | 'units' | 'dosages'): void {
    this.activeTab.set(tab);
    this.loadMetadata();
  }

  private metadataLoaded = false;

  private loadMetadata(): void {
    if (this.metadataLoaded) return;
    this.metadataLoaded = true;
    if (this.authApi.getSpecialties) {
      this.authApi.getSpecialties().subscribe({
        next: (items) => this.specialtiesFromApi.set(items || []),
        error: () => {},
      });
    }
    if (this.authApi.getMedicationUnits) {
      this.authApi.getMedicationUnits(!this.canManage()).subscribe({
        next: (items) => this.medicationUnits.set(items || []),
        error: (response: ApiErrorResponse) => this.error.set(apiErrorMessage(response)),
      });
    }
    if (this.authApi.getMedicationDosages) {
      this.authApi.getMedicationDosages(undefined, !this.canManage()).subscribe({
        next: (items) => this.medicationDosages.set(items || []),
        error: (response: ApiErrorResponse) => this.error.set(apiErrorMessage(response)),
      });
    }
  }

  protected openCreate(): void {
    this.loadMetadata();
    this.editingId.set(null);
    this.code.set('');
    this.name.set('');
    this.category.set('');
    this.specialties.set('');
    this.defaultDosage.set('');
    this.defaultInstructions.set('');
    this.unit.set('');
    this.unitPrice.set(0);
    this.error.set('');
    this.modalOpen.set(true);
  }

  protected openEdit(item: MedicationSuggestionResponse): void {
    this.loadMetadata();
    this.editingId.set(item.id);
    this.code.set(item.code);
    this.name.set(item.name);
    this.category.set(item.category || '');
    this.specialties.set(item.specialties || '');
    this.defaultDosage.set(item.defaultDosage || '');
    this.defaultInstructions.set(item.defaultInstructions || '');
    this.unit.set(item.unit || '');
    this.unitPrice.set(item.unitPrice ?? 0);
    this.error.set('');
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    if (!this.saving()) this.modalOpen.set(false);
  }

  protected openMetadataCreate(type: 'unit' | 'dosage'): void {
    if (!this.canManage()) return;
    this.loadMetadata();
    this.metadataModal.set(type);
    this.metadataEditingId.set(null);
    this.metadataOriginalUnitName.set('');
    this.metadataCode.set('');
    this.metadataName.set('');
    this.metadataDescription.set('');
    this.metadataFormat.set('');
    this.metadataUnitName.set(this.medicationUnits().find((item) => item.active !== false)?.name ?? '');
    this.metadataSortOrder.set(type === 'unit'
      ? Math.max(0, ...this.medicationUnits().map((item) => item.sortOrder ?? 0)) + 1
      : Math.max(0, ...this.medicationDosages().map((item) => item.sortOrder ?? 0)) + 1);
    this.error.set('');
  }

  protected openMetadataEdit(type: 'unit' | 'dosage', item: MedicationUnitOption | MedicationDosageOption): void {
    if (!this.canManage() || !item.id) return;
    this.metadataModal.set(type);
    this.metadataEditingId.set(item.id);
    this.metadataCode.set(item.code);
    this.metadataName.set(type === 'unit' ? (item as MedicationUnitOption).name : '');
    this.metadataDescription.set(item.description || '');
    this.metadataFormat.set(type === 'dosage' ? (item as MedicationDosageOption).dosageFormat : '');
    this.metadataUnitName.set(type === 'dosage' ? (item as MedicationDosageOption).unitName : '');
    this.metadataOriginalUnitName.set(type === 'unit' ? (item as MedicationUnitOption).name : '');
    this.metadataSortOrder.set(item.sortOrder ?? 0);
    this.error.set('');
  }

  protected closeMetadataModal(): void {
    if (!this.saving()) this.metadataModal.set(null);
  }

  protected saveMetadata(): void {
    const type = this.metadataModal();
    if (!type || !this.canManage()) return;
    const code = this.metadataCode().trim().toUpperCase();
    const nameOrFormat = type === 'unit' ? this.metadataName().trim() : this.metadataFormat().trim();
    const sortOrder = Number(this.metadataSortOrder());
    if (!/^[A-Z0-9_-]{2,50}$/.test(code) || !nameOrFormat || nameOrFormat.length > (type === 'unit' ? 100 : 200)) {
      this.error.set(type === 'unit'
        ? 'Nhập mã hợp lệ và tên đơn vị tính không quá 100 ký tự.'
        : 'Nhập mã hợp lệ và định dạng liều dùng không quá 200 ký tự.');
      return;
    }
    if (!Number.isSafeInteger(sortOrder) || sortOrder < 0) {
      this.error.set('Thứ tự hiển thị phải là số nguyên không âm.');
      return;
    }
    if (type === 'dosage' && !this.metadataUnitName().trim()) {
      this.error.set('Chọn đơn vị tính đang hoạt động trước khi thêm liều dùng.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const editingId = this.metadataEditingId();
    if (type === 'unit') {
      const request = {
        code, name: this.metadataName().trim(), description: this.metadataDescription().trim(), sortOrder,
      };
      const save = editingId
        ? this.authApi.updateMedicationUnit(editingId, request)
        : this.authApi.createMedicationUnit(request);
      save.subscribe({
        next: (saved) => this.finishMetadataSave('unit', saved),
        error: (response: ApiErrorResponse) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
      });
    } else {
      const request = {
        code, unitName: this.metadataUnitName().trim(), dosageFormat: this.metadataFormat().trim(),
        description: this.metadataDescription().trim(), sortOrder,
      };
      const save = editingId
        ? this.authApi.updateMedicationDosage(editingId, request)
        : this.authApi.createMedicationDosage(request);
      save.subscribe({
        next: (saved) => this.finishMetadataSave('dosage', saved),
        error: (response: ApiErrorResponse) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
      });
    }
  }

  private finishMetadataSave(type: 'unit' | 'dosage', saved: MedicationUnitOption | MedicationDosageOption): void {
    if (type === 'unit') {
      const editingId = this.metadataEditingId();
      this.medicationUnits.update((items) => (editingId
        ? items.map((item) => item.id === editingId ? saved as MedicationUnitOption : item)
        : [...items, saved as MedicationUnitOption])
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)));
      if (editingId) {
        this.medicationDosages.update((items) => items.map((item) => item.unitName === this.metadataOriginalUnitName() ? { ...item, unitName: (saved as MedicationUnitOption).name } : item));
      }
    } else {
      const editingId = this.metadataEditingId();
      this.medicationDosages.update((items) => (editingId
        ? items.map((item) => item.id === editingId ? saved as MedicationDosageOption : item)
        : [...items, saved as MedicationDosageOption])
        .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)));
    }
    this.saving.set(false);
    this.metadataModal.set(null);
    this.notice.set(type === 'unit'
      ? (this.metadataEditingId() ? 'Đã cập nhật đơn vị tính.' : 'Đã thêm đơn vị tính.')
      : (this.metadataEditingId() ? 'Đã cập nhật liều dùng chuẩn.' : 'Đã thêm liều dùng chuẩn.'));
    this.metadataEditingId.set(null);
    setTimeout(() => this.notice.set(''), 4000);
  }

  protected toggleMetadataActive(type: 'unit' | 'dosage', item: MedicationUnitOption | MedicationDosageOption): void {
    if (!this.canToggleActive() || !item.id) return;
    const onError = (response: ApiErrorResponse) => this.error.set(apiErrorMessage(response));
    if (type === 'unit') {
      this.authApi.setMedicationUnitActive(item.id, item.active === false).subscribe({
        next: (saved) => {
          this.medicationUnits.update((items) => items.map((current) => current.id === saved.id ? saved : current));
          if (saved.active === false) {
            this.medicationDosages.update((items) => items.map((current) => current.unitName === saved.name ? { ...current, active: false } : current));
          }
          this.setMetadataToggleNotice(type, saved.active !== false);
        },
        error: onError,
      });
    } else {
      this.authApi.setMedicationDosageActive(item.id, item.active === false).subscribe({
        next: (saved) => {
          this.medicationDosages.update((items) => items.map((current) => current.id === saved.id ? saved : current));
          this.setMetadataToggleNotice(type, saved.active !== false);
        },
        error: onError,
      });
    }
  }

  private setMetadataToggleNotice(type: 'unit' | 'dosage', active: boolean): void {
    this.notice.set(active
      ? `Đã kích hoạt lại ${type === 'unit' ? 'đơn vị tính' : 'liều dùng chuẩn'}.`
      : type === 'unit'
        ? 'Đã ngừng đơn vị tính và các liều dùng tương ứng; dữ liệu cũ vẫn được giữ.'
        : 'Đã ngừng liều dùng chuẩn; dữ liệu cũ vẫn được giữ.');
    setTimeout(() => this.notice.set(''), 4000);
  }

  protected save(): void {
    const code = this.code().trim().toUpperCase();
    const name = this.name().trim();
    const unitPrice = Number(this.unitPrice());
    if (!/^[A-Z0-9_-]{2,50}$/.test(code) || !name || name.length > 200) {
      this.error.set('Nhập mã thuốc hợp lệ và tên thuốc không quá 200 ký tự.');
      return;
    }
    if (!Number.isSafeInteger(unitPrice) || unitPrice < 0) {
      this.error.set('Giá bán phải là số nguyên không âm.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const editingId = this.editingId();
    const metadata = {
      category: this.category().trim() || undefined,
      specialties: this.specialties().trim() || undefined,
      defaultDosage: this.defaultDosage().trim() || undefined,
      defaultInstructions: this.defaultInstructions().trim() || undefined,
      unit: this.unit().trim() || undefined,
      unitPrice,
    };
    const request = editingId
      ? (metadata ? this.authApi.updateMedication(editingId, code, name, metadata) : this.authApi.updateMedication(editingId, code, name))
      : (metadata ? this.authApi.createMedication(code, name, metadata) : this.authApi.createMedication(code, name));
    request.subscribe({
      next: (saved) => {
        this.medications.update((items) => {
          const next = editingId ? items.map((item) => item.id === saved.id ? saved : item) : [...items, saved];
          return next.sort((a, b) => a.name.localeCompare(b.name, 'vi'));
        });
        this.saving.set(false);
        this.modalOpen.set(false);
        this.notice.set(editingId ? 'Đã cập nhật thuốc.' : 'Đã thêm thuốc.');
        setTimeout(() => this.notice.set(''), 4000);
      },
      error: (response: ApiErrorResponse) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected toggleActive(item: MedicationSuggestionResponse): void {
    this.toggle(item);
  }

  protected toggle(item: MedicationSuggestionResponse): void {
    this.error.set('');
    this.notice.set('');
    this.authApi.setMedicationActive(item.id, !item.active).subscribe({
      next: (saved) => {
        this.medications.update((items) => items.map((current) => current.id === saved.id ? saved : current));
        this.notice.set(saved.active ? 'Đã cho phép dùng lại thuốc.' : 'Đã tạm ngưng thuốc.');
        setTimeout(() => this.notice.set(''), 4000);
      },
      error: (response: ApiErrorResponse) => this.error.set(apiErrorMessage(response)),
    });
  }

  protected load(): void {
    this.authApi.getAdminMedications().subscribe({
      next: (items) => { this.medications.set(items); this.loading.set(false); },
      error: (response: ApiErrorResponse) => { this.error.set(apiErrorMessage(response)); this.loading.set(false); },
    });
  }

  protected refresh(): void {
    this.load();
    if (this.authApi.getMedicationUnits) {
      this.authApi.getMedicationUnits(!this.canManage()).subscribe({
        next: (items) => this.medicationUnits.set(items || []),
        error: (response: ApiErrorResponse) => this.error.set(apiErrorMessage(response)),
      });
    }
    if (this.authApi.getMedicationDosages) {
      this.authApi.getMedicationDosages(undefined, !this.canManage()).subscribe({
        next: (items) => this.medicationDosages.set(items || []),
        error: (response: ApiErrorResponse) => this.error.set(apiErrorMessage(response)),
      });
    }
  }
}
