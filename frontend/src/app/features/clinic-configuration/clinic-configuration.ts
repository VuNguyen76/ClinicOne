import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AccountMenu } from '../../shared/account-menu/account-menu';
import { StaffWorkspaceShell } from '../../shared/staff-workspace-shell/staff-workspace-shell';
import { ApiErrorResponse, AuthApiService, ClinicConfigurationResponse, apiErrorMessage } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-clinic-configuration',
  standalone: true,
  imports: [FormsModule, RouterLink, MatIconModule, AccountMenu, DatePipe, StaffWorkspaceShell],
  templateUrl: './clinic-configuration.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClinicConfiguration implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly unitName = signal('');
  protected readonly departmentName = signal('');
  protected readonly holdMinutes = signal(10);
  protected readonly cancellationThresholdHours = signal(12);
  protected readonly updatedAt = signal('');
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly message = signal('');
  protected readonly error = signal('');

  ngOnInit(): void {
    this.authApi.getClinicConfiguration().subscribe({
      next: (configuration) => this.apply(configuration),
      error: (response: ApiErrorResponse) => { this.error.set(apiErrorMessage(response)); this.loading.set(false); },
    });
  }

  protected save(): void {
    this.error.set('');
    this.message.set('');
    this.saving.set(true);
    this.authApi.updateClinicConfiguration({
      unitName: this.unitName().trim(),
      departmentName: this.departmentName().trim(),
      holdMinutes: Number(this.holdMinutes()),
      cancellationThresholdHours: Number(this.cancellationThresholdHours()),
    }).subscribe({
      next: (configuration) => { this.apply(configuration); this.message.set('Đã lưu cấu hình.'); this.saving.set(false); },
      error: (response: ApiErrorResponse) => { this.error.set(apiErrorMessage(response)); this.saving.set(false); },
    });
  }

  protected setNumber(target: 'hold' | 'cancel', value: string): void {
    const parsed = Number(value);
    if (target === 'hold') this.holdMinutes.set(Number.isFinite(parsed) ? parsed : 10);
    else this.cancellationThresholdHours.set(Number.isFinite(parsed) ? parsed : 12);
  }

  private apply(configuration: ClinicConfigurationResponse): void {
    this.unitName.set(configuration.unitName);
    this.departmentName.set(configuration.departmentName);
    this.holdMinutes.set(configuration.holdMinutes);
    this.cancellationThresholdHours.set(configuration.cancellationThresholdHours);
    this.updatedAt.set(configuration.updatedAt);
    this.loading.set(false);
  }
}
