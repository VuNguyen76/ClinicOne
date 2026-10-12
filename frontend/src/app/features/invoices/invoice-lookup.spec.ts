import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { InvoiceLookup } from './invoice-lookup';

describe('InvoiceLookup', () => {
  let fixture: ComponentFixture<InvoiceLookup>;
  let component: InvoiceLookup;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InvoiceLookup],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: () => null,
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InvoiceLookup);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  it('renders the electronic invoice lookup portal with tabs and captcha', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Tra cứu Hóa đơn điện tử');
    expect(text).toContain('Mã tra cứu hóa đơn');
    expect(text).toContain('Mã bệnh nhân / Lịch hẹn');
    expect(text).toContain('Mã xác thực bảo mật');
    expect(text).toContain('Lưu ý khi tra cứu hóa đơn');
    expect(text).toContain('1900 0000');
  });

  it('switches between lookup by code and lookup by patient ID', () => {
    component.setLookupType('patientCode');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mã bệnh nhân / Mã lịch hẹn');

    component.setLookupType('lookupCode');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mã tra cứu hóa đơn');
  });

  it('performs lookup and displays the official hospital electronic invoice', () => {
    component.lookupCodeInput.set('220051896851388');
    component.search();
    fixture.detectChanges();

    expect(component.invoiceResult()).toBeTruthy();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Đã tìm thấy hóa đơn điện tử gốc hợp lệ');
    expect(text).toContain('HÓA ĐƠN BÁN HÀNG');
    expect(text).toContain('2K26TVP');
    expect(text).toContain('SIGNATURE VALID');
    expect(text).toContain('KCT');
  });
});
