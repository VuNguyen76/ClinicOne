import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import {
  ApiErrorResponse,
  apiErrorMessage,
  AuthApiService,
  AvailableReplacementSlot,
  RescheduleCaseResponse,
} from '../../core/auth/auth-api.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';

@Component({
  selector: 'app-rescheduling',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu],
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
  protected readonly form = this.formBuilder.nonNullable.group({
    appointmentDate: ['', [Validators.required]],
    startTime: ['', [Validators.required]],
    doctorName: ['', [Validators.required, Validators.maxLength(120)]],
    doctorId: [''],
  });

  ngOnInit(): void {
    this.loadCases();
  }

  protected selectCase(item: RescheduleCaseResponse): void {
    this.selectedCase.set(item);
    this.error.set('');
    this.notice.set('');
    this.form.reset({ appointmentDate: '', startTime: '', doctorName: '', doctorId: '' });
    this.alternatives.set([]);
    this.alternativesLoading.set(true);
    this.authApi.getReplacementSlots(item.id).subscribe({
      next: (slots) => { this.alternatives.set(slots); this.alternativesLoading.set(false); },
      error: (response) => { this.alternativesLoading.set(false); this.error.set(apiErrorMessage(response)); },
    });
  }

  protected chooseAlternative(slot: AvailableReplacementSlot): void {
    this.form.setValue({
      appointmentDate: slot.appointmentDate,
      startTime: slot.startTime.slice(0, 5),
      doctorName: slot.doctorName,
      doctorId: slot.doctorId ?? '',
    });
    this.error.set('');
  }

  protected resolve(): void {
    const item = this.selectedCase();
    if (!item || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    this.authApi.resolveRescheduleCase(item.id, value.appointmentDate, value.startTime,
      value.doctorName, value.doctorId || null).subscribe({
        next: (resolved) => {
          this.cases.update((items) => items.filter((entry) => entry.id !== resolved.id));
          this.selectedCase.set(null);
          this.alternatives.set([]);
          this.saving.set(false);
          this.notice.set(`Đã sắp xếp lại lịch ${resolved.appointmentCode}.`);
        },
        error: (response) => { this.saving.set(false); this.error.set(apiErrorMessage(response)); },
      });
  }

  protected formatDate(value: string): string {
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('vi-VN').format(new Date(year, month - 1, day));
  }

  protected formatTime(value: string): string {
    return value.slice(0, 5);
  }

  private loadCases(): void {
    this.authApi.getRescheduleCases().subscribe({
      next: (items) => {
        this.cases.set(items);
        this.loading.set(false);
        if (items[0]) this.selectCase(items[0]);
      },
      error: (response) => { this.loading.set(false); this.handleError(response); },
    });
  }

  private handleError(response: { status?: number } & ApiErrorResponse): void {
    this.error.set(apiErrorMessage(response));
  }
}
