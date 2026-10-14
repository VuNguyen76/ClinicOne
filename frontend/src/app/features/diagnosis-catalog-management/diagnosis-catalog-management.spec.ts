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
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['COORDINATOR']));
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

  it('lets a coordinator add a diagnosis to the suggestion catalog', () => {
    http.expectOne((request) => request.url === '/api/v1/admin/diagnoses' && request.params.get('activeOnly') === 'false').flush([]);
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

  it('lets an administrator view diagnoses without management actions', () => {
    http.expectOne((request) => request.url === '/api/v1/admin/diagnoses' && request.params.get('activeOnly') === 'false').flush([]);
    fixture.destroy();
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['ADMIN']));
    fixture = TestBed.createComponent(DiagnosisCatalogManagement);
    fixture.detectChanges();
    http.expectOne((request) => request.url === '/api/v1/admin/diagnoses' && request.params.get('activeOnly') === 'false')
      .flush([{ id: 'dx-view', code: 'I10', name: 'Tăng huyết áp', active: true }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Tăng huyết áp');
    expect(fixture.nativeElement.querySelector('[data-testid="open-create-diagnosis"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="edit-diagnosis-dx-view"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="toggle-diagnosis-dx-view"]')).toBeNull();
  });

  it('renders inside the staff ERP workspace', () => {
    http.expectOne((request) => request.url === '/api/v1/admin/diagnoses' && request.params.get('activeOnly') === 'false').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="staff-workspace-shell"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="staff-window-title"]')?.textContent)
      .toContain('Chẩn đoán');
  });

  it('updates and safely removes diagnoses through the admin APIs', () => {
    http.expectOne((request) => request.url === '/api/v1/admin/diagnoses' && request.params.get('activeOnly') === 'false')
      .flush([{ id: 'dx-1', code: 'I10', name: 'Tăng huyết áp', active: true }]);
    fixture.detectChanges();

    const edit = fixture.nativeElement.querySelector('[data-testid="edit-diagnosis-dx-1"]') as HTMLButtonElement;
    expect(edit.getAttribute('aria-label')).toContain('Sửa');
    edit.click();
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as {
      name: { set(value: string): void }; save(): void;
    };
    component.name.set('Tăng huyết áp nguyên phát');
    component.save();
    const update = http.expectOne('/api/v1/admin/diagnoses/dx-1');
    expect(update.request.method).toBe('PUT');
    update.flush({ id: 'dx-1', code: 'I10', name: 'Tăng huyết áp nguyên phát', active: true });

    (fixture.nativeElement.querySelector('[data-testid="toggle-diagnosis-dx-1"]') as HTMLButtonElement).click();
    const deactivate = http.expectOne((request) => request.url === '/api/v1/admin/diagnoses/dx-1/active'
      && request.params.get('value') === 'false');
    expect(deactivate.request.method).toBe('PATCH');
    deactivate.flush({ id: 'dx-1', code: 'I10', name: 'Tăng huyết áp nguyên phát', active: false });
  });
});
