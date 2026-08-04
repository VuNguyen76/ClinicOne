import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

type Service = {
  icon: string;
  title: string;
  description: string;
  tone: string;
};

type Facility = {
  name: string;
  location: string;
  specialty: string;
  rating: string;
  image: string;
};

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, MatIconModule],
  templateUrl: './home.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home {
  readonly services: Service[] = [
    { icon: 'calendar_month', title: 'Đặt lịch khám', description: 'Chọn cơ sở, chuyên khoa và giờ phù hợp.', tone: 'bg-[#e5f4f2] text-[#0e6872]' },
    { icon: 'confirmation_number', title: 'Lấy số trực tuyến', description: 'Đến đúng lúc, giảm thời gian chờ tại quầy.', tone: 'bg-[#fff1df] text-[#b46723]' },
    { icon: 'folder_shared', title: 'Hồ sơ khám', description: 'Xem lại lịch sử và kết quả đã được ký.', tone: 'bg-[#e9effa] text-[#496caa]' },
    { icon: 'support_agent', title: 'Cần hỗ trợ?', description: 'Đội ngũ ClinicOne luôn sẵn sàng lắng nghe.', tone: 'bg-[#f2eafa] text-[#7955a8]' },
  ];

  readonly facilities: Facility[] = [
    {
      name: 'Phòng khám ClinicOne Trung tâm',
      location: 'Quận 1, TP. Hồ Chí Minh',
      specialty: 'Nội tổng quát · Tai mũi họng · Da liễu',
      rating: '4.9',
      image: 'https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?auto=format&fit=crop&w=900&q=85',
    },
    {
      name: 'Phòng khám ClinicOne Bình Thạnh',
      location: 'Quận Bình Thạnh, TP. Hồ Chí Minh',
      specialty: 'Nhi khoa · Sản phụ khoa · Tim mạch',
      rating: '4.8',
      image: 'https://images.unsplash.com/photo-1587351021759-3e566b6af7cc?auto=format&fit=crop&w=900&q=85',
    },
    {
      name: 'Phòng khám ClinicOne Cầu Giấy',
      location: 'Cầu Giấy, Hà Nội',
      specialty: 'Nội tổng quát · Cơ xương khớp · Mắt',
      rating: '4.8',
      image: 'https://images.unsplash.com/photo-1516841273335-e39b37888115?auto=format&fit=crop&w=900&q=85',
    },
  ];

  readonly searchTerm = signal('');
  readonly filteredFacilities = computed(() => {
    const term = this.searchTerm().trim().toLocaleLowerCase();
    if (!term) return this.facilities;
    return this.facilities.filter((facility) =>
      `${facility.name} ${facility.location} ${facility.specialty}`.toLocaleLowerCase().includes(term),
    );
  });

  setSearchTerm(value: string): void {
    this.searchTerm.set(value);
  }
}
