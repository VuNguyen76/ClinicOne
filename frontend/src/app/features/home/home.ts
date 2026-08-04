import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';

type Service = {
  icon: string;
  title: string;
  description: string;
  route: string;
};

type ProcessStep = {
  order: string;
  icon: string;
  title: string;
  description: string;
};

type SupportItem = {
  icon: string;
  title: string;
  value: string;
  href: string;
  type: string;
};

type FooterLink = {
  label: string;
  href: string;
};

type NewsItem = {
  category: string;
  date: string;
  title: string;
  summary: string;
  image: string;
};

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatIconModule],
  templateUrl: './home.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home {
  private readonly destroyRef = inject(DestroyRef);
  readonly mobileMenuOpen = signal(false);
  readonly heroImageIndex = signal(0);
  readonly loggedIn = signal(typeof sessionStorage !== 'undefined' && Boolean(sessionStorage.getItem('clinicOneAccessToken')));

  readonly heroImages = [
    'https://images.unsplash.com/photo-1586773860418-d37222d8fce3?auto=format&fit=crop&w=1400&q=88',
    'https://images.unsplash.com/photo-1538108149393-fbbd81895907?auto=format&fit=crop&w=1400&q=88',
    'https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?auto=format&fit=crop&w=1400&q=88',
    'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=1400&q=88',
  ];

  readonly services: Service[] = [
    { icon: 'person_search', title: 'Đặt khám theo bác sĩ', description: 'Tìm bác sĩ phù hợp với nhu cầu.', route: '/login' },
    { icon: 'medical_services', title: 'Đặt khám theo chuyên khoa', description: 'Chọn chuyên khoa và lịch trống.', route: '/login' },
    { icon: 'calendar_month', title: 'Đặt khám theo ngày', description: 'Chọn ngày thuận tiện để đi khám.', route: '/login' },
    { icon: 'videocam', title: 'Tư vấn khám online', description: 'Trao đổi với bác sĩ từ xa.', route: '/login' },
    { icon: 'home_health', title: 'Xét nghiệm tại nhà', description: 'Lấy mẫu nhanh tại nơi bạn ở.', route: '/login' },
    { icon: 'vaccines', title: 'Tiêm chủng – vắc-xin', description: 'Đăng ký tiêm chủng chủ động.', route: '/login' },
  ];

  readonly commonIssues = [
    'Quản lý thông tin bệnh nhân',
    'Quy trình khám và nhận phiếu',
    'Hoàn tất thanh toán',
  ];

  readonly processSteps: ProcessStep[] = [
    { order: '01', icon: 'touch_app', title: 'Chọn dịch vụ', description: 'Chọn hình thức khám phù hợp với nhu cầu của bạn.' },
    { order: '02', icon: 'schedule', title: 'Chọn bác sĩ & thời gian', description: 'Xem lịch trống và giữ khung giờ thuận tiện.' },
    { order: '03', icon: 'payments', title: 'Thanh toán', description: 'Hoàn tất phí khám theo hướng dẫn trên hệ thống.' },
    { order: '04', icon: 'confirmation_number', title: 'Nhận phiếu khám', description: 'Lưu mã lịch hẹn để check-in và theo dõi lượt khám.' },
  ];

  readonly supportChannels: SupportItem[] = [
    { icon: 'phone_in_talk', title: 'Tổng đài đặt lịch khám', value: '1900000', href: 'tel:1900000', type: 'Gọi ngay' },
    { icon: 'public', title: 'Fanpage Facebook', value: 'ClinicOne Official', href: 'https://facebook.com', type: 'Bấm vào đây' },
    { icon: 'chat', title: 'Hỗ trợ Zalo', value: 'ClinicOne Support', href: 'https://zalo.me', type: 'Bấm vào đây' },
    { icon: 'forum', title: 'Chat trực tuyến', value: 'Hỗ trợ trong giờ làm việc', href: '#support', type: 'Bắt đầu chat' },
  ];

  readonly quickLinks: FooterLink[] = [
    { label: 'Trang chủ', href: '#home' },
    { label: 'Hướng dẫn', href: '#process' },
    { label: 'Phiếu khám', href: '#lookup' },
    { label: 'Thông báo', href: '#support' },
    { label: 'Đăng nhập', href: '/login' },
  ];

  readonly legalLinks: FooterLink[] = [
    { label: 'Liên hệ', href: '#support' },
    { label: 'Điều khoản dịch vụ', href: '#legal' },
    { label: 'Chính sách bảo mật', href: '#legal' },
    { label: 'Quy định sử dụng', href: '#legal' },
  ];

  readonly news: NewsItem[] = [
    { category: 'Thông tin ClinicOne', date: '04.08.2026', title: 'ClinicOne sẵn sàng đồng hành cùng lịch khám của bạn', summary: 'Tin tức mô phỏng — nội dung chính thức sẽ được cập nhật trong phiên bản tiếp theo.', image: 'https://images.unsplash.com/photo-1505751172876-fa1923c5c528?auto=format&fit=crop&w=900&q=82' },
    { category: 'Hướng dẫn', date: '01.08.2026', title: 'Ba việc nên chuẩn bị trước khi đến phòng khám', summary: 'Kiểm tra lịch hẹn, giấy tờ cần thiết và thời gian di chuyển.', image: 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=900&q=82' },
    { category: 'Sức khỏe', date: '28.07.2026', title: 'Chủ động theo dõi lịch tái khám', summary: 'Lưu lại hướng dẫn của bác sĩ để chuẩn bị tốt cho lần khám tiếp theo.', image: 'https://images.unsplash.com/photo-1559757175-0eb30cd8c063?auto=format&fit=crop&w=900&q=82' },
  ];

  readonly publicLinks = [
    { label: 'Trang chủ', route: '/home' },
    { label: 'Giới thiệu', route: '/about' },
    { label: 'Quy trình', route: '/process' },
    { label: 'Hướng dẫn', route: '/common-issues' },
    { label: 'Thắc mắc', route: '/support' },
    { label: 'Liên hệ', route: '/contact' },
  ];

  constructor() {
    interval(10000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.nextHeroImage());
  }

  nextHeroImage(): void {
    this.heroImageIndex.update((index) => (index + 1) % this.heroImages.length);
  }

  selectHeroImage(index: number): void {
    this.heroImageIndex.set(index);
  }

  toggleMenu(): void {
    this.mobileMenuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.mobileMenuOpen.set(false);
  }
}
