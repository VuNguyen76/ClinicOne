import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AccountNav } from './account-nav';

describe('AccountNav', () => {
  let fixture: ComponentFixture<AccountNav>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountNav],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountNav);
    fixture.detectChanges();
  });

  it('renders one shared menu in the same order on every account page', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[];

    expect(links.map((link) => link.querySelector('span')?.textContent?.trim())).toEqual([
      'Lịch hẹn của tôi',
      'Hồ sơ bệnh nhân',
      'Đổi mật khẩu',
      'Phiếu khám bệnh',
      'Thông báo',
    ]);
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/appointments',
      '/account',
      '/change-password',
      '/medical-records',
      '/notifications',
    ]);
  });
});
