import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Notifications } from './notifications';

describe('Notifications', () => {
  let fixture: ComponentFixture<Notifications>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Notifications],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Notifications);
    fixture.detectChanges();
  });

  it('shows the notifications section with the account side menu', () => {
    expect(fixture.nativeElement.textContent).toContain('Thông báo');
    expect(fixture.nativeElement.textContent).toContain('Hồ sơ bệnh nhân');
  });
});
