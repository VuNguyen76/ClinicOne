import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthApiService, apiErrorMessage, PatientProfileItem } from '../../../core/auth/auth-api.service';
import { AccountMenu } from '../../../shared/account-menu/account-menu';

@Component({
  selector: 'app-booking',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, AccountMenu],
  templateUrl: './booking.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Booking implements OnInit {
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
    profileId: [''],
  });
  protected profiles: PatientProfileItem[] = [];
  protected profilesLoading = true;
  protected busy = false;
  protected error = '';

  ngOnInit(): void {
    this.authApi.getPatientProfiles().subscribe({
      next: (profiles) => {
        this.profiles = profiles;
        const primary = profiles.find((profile) => profile.primaryProfile) ?? profiles[0];
        if (primary) this.form.controls.profileId.setValue(primary.id);
        this.profilesLoading = false;
      },
      error: (response) => {
        this.profilesLoading = false;
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

  protected submit(): void {
    this.error = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.busy = true;
    const value = this.form.getRawValue();
    this.authApi.createAppointment({ ...value, profileId: value.profileId || undefined }).subscribe({
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
