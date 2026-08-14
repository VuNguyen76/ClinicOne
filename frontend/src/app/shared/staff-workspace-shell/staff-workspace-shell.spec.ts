import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
});
