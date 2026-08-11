import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AccountMenu } from './account-menu';

describe('AccountMenu', () => {
  let fixture: ComponentFixture<AccountMenu>;

  beforeEach(async () => {
    sessionStorage.clear();
    sessionStorage.setItem('clinicOneAccessToken', 'test-token');
    sessionStorage.setItem('clinicOnePatientName', 'Nguyễn Thanh Vũ');

    await TestBed.configureTestingModule({
      imports: [AccountMenu],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountMenu);
    fixture.detectChanges();
  });

  afterEach(() => sessionStorage.clear());

  it('keeps all account destinations in one consistent menu', () => {
    const trigger = fixture.nativeElement.querySelector('[data-testid="account-menu-trigger"]') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();

    const links = Array.from(fixture.nativeElement.querySelectorAll('[role="menuitem"]')) as HTMLElement[];
    const destinations = links
      .map((link) => link.getAttribute('href'))
      .filter((href): href is string => Boolean(href));

    expect(destinations).toEqual([
      '/dashboard',
      '/appointments',
      '/account',
      '/patient-profiles',
      '/medical-records',
      '/notifications',
      '/change-password',
    ]);
    expect(fixture.nativeElement.textContent).toContain('Hồ sơ người đi khám');
    expect(fixture.nativeElement.querySelector('[role="menu"]')?.getAttribute('aria-label')).toBe('Menu tài khoản');
  });

  it('closes the menu when Escape is pressed', () => {
    const trigger = fixture.nativeElement.querySelector('[data-testid="account-menu-trigger"]') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="menu"]')).not.toBeNull();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="menu"]')).toBeNull();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
  });

  it('shows the medication catalog only to an administrator', async () => {
    sessionStorage.setItem('clinicOneStaffRole', 'ADMIN');
    sessionStorage.setItem('clinicOneStaffRoles', '["ADMIN"]');
    await TestBed.resetTestingModule().configureTestingModule({
      imports: [AccountMenu], providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(AccountMenu);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="account-menu-trigger"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('a[href="/admin/medications"]')?.textContent).toContain('Danh mục thuốc');
  });
});
