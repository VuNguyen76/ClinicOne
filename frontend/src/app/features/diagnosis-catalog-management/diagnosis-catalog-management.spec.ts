import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DiagnosisCatalogManagement } from './diagnosis-catalog-management';

describe('DiagnosisCatalogManagement', () => {
  let fixture: ComponentFixture<DiagnosisCatalogManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneAccessToken', 'staff-token');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['ADMIN']));
    await TestBed.configureTestingModule({
      imports: [DiagnosisCatalogManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(DiagnosisCatalogManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('lets an administrator add a diagnosis to the suggestion catalog', () => {
    http.expectOne('/api/v1/admin/diagnoses').flush([]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="open-create-diagnosis"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as {
      code: { set(value: string): void }; name: { set(value: string): void }; save(): void;
    };
    component.code.set('HEADACHE_TENSION');
    component.name.set('Đau đầu căng thẳng');
    component.save();
    const request = http.expectOne('/api/v1/admin/diagnoses');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ code: 'HEADACHE_TENSION', name: 'Đau đầu căng thẳng' });
  });

  it('renders inside the staff ERP workspace', () => {
    http.expectOne('/api/v1/admin/diagnoses').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="staff-workspace-shell"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="staff-window-title"]')?.textContent)
      .toContain('Chẩn đoán');
  });
});
