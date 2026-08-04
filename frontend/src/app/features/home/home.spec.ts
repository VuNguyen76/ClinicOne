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
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('nhẹ nhàng hơn');
  });

  it('filters facilities by the search term', () => {
    component.setSearchTerm('Bình Thạnh');

    expect(component.filteredFacilities().length).toBe(1);
    expect(component.filteredFacilities()[0].name).toContain('Bình Thạnh');
  });
});
