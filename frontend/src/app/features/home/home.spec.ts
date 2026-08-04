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

  it('should create the patient homepage', () => {
    expect(component).toBeTruthy();
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Đặt lịch khám');
  });

  it('opens the first FAQ by default and toggles questions', () => {
    expect(component.activeFaq()).toBe(0);

    component.toggleFaq(0);
    expect(component.activeFaq()).toBeNull();

    component.toggleFaq(2);
    expect(component.activeFaq()).toBe(2);
  });
});
