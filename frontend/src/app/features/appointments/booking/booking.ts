import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, apiErrorMessage } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';

@Component({
  selector: 'app-booking',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './booking.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Booking {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly form = this.formBuilder.nonNullable.group({
    specialty: ['', [Validators.required, Validators.maxLength(120)]],
    doctorName: ['', [Validators.required, Validators.maxLength(120)]],
    appointmentDate: ['', [Validators.required]],
    startTime: ['', [Validators.required]],
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
  });
  protected busy = false;
  protected error = '';

  protected submit(): void {
    this.error = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.busy = true;
    this.authApi.createAppointment(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl('/dashboard'),
      error: (response) => {
        this.busy = false;
        if (response.status === 401 || response.status === 403) {
          sessionStorage.removeItem('clinicOneAccessToken');
          sessionStorage.removeItem('clinicOnePatientName');
          void this.router.navigateByUrl('/login');
          return;
        }
        this.error = apiErrorMessage(response);
      },
    });
  }
}
