import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { StaffWorkspaceShell } from './staff-workspace-shell';

@Component({
  standalone: true,
  imports: [StaffWorkspaceShell],
  template: `
    <app-staff-workspace-shell moduleTitle="Khám bệnh" pageTitle="Hàng đợi khám bệnh">
      <button workspace-actions type="button">Làm mới</button>
      <nav workspace-tabs data-testid="workspace-tabs"><button type="button">Danh sách</button><button type="button">Thiết lập</button></nav>
      <p data-testid="projected-content">Nội dung nghiệp vụ</p>
    </app-staff-workspace-shell>
  `,
})
class HostComponent {}

describe('StaffWorkspaceShell', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    sessionStorage.setItem('clinicOneStaffRole', 'DOCTOR');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['DOCTOR']));
    sessionStorage.setItem('clinicOnePatientName', 'BS. Nguyễn An');
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  afterEach(() => sessionStorage.clear());

  it('keeps staff inside role-specific ERP navigation without a customer home link', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="staff-module-nav"] a')) as HTMLAnchorElement[];
    expect(links.map((link) => link.querySelector('span')?.textContent?.trim())).toEqual(['Hàng đợi khám bệnh']);
    expect(links.some((link) => link.getAttribute('href') === '/home')).toBe(false);
  });

  it('renders a compact window toolbar and projected work area', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="staff-window-title"]')?.textContent)
      .toContain('Hàng đợi khám bệnh');
    expect(fixture.nativeElement.querySelector('[data-testid="staff-window-actions"]')?.textContent)
      .toContain('Làm mới');
    expect(fixture.nativeElement.querySelector('[data-testid="projected-content"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="workspace-tabs"]')?.textContent).toContain('Thiết lập');
  });

  it('clears pointer focus from a clicked module link without affecting keyboard navigation', () => {
    const link = fixture.nativeElement.querySelector('[data-testid="staff-module-nav"] a') as HTMLAnchorElement;
    link.focus();

    const shell = fixture.debugElement.query(By.directive(StaffWorkspaceShell)).componentInstance as unknown as {
      handleNavigationClick(event: MouseEvent): void;
    };
    shell
      .handleNavigationClick({ currentTarget: link, detail: 1 } as unknown as MouseEvent);

    expect(document.activeElement).not.toBe(link);
  });

  it('filters the module menu from the functional search input', () => {
    const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = 'hàng đợi';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('[data-testid="staff-module-nav"] a span') as NodeListOf<HTMLSpanElement>;
    const labels = Array.from(items).map((element) => element.textContent?.trim());
    expect(labels).toEqual(['Hàng đợi khám bệnh']);
  });

  it('shows each reception workflow as a separate menu destination', () => {
    fixture.destroy();
    sessionStorage.setItem('clinicOneStaffRole', 'RECEPTIONIST');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['RECEPTIONIST']));
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    const links = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="staff-module-nav"] a')) as HTMLAnchorElement[];
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      '/reception',
      '/reception/appointments',
      '/reception/walk-in',
      '/reception/queue',
      '/reception/exceptions',
      '/reception/profiles',
    ]);
  });

  it('includes reason catalog in catalog navigation for administrator', () => {
    fixture.destroy();
    sessionStorage.setItem('clinicOneStaffRole', 'ADMIN');
    sessionStorage.setItem('clinicOneStaffRoles', JSON.stringify(['ADMIN']));
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    const links = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="staff-module-nav"] a')) as HTMLAnchorElement[];
    const hrefs = links.map((link) => link.getAttribute('href'));
    expect(hrefs).toContain('/admin/reason-catalog');
  });
});
