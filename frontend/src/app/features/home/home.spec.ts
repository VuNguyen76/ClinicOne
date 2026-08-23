import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Home } from './home';

describe('Home', () => {
  let component: Home;
  let fixture: ComponentFixture<Home>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders the patient portal with six booking services', () => {
    expect(component).toBeTruthy();
    expect(component.services).toHaveLength(6);
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Chăm sóc sức khỏe');
  });

  it('sends every supported booking call-to-action through the guarded booking route', () => {
    expect(fixture.nativeElement.querySelector('a[routerlink="/appointments/new"]')).not.toBeNull();
    expect(component.services.slice(0, 3).every((service) => service.route === '/appointments/new')).toBe(true);
  });

  it('toggles the mobile navigation state', () => {
    expect(component.mobileMenuOpen()).toBe(false);

    component.toggleMenu();
    expect(component.mobileMenuOpen()).toBe(true);

    component.closeMenu();
    expect(component.mobileMenuOpen()).toBe(false);
  });

  it('contains four process steps and four support channels', () => {
    expect(component.processSteps).toHaveLength(4);
    expect(component.supportChannels).toHaveLength(4);
  });
});
