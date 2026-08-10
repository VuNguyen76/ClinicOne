import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AuthApiService, OperationalStatisticsResponse, apiErrorMessage } from '../../core/auth/auth-api.service';
import { AccountMenu } from '../../shared/account-menu/account-menu';

@Component({
  selector: 'app-admin-statistics',
  standalone: true,
  imports: [FormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './admin-statistics.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminStatistics implements OnInit {
  private readonly authApi = inject(AuthApiService);
  protected readonly from = signal(this.today());
  protected readonly to = signal(this.today());
  protected readonly specialty = signal('');
  protected readonly specialties = signal<{ code: string; name: string; description: string }[]>([]);
  protected readonly doctorId = signal('');
  protected readonly doctors = signal<{ staffId: string; fullName: string; specialty: string | null }[]>([]);
  protected readonly groupBy = signal<'DAY' | 'WEEK' | 'MONTH'>('DAY');
  protected readonly report = signal<OperationalStatisticsResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  ngOnInit(): void {
    this.authApi.getDoctors().subscribe({ next: (doctors) => this.doctors.set(doctors) });
    this.authApi.getSpecialties().subscribe({
      next: (specialties) => {
        this.specialties.set(specialties);
        if (!this.specialty() && specialties.length > 0) this.specialty.set(specialties[0].name);
        this.load();
      },
      error: (response: ApiErrorResponse) => {
        this.error.set(apiErrorMessage(response));
      },
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    this.authApi.getOperationalStatistics(this.from(), this.to(), this.specialty(), this.doctorId() || undefined, this.groupBy()).subscribe({
      next: (result) => { this.report.set(result); this.loading.set(false); },
      error: (response: ApiErrorResponse) => { this.report.set(null); this.error.set(apiErrorMessage(response)); this.loading.set(false); },
    });
  }

  protected updateFrom(value: string): void { this.from.set(value); }
  protected updateTo(value: string): void { this.to.set(value); }
  protected updateSpecialty(value: string): void { this.specialty.set(value); }
  protected updateDoctor(value: string): void { this.doctorId.set(value); }
  protected updateGroupBy(value: string): void {
    if (value === 'WEEK' || value === 'MONTH' || value === 'DAY') this.groupBy.set(value);
  }
  protected formatAverage(value: number | null): string { return value === null ? '—' : `${value.toFixed(1)} phút`; }

  private today(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  }
}
