import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { ApiErrorResponse, AuthApiService, MedicationSuggestionResponse, SpecialtyOption, apiErrorMessage } from '../../core/auth/auth-api.service';
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
  protected readonly medications = signal<MedicationSuggestionResponse[]>([]);
  protected readonly specialtiesFromApi = signal<SpecialtyOption[]>([]);
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

  protected readonly defaultSpecialties: string[] = [
    'Khám Tổng Quát',
    'Nội Tổng Quát',
    'Tim Mạch',
    'Hô Hấp',
    'Tiêu Hóa',
    'Nhi Khoa',
    'Tai Mũi Họng',
    'Mắt',
    'Da Liễu',
    'Cơ Xương Khớp',
    'Răng Hàm Mặt',
    'Sản Phụ Khoa',
  ];

  protected readonly availableSpecialties = computed<string[]>(() => {
    const apiSpecs = this.specialtiesFromApi().map((s) => s.name);
    const combined = new Set<string>([...apiSpecs, ...this.defaultSpecialties]);
    return Array.from(combined);
  });

  protected isKnownSpecialty(name: string): boolean {
    if (!name) return true;
    if (name === 'Dùng chung (Đa khoa)') return true;
    return this.availableSpecialties().includes(name);
  }

  protected readonly standardUnits: string[] = [
    'Viên',
    'Gói',
    'Chai',
    'Lọ',
    'Ống',
    'Tuýp',
    'Vỉ',
    'Hộp',
    'Túi',
    'Bình xịt',
    'Miếng dán',
    'Giọt',
  ];

  protected readonly standardCategories: string[] = [
    'Hạ sốt & Giảm đau',
    'Kháng sinh & Kháng khuẩn',
    'Kháng viêm & Giảm phù nề',
    'Tim mạch & Huyết áp',
    'Hô hấp & Giảm ho',
    'Tiêu hóa & Dạ dày',
    'Dị ứng & Kháng Histamin',
    'Vitamin & Khoáng chất',
    'Mắt & Tai Mũi Họng',
    'Da liễu & Dùng ngoài',
  ];

  protected readonly standardDosageFormats = computed<string[]>(() => {
    const rawUnit = this.unit().trim();
    const u = (rawUnit || 'viên').toLowerCase();

    if (u.includes('gói')) {
      return [
        '1 gói/lần x 2 lần/ngày (Sáng 1, Tối 1)',
        '1 gói/lần x 3 lần/ngày (Sáng 1, Trưa 1, Tối 1)',
        '1 gói/lần x 1 lần/ngày (Sáng 1)',
        '1 gói/lần khi đau/sốt (cách tối thiểu 4-6 giờ)',
        '1-2 gói/ngày chia 2 lần pha nước',
      ];
    }
    if (u.includes('ống') || u.includes('chai') || u.includes('lọ') || u.includes('ml')) {
      return [
        '1 ống/lần x 2 lần/ngày (Sáng 1, Tối 1)',
        '1 ống/lần x 1 lần/ngày (Sáng 1)',
        '5 ml/lần x 2-3 lần/ngày',
        '10 ml/lần x 2 lần/ngày sau ăn',
        '1 lọ/ngày chia 2 lần uống',
      ];
    }
    if (u.includes('tuýp') || u.includes('kem') || u.includes('gel') || u.includes('mỡ')) {
      return [
        'Bôi 1 lớp mỏng 2 lần/ngày (Sáng, Tối)',
        'Bôi 1 lớp mỏng 3 lần/ngày',
        'Thoa nhẹ lên vùng da tổn thương 1-2 lần/ngày',
      ];
    }
    if (u.includes('giọt')) {
      return [
        '1-2 giọt/lần x 3 lần/ngày',
        '2-3 giọt/lần x 2 lần/ngày',
        '1 giọt vào mỗi mắt x 2 lần/ngày',
      ];
    }
    if (u.includes('xịt') || u.includes('bình')) {
      return [
        'Xịt 1-2 nhát/lần x 2 lần/ngày',
        'Xịt 1 nhát vào mỗi bên mũi x 2 lần/ngày',
        'Xịt 2 nhát khi khó thở (cách tối thiểu 4 giờ)',
      ];
    }
    if (u.includes('miếng') || u.includes('dán')) {
      return [
        'Dán 1 miếng/ngày (thay sau 24 giờ)',
        'Dán 1 miếng khi đau (tối đa 8 giờ)',
      ];
    }
    return [
      `1 ${u}/lần x 2 lần/ngày (Sáng 1, Tối 1)`,
      `1 ${u}/lần x 3 lần/ngày (Sáng 1, Trưa 1, Tối 1)`,
      `1 ${u}/lần x 1 lần/ngày (Sáng 1)`,
      `1 ${u}/lần x 1 lần/ngày (Tối 1 trước khi ngủ)`,
      `2 ${u}/lần khi sốt/đau (cách nhau 4-6 giờ, tối đa 4 lần/ngày)`,
    ];
  });

  protected readonly standardInstructionOptions: string[] = [
    'Uống sau khi ăn no với nước ấm',
    'Uống trước khi ăn 30 phút',
    'Uống cách xa bữa ăn với nhiều nước',
    'Uống trước khi đi ngủ',
    'Pha với 100-150ml nước ấm',
    'Nhai kỹ trước khi nuốt',
    'Uống khi sốt cao trên 38.5°C cách 4-6 giờ',
    'Bôi ngoài da sau khi vệ sinh sạch sẽ',
    'Nhỏ mắt sau khi rửa tay sạch, tránh chạm đầu lọ',
    'Xịt sau khi làm sạch khoang mũi',
  ];

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
  protected readonly activeCount = computed(() => this.medications().filter((item) => item.active).length);
  protected readonly inactiveCount = computed(() => this.medications().filter((item) => !item.active).length);

  ngOnInit(): void {
    this.load();
  }

  private specialtiesLoaded = false;

  private loadSpecialties(): void {
    if (this.specialtiesLoaded) return;
    if (this.authApi.getSpecialties) {
      this.authApi.getSpecialties().subscribe({
        next: (items) => {
          this.specialtiesFromApi.set(items || []);
          this.specialtiesLoaded = true;
        },
        error: () => {},
      });
    }
  }

  protected openCreate(): void {
    this.loadSpecialties();
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
    this.loadSpecialties();
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
