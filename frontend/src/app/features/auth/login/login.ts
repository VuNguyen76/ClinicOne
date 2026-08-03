import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-login',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  templateUrl: './login.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);

  protected readonly notice = signal('');

  readonly form = this.formBuilder.nonNullable.group({
    phone: ['', [Validators.required, Validators.pattern(/^0\d{9}$/)]],
  });

  protected submit(): void {
    this.notice.set('');

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.notice.set(
      'Số điện thoại hợp lệ. Bước tiếp theo sẽ được kết nối với dịch vụ xác thực của ClinicOne.',
    );
  }

  protected showNotReadyMessage(action: string): void {
    this.notice.set(`${action} sẽ được bổ sung trong phiên bản tiếp theo.`);
  }
}
