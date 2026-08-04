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
