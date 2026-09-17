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
    return hasStaffRole('ADMIN') || hasStaffRole('COORDINATOR') || hasStaffRole('DOCTOR');
  }

  protected canToggleActive(): boolean {
    return hasStaffRole('ADMIN') || hasStaffRole('COORDINATOR');
  }

  protected isDoctorRole(): boolean {
    return hasStaffRole('DOCTOR') && !hasStaffRole('COORDINATOR') && !hasStaffRole('ADMIN');
  }

  // Active Tab: 'medications' | 'units' | 'dosages'
  protected readonly activeTab = signal<'medications' | 'units' | 'dosages'>('medications');

  protected readonly medications = signal<MedicationSuggestionResponse[]>([]);
  protected readonly specialtiesFromApi = signal<SpecialtyOption[]>([]);
  protected readonly medicationUnits = signal<MedicationUnitOption[]>([
    { code: 'UNIT-VIEN', name: 'Viên', description: 'Dạng rắn: viên nén, viên bao phim, viên nang, viên sủi', active: true, sortOrder: 1 },
    { code: 'UNIT-GOI', name: 'Gói', description: 'Dạng bột hoặc cốm pha hỗn dịch/dung dịch uống', active: true, sortOrder: 2 },
    { code: 'UNIT-CHAI', name: 'Chai', description: 'Dung dịch uống, siro, cồn sát khuẩn, nước súc họng', active: true, sortOrder: 3 },
    { code: 'UNIT-LO', name: 'Lọ', description: 'Thuốc nhỏ mắt, mũi, tai hoặc dung dịch tiêm truyền', active: true, sortOrder: 4 },
    { code: 'UNIT-ONG', name: 'Ống', description: 'Dung dịch uống, hỗn dịch khí dung hoặc ống tiêm', active: true, sortOrder: 5 },
    { code: 'UNIT-TUYP', name: 'Tuýp', description: 'Dạng kem, mỡ, gel dùng ngoài bôi da', active: true, sortOrder: 6 },
    { code: 'UNIT-VI', name: 'Vỉ', description: 'Vỉ thuốc nén hoặc nang', active: true, sortOrder: 7 },
    { code: 'UNIT-HOP', name: 'Hộp', description: 'Quy cách đóng gói hộp nguyên', active: true, sortOrder: 8 },
    { code: 'UNIT-TUI', name: 'Túi', description: 'Túi dịch truyền hoặc túi bột', active: true, sortOrder: 9 },
    { code: 'UNIT-BINH-XIT', name: 'Bình xịt', description: 'Thuốc xịt mũi, xịt họng hoặc bình hít định liều', active: true, sortOrder: 10 },
    { code: 'UNIT-MIENG-DAN', name: 'Miếng dán', description: 'Miếng dán thẩm thấu qua da giảm đau hoặc hạ sốt', active: true, sortOrder: 11 },
    { code: 'UNIT-GIOT', name: 'Giọt', description: 'Dung dịch đậm đặc nhỏ giọt', active: true, sortOrder: 12 },
  ]);

  protected readonly medicationDosages = signal<MedicationDosageOption[]>([
    { code: 'DOS-VIEN-2X', unitName: 'Viên', dosageFormat: '1 viên/lần x 2 lần/ngày (Sáng 1, Tối 1)', description: 'Dùng 2 lần sáng và tối sau ăn', active: true, sortOrder: 1 },
    { code: 'DOS-VIEN-3X', unitName: 'Viên', dosageFormat: '1 viên/lần x 3 lần/ngày (Sáng 1, Trưa 1, Tối 1)', description: 'Dùng 3 lần mỗi ngày', active: true, sortOrder: 2 },
    { code: 'DOS-VIEN-1X-S', unitName: 'Viên', dosageFormat: '1 viên/lần x 1 lần/ngày (Sáng 1)', description: 'Dùng 1 lần cố định buổi sáng', active: true, sortOrder: 3 },
    { code: 'DOS-VIEN-1X-T', unitName: 'Viên', dosageFormat: '1 viên/lần x 1 lần/ngày (Tối 1 trước khi ngủ)', description: 'Dùng 1 lần cố định buổi tối', active: true, sortOrder: 4 },
    { code: 'DOS-VIEN-PAIN', unitName: 'Viên', dosageFormat: '2 viên/lần khi sốt/đau (cách nhau 4-6 giờ, tối đa 4 lần/ngày)', description: 'Dùng khi có triệu chứng sốt hoặc đau', active: true, sortOrder: 5 },
    { code: 'DOS-GOI-2X', unitName: 'Gói', dosageFormat: '1 gói/lần x 2 lần/ngày (Sáng 1, Tối 1)', description: 'Pha với nước ấm uống sáng, tối', active: true, sortOrder: 10 },
    { code: 'DOS-GOI-3X', unitName: 'Gói', dosageFormat: '1 gói/lần x 3 lần/ngày (Sáng 1, Trưa 1, Tối 1)', description: 'Pha nước uống 3 lần/ngày', active: true, sortOrder: 11 },
    { code: 'DOS-GOI-1X', unitName: 'Gói', dosageFormat: '1 gói/lần x 1 lần/ngày (Sáng 1)', description: 'Pha nước uống vào buổi sáng', active: true, sortOrder: 12 },
    { code: 'DOS-GOI-PAIN', unitName: 'Gói', dosageFormat: '1 gói/lần khi đau/sốt (cách tối thiểu 4-6 giờ)', description: 'Pha với nước ấm khi sốt hoặc đau', active: true, sortOrder: 13 },
    { code: 'DOS-GOI-MIX', unitName: 'Gói', dosageFormat: '1-2 gói/ngày chia 2 lần pha nước', description: 'Hòa tan hoàn toàn trước khi uống', active: true, sortOrder: 14 },
    { code: 'DOS-ONG-2X', unitName: 'Ống', dosageFormat: '1 ống/lần x 2 lần/ngày (Sáng 1, Tối 1)', description: 'Lắc đều trước khi bẻ ống uống', active: true, sortOrder: 20 },
    { code: 'DOS-ONG-1X', unitName: 'Ống', dosageFormat: '1 ống/lần x 1 lần/ngày (Sáng 1)', description: 'Uống 1 ống vào buổi sáng', active: true, sortOrder: 21 },
    { code: 'DOS-ML-23X', unitName: 'Chai', dosageFormat: '5 ml/lần x 2-3 lần/ngày', description: 'Đo bằng cốc chia vạch đi kèm', active: true, sortOrder: 22 },
    { code: 'DOS-ML-2X', unitName: 'Chai', dosageFormat: '10 ml/lần x 2 lần/ngày sau ăn', description: 'Uống sau ăn', active: true, sortOrder: 23 },
    { code: 'DOS-LO-EYE', unitName: 'Lọ', dosageFormat: 'Nhỏ 1-2 giọt vào mắt bị bệnh, ngày 3-4 lần', description: 'Nhỏ mắt cách nhau 4-6 giờ', active: true, sortOrder: 24 },
    { code: 'DOS-TUYP-2X', unitName: 'Tuýp', dosageFormat: 'Bôi 1 lớp mỏng 2 lần/ngày (Sáng, Tối)', description: 'Vệ sinh sạch vùng tổn thương trước khi thoa', active: true, sortOrder: 30 },
    { code: 'DOS-TUYP-3X', unitName: 'Tuýp', dosageFormat: 'Bôi 1 lớp mỏng 3 lần/ngày', description: 'Thoa nhẹ nhàng, tránh tiếp xúc mắt', active: true, sortOrder: 31 },
    { code: 'DOS-TUYP-SKIN', unitName: 'Tuýp', dosageFormat: 'Thoa nhẹ lên vùng da tổn thương 1-2 lần/ngày', description: 'Dùng ngoài da', active: true, sortOrder: 32 },
    { code: 'DOS-GIOT-3X', unitName: 'Giọt', dosageFormat: '1-2 giọt/lần x 3 lần/ngày', description: 'Nhỏ trực tiếp hoặc pha chút nước', active: true, sortOrder: 40 },
    { code: 'DOS-GIOT-2X', unitName: 'Giọt', dosageFormat: '2-3 giọt/lần x 2 lần/ngày', description: 'Nhỏ theo chỉ dẫn', active: true, sortOrder: 41 },
    { code: 'DOS-XIT-2X', unitName: 'Bình xịt', dosageFormat: 'Xịt 1-2 nhát/lần x 2 lần/ngày', description: 'Xịt sau khi vệ sinh sạch khoang mũi/họng', active: true, sortOrder: 50 },
    { code: 'DOS-XIT-NOSE', unitName: 'Bình xịt', dosageFormat: 'Xịt 1 nhát vào mỗi bên mũi x 2 lần/ngày', description: 'Hít nhẹ khi ấn đầu xịt', active: true, sortOrder: 51 },
    { code: 'DOS-XIT-BREATH', unitName: 'Bình xịt', dosageFormat: 'Xịt 2 nhát khi khó thở (cách tối thiểu 4 giờ)', description: 'Bình xịt định liều', active: true, sortOrder: 52 },
    { code: 'DOS-DAN-1X', unitName: 'Miếng dán', dosageFormat: 'Dán 1 miếng/ngày (thay sau 24 giờ)', description: 'Dán lên vùng da khô sạch không trầy xước', active: true, sortOrder: 60 },
    { code: 'DOS-DAN-PAIN', unitName: 'Miếng dán', dosageFormat: 'Dán 1 miếng khi đau (tối đa 8 giờ)', description: 'Gỡ bỏ sau tối đa 8 giờ sử dụng', active: true, sortOrder: 61 },
  ]);

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

  protected readonly availableSpecialties = computed<string[]>(() => {
    return this.specialtiesFromApi().map((s) => s.name);
  });

  protected readonly standardUnits = computed<string[]>(() => {
    return this.medicationUnits().map((u) => u.name);
  });

  protected isKnownSpecialty(name: string): boolean {
    if (!name) return true;
    if (name === 'Dùng chung (Đa khoa)') return true;
    return this.availableSpecialties().includes(name);
  }

  protected readonly filteredDosagesForUnit = computed<MedicationDosageOption[]>(() => {
    const rawUnit = this.unit().trim().toLowerCase();
    const all = this.medicationDosages();
    if (!rawUnit) return all;
    const matched = all.filter((d) => d.unitName && d.unitName.trim().toLowerCase() === rawUnit);
    return matched.length > 0 ? matched : all;
  });

  protected readonly standardDosageFormats = computed<string[]>(() => {
    return this.filteredDosagesForUnit().map((d) => d.dosageFormat);
  });

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
      this.authApi.getMedicationUnits().subscribe({
        next: (items) => {
          if (items && items.length > 0) this.medicationUnits.set(items);
        },
        error: () => {},
      });
    }
    if (this.authApi.getMedicationDosages) {
      this.authApi.getMedicationDosages().subscribe({
        next: (items) => {
          if (items && items.length > 0) this.medicationDosages.set(items);
        },
        error: () => {},
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
    this.unit.set(item.unit || 'Viên');
    this.error.set('');
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    if (!this.saving()) this.modalOpen.set(false);
  }

  protected save(): void {
    const code = this.code().trim().toUpperCase();
    const name = this.name().trim();
    if (!/^[A-Z0-9_-]{2,50}$/.test(code) || !name || name.length > 200) {
      this.error.set('Nhập mã thuốc hợp lệ và tên thuốc không quá 200 ký tự.');
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const editingId = this.editingId();
    const hasMetadata = Boolean(
      this.category().trim() ||
      this.specialties().trim() ||
      this.defaultDosage().trim() ||
      this.defaultInstructions().trim() ||
      this.unit().trim()
    );
    const metadata = hasMetadata ? {
      category: this.category().trim() || undefined,
      specialties: this.specialties().trim() || undefined,
      defaultDosage: this.defaultDosage().trim() || undefined,
      defaultInstructions: this.defaultInstructions().trim() || undefined,
      unit: this.unit().trim() || undefined,
    } : undefined;
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
}
