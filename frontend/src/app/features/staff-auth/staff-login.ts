import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ApiErrorResponse, AuthApiService, apiErrorMessage } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-staff-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatIconModule],
  templateUrl: './staff-login.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StaffLogin {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authApi = inject(AuthApiService);
  private readonly router = inject(Router);

  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly form = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(80)]],
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(72)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set('');
    this.busy.set(true);
    const { username, password } = this.form.getRawValue();
    this.authApi.staffLogin(username, password).subscribe({
      next: (session) => {
        this.busy.set(false);
        const destination = session.role === 'DOCTOR'
          ? '/staff'
          : session.role === 'ADMIN' || session.role === 'COORDINATOR'
            ? '/admin/rooms'
            : '/home';
        void this.router.navigateByUrl(destination);
      },
      error: (response) => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(response as ApiErrorResponse));
      },
    });
  }
}
