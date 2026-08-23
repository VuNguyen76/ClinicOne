import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { PatientHeader } from './patient-header';

describe('PatientHeader', () => {
  let component: PatientHeader;
  let fixture: ComponentFixture<PatientHeader>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PatientHeader],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientHeader);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders brand title and all six public navigation links', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('ClinicOne');
    expect(text).toContain('Trang chủ');
    expect(text).toContain('Giới thiệu');
    expect(text).toContain('Quy trình');
    expect(text).toContain('Hướng dẫn');
    expect(text).toContain('Thắc mắc');
    expect(text).toContain('Liên hệ');
    expect(text).toContain('1900000');
  });

  it('toggles mobile menu on trigger', () => {
    expect(component.mobileMenuOpen()).toBe(false);
    component.toggleMenu();
    expect(component.mobileMenuOpen()).toBe(true);
    component.closeMenu();
    expect(component.mobileMenuOpen()).toBe(false);
  });
});
