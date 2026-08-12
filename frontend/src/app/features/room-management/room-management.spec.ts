import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RoomManagement } from './room-management';

describe('RoomManagement', () => {
  let fixture: ComponentFixture<RoomManagement>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoomManagement],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(RoomManagement);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads rooms ordered by the backend', () => {
    http.expectOne('/api/v1/specialties').flush([{ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' }]);
    http.expectOne('/api/v1/rooms').flush([room('NOI-01', true), room('NHI-01', false)]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="room-row"]').length).toBe(2);
    expect(fixture.nativeElement.querySelector('[data-testid="room-status"]').textContent).toContain('Đang hoạt động');
  });

  it('creates a room from the admin form', () => {
    http.expectOne('/api/v1/specialties').flush([{ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' }]);
    http.expectOne('/api/v1/rooms').flush([]);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="open-room-form"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    fixture.nativeElement.querySelector('[formcontrolname="code"]').value = 'NOI-01';
    fixture.nativeElement.querySelector('[formcontrolname="name"]').value = 'Phòng Nội 01';
    fixture.nativeElement.querySelector('[formcontrolname="specialty"]').value = 'Nội tổng quát';
    fixture.nativeElement.querySelector('[formcontrolname="code"]').dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('[formcontrolname="name"]').dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('[formcontrolname="specialty"]').dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('[data-testid="save-room"]') as HTMLButtonElement).click();

    const request = http.expectOne('/api/v1/rooms');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ code: 'NOI-01', name: 'Phòng Nội 01', specialty: 'Nội tổng quát' });
    request.flush(room('NOI-01', true));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });

  it('uses the preloaded specialty catalog in the room form', () => {
    http.expectOne('/api/v1/specialties').flush([
      { code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' },
      { code: 'NHI', name: 'Nhi', description: 'Khám trẻ em' },
    ]);
    http.expectOne('/api/v1/rooms').flush([]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="open-room-form"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const specialty = fixture.nativeElement.querySelector('[data-testid="room-specialty"]') as HTMLSelectElement;
    expect(Array.from(specialty.options).map((option) => option.textContent?.trim())).toEqual(['Chọn chuyên khoa', 'Nội tổng quát', 'Nhi']);
  });

  it('opens the edit form as a modal for an existing room', () => {
    http.expectOne('/api/v1/specialties').flush([{ code: 'NOI', name: 'Nội tổng quát', description: 'Khám tổng quát' }]);
    http.expectOne('/api/v1/rooms').flush([room('NOI-01', true)]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[aria-label="Sửa phòng Phòng NOI-01"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="room-form-title"]').textContent).toContain('Sửa thông tin phòng');
  });
});

function room(code: string, active: boolean) {
  return { id: `${code}-id`, code, name: `Phòng ${code}`, specialty: 'Nội tổng quát', active };
}
