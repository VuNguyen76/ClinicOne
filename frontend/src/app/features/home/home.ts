import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

type BookingService = {
  icon: string;
  title: string;
  description: string;
};

type Facility = {
  name: string;
  location: string;
  specialty: string;
  rating: string;
};

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, MatIconModule],
  templateUrl: './home.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home {
  readonly bookingServices: BookingService[] = [
    { icon: 'medical_services', title: 'Theo chuyên khoa', description: 'Chọn chuyên khoa và khung giờ phù hợp.' },
    { icon: 'person_search', title: 'Theo bác sĩ', description: 'Tìm bác sĩ bạn muốn đăng ký khám.' },
    { icon: 'confirmation_number', title: 'Lấy số hẹn trước', description: 'Đến đúng lúc, giảm thời gian chờ.' },
    { icon: 'history', title: 'Tái khám', description: 'Tiếp tục từ lịch sử khám của bạn.' },
  ];

  readonly facilities: Facility[] = [
    { name: 'ClinicOne Trung tâm', location: 'Quận 1, TP. Hồ Chí Minh', specialty: 'Nội tổng quát · Tai mũi họng · Da liễu', rating: '4.9' },
    { name: 'ClinicOne Bình Thạnh', location: 'Quận Bình Thạnh, TP. Hồ Chí Minh', specialty: 'Nhi khoa · Sản phụ khoa · Tim mạch', rating: '4.8' },
    { name: 'ClinicOne Cầu Giấy', location: 'Cầu Giấy, Hà Nội', specialty: 'Nội tổng quát · Cơ xương khớp · Mắt', rating: '4.8' },
  ];

  readonly faqs = [
    'Tôi cần chuẩn bị gì trước khi đến khám?',
    'Làm sao để đổi hoặc hủy lịch hẹn?',
    'Tôi có thể đặt lịch cho người thân không?',
    'Nếu đến muộn thì lịch hẹn được xử lý thế nào?',
  ];

  readonly activeFaq = signal<number | null>(0);

  toggleFaq(index: number): void {
    this.activeFaq.update((active) => (active === index ? null : index));
  }
}
