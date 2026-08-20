import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import {
  ApiErrorResponse,
  apiErrorMessage,
  AuthApiService,
  AvailableReplacementSlot,
  RescheduleCaseResponse,
} from '../../core/auth/auth-api.service';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { hasStaffRole } from '../../core/auth/auth.guard';

@Component({
  selector: 'app-rescheduling',
  standalone: true,
  imports: [ReactiveFormsModule, MatIconModule, StaffWorkspaceShell],
  templateUrl: './rescheduling.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Rescheduling implements OnInit {
  private readonly authApi = inject(AuthApiService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly cases = signal<RescheduleCaseResponse[]>([]);
  protected readonly selectedCase = signal<RescheduleCaseResponse | null>(null);
  protected readonly alternatives = signal<AvailableReplacementSlot[]>([]);
  protected readonly loading = signal(true);
  protected readonly alternativesLoading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly searchTerm = signal('');
  protected readonly form = this.formBuilder.nonNullable.group({
    appointmentDate: ['', [Validators.required]],
    startTime: ['', [Validators.required]],
    doctorName: ['', [Validators.required, Validators.maxLength(120)]],
    doctorId: [''],
  });

  protected filteredCases(): RescheduleCaseResponse[] {
    const q = this.searchTerm().trim().toLowerCase();
    if (!q) return this.cases();
    return this.cases().filter((c) =>
      c.appointmentCode.toLowerCase().includes(q) ||
      c.specialty.toLowerCase().includes(q) ||
      c.oldDoctorName.toLowerCase().includes(q) ||
      (c.reason && c.reason.toLowerCase().includes(q))
    );
  }

  protected totalCasesCount(): number {
    return this.cases().length;
  }

  protected pendingCasesCount(): number {
    return this.cases().filter((c) => c.status === 'OPEN' || c.status === 'PENDING' || !c.status).length;
  }

  protected resolvedCasesCount(): number {
    return this.cases().filter((c) => c.status === 'RESOLVED').length;
  }

  ngOnInit(): void {
    this.loadCases();
  }

  protected loadCases(): void {
    this.loading.set(true);
    this.authApi.getRescheduleCases().subscribe({
      next: (items) => {
        this.cases.set(items);
        this.loading.set(false);
        if (items[0]) this.selectCase(items[0]);
      },
      error: (response) => {
        this.loading.set(false);
        this.handleError(response);
      },
    });
  }

  protected readonly selectedSlot = signal<AvailableReplacementSlot | null>(null);
  protected readonly showManualForm = signal(false);

  protected selectCase(item: RescheduleCaseResponse): void {
    this.selectedCase.set(item);
    this.selectedSlot.set(null);
    this.showManualForm.set(false);
    this.error.set('');
    this.notice.set('');
    this.form.reset({ appointmentDate: '', startTime: '', doctorName: '', doctorId: '' });
    this.alternatives.set([]);
    this.alternativesLoading.set(true);
    this.authApi.getReplacementSlots(item.id).subscribe({
      next: (slots) => {
        this.alternatives.set(slots);
        this.alternativesLoading.set(false);
        if (slots[0]) {
          this.chooseAlternative(slots[0]);
        }
      },
      error: (response) => {
        this.alternativesLoading.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected chooseAlternative(slot: AvailableReplacementSlot): void {
    this.selectedSlot.set(slot);
    this.form.setValue({
      appointmentDate: slot.appointmentDate,
      startTime: slot.startTime.slice(0, 5),
      doctorName: slot.doctorName,
      doctorId: slot.doctorId ?? '',
    });
    this.error.set('');
  }

  protected onSlotDropdownChange(event: Event): void {
    const target = event.target as HTMLSelectElement;
    const idx = Number(target.value);
    const slots = this.alternatives();
    if (slots[idx]) {
      this.chooseAlternative(slots[idx]);
    }
  }

  protected isSlotSelected(slot: AvailableReplacementSlot): boolean {
    const sel = this.selectedSlot();
    if (!sel) return false;
    return sel.appointmentDate === slot.appointmentDate && sel.startTime === slot.startTime && sel.doctorId === slot.doctorId;
  }

  protected isFemaleDoctor(doctorName: string): boolean {
    const lower = (doctorName || '').toLowerCase();
    return lower.includes('hà') || lower.includes('nga') || lower.includes('lan') || lower.includes('thảo') || lower.includes('mai');
  }

  protected getDoctorSvgAvatar(doctorName: string): string {
    if (this.isFemaleDoctor(doctorName)) {
      return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120"><circle cx="60" cy="60" r="60" fill="%23e0f2fe"/><circle cx="60" cy="46" r="22" fill="%23fed7aa"/><path d="M38 42c0-12 10-20 22-20s22 8 22 20c0 4-2 10-4 12-2-8-8-12-18-12s-16 4-18 12c-2-2-4-8-4-12z" fill="%23334155"/><path d="M60 72c-20 0-36 14-36 34v14h72v-14c0-20-16-34-36-34z" fill="%23ffffff"/><path d="M48 72l12 24 12-24" fill="%230284c7"/><path d="M42 86c0 10 8 18 18 18s18-8 18-18" fill="none" stroke="%23334155" stroke-width="3" stroke-linecap="round"/><circle cx="60" cy="104" r="3" fill="%230284c7"/><path d="M50 46c2 1 6 1 8 0m4 0c2 1 6 1 8 0" fill="none" stroke="%23334155" stroke-width="1.5" stroke-linecap="round"/><path d="M56 56c2 2 6 2 8 0" fill="none" stroke="%23f43f5e" stroke-width="2" stroke-linecap="round"/></svg>';
    }
    return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120"><circle cx="60" cy="60" r="60" fill="%23ccfbf1"/><circle cx="60" cy="46" r="22" fill="%23fde047" fill-opacity="0.6"/><path d="M38 40c0-14 10-22 22-22s22 8 22 22v4c-6-4-12-6-22-6s-16 2-22 6v-4z" fill="%231e293b"/><path d="M60 72c-20 0-36 14-36 34v14h72v-14c0-20-16-34-36-34z" fill="%23ffffff"/><path d="M48 72l12 24 12-24" fill="%230f766e"/><path d="M42 86c0 10 8 18 18 18s18-8 18-18" fill="none" stroke="%23334155" stroke-width="3" stroke-linecap="round"/><circle cx="60" cy="104" r="3" fill="%230f766e"/><path d="M50 46c2 1 6 1 8 0m4 0c2 1 6 1 8 0" fill="none" stroke="%23334155" stroke-width="1.5" stroke-linecap="round"/><path d="M56 56c2 2 6 2 8 0" fill="none" stroke="%23e11d48" stroke-width="1.5" stroke-linecap="round"/></svg>';
  }

  protected handleAvatarError(event: Event, doctorName: string): void {
    const target = event.target as HTMLImageElement;
    if (target) {
      target.src = this.getDoctorSvgAvatar(doctorName);
    }
  }

  protected getDoctorAvatar(doctorName: string): string {
    return this.getDoctorSvgAvatar(doctorName);
  }

  protected resolve(): void {
    if (!this.canResolve()) {
      this.error.set('Chỉ điều phối viên được xác nhận sắp xếp lại lịch.');
      return;
    }
    const item = this.selectedCase();
    if (!item || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    this.authApi.resolveRescheduleCase(
      item.id,
      value.appointmentDate,
      value.startTime,
      value.doctorName,
      value.doctorId || null,
    ).subscribe({
      next: (resolved) => {
        this.cases.update((items) => items.filter((entry) => entry.id !== resolved.id));
        const remaining = this.cases();
        if (remaining.length > 0) {
          this.selectCase(remaining[0]);
        } else {
          this.selectedCase.set(null);
          this.alternatives.set([]);
          this.selectedSlot.set(null);
        }
        this.saving.set(false);
        this.notice.set(`Đã sắp xếp lại lịch ${resolved.appointmentCode}.`);
      },
      error: (response) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected formatDate(value: string): string {
    if (!value) return '';
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN').format(new Date(year, month - 1, day));
  }

  protected formatTime(value: string): string {
    return value ? value.slice(0, 5) : '';
  }

  protected canResolve(): boolean {
    return hasStaffRole('COORDINATOR');
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    this.error.set(apiErrorMessage(response));
  }
}
