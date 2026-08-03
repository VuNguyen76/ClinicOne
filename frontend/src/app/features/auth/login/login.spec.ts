import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Login } from './login';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should keep the form invalid until email and password are valid', () => {
    expect(component.form.invalid).toBe(true);

    component.form.controls.email.setValue('patient@example.com');
    component.form.controls.password.setValue('safe-password');

    expect(component.form.valid).toBe(true);
  });
});
