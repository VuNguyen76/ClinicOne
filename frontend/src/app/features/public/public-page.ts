import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { PatientHeader } from '../../shared/patient-header/patient-header';

export type PublicPageKey = 'about' | 'process' | 'common-issues' | 'support' | 'contact';

@Component({
  selector: 'app-public-page',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatIconModule, PatientHeader],
  templateUrl: './public-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicPage {
  private readonly route = inject(ActivatedRoute);
  private readonly routeData = toSignal(this.route.data);

  readonly page = computed<PublicPageKey>(() =>
    (this.routeData()?.['page'] as PublicPageKey) || (this.route.snapshot.data['page'] as PublicPageKey) || 'about'
  );
  readonly mobileMenuOpen = signal(false);
  readonly contactNotice = signal('');
  readonly contactBusy = signal(false);

  readonly publicLinks = [
    { label: 'Trang chủ', route: '/home' },
    { label: 'Giới thiệu', route: '/about' },
    { label: 'Quy trình', route: '/process' },
    { label: 'Hướng dẫn', route: '/common-issues' },
    { label: 'Thắc mắc', route: '/support' },
    { label: 'Liên hệ', route: '/contact' },
  ];

  readonly pageContent: Record<PublicPageKey, { eyebrow: string; title: string; description: string; image: string }> = {
    about: {
      eyebrow: 'Giới thiệu ClinicOne',
      title: 'Một hành trình khám bệnh rõ ràng hơn.',
      description: 'ClinicOne kết nối lịch hẹn, tiếp nhận và theo dõi lượt khám trong một hành trình dễ hiểu cho người bệnh và nhân viên y tế.',
      image: 'https://images.unsplash.com/photo-1538108149393-fbbd81895907?auto=format&fit=crop&w=1200&q=84',
    },
    process: {
      eyebrow: 'Quy trình khám',
      title: 'Biết mình cần làm gì ở mỗi bước.',
      description: 'Từ lúc chọn dịch vụ đến khi nhận kết quả, mọi mốc quan trọng đều được hiển thị rõ ràng.',
      image: 'https://images.unsplash.com/photo-1559757175-0eb30cd8c063?auto=format&fit=crop&w=1200&q=84',
    },
    'common-issues': {
      eyebrow: 'Hướng dẫn',
      title: 'Giải đáp những việc thường gặp.',
      description: 'Các hướng dẫn ngắn giúp bạn chuẩn bị tốt hơn trước, trong và sau buổi khám.',
      image: 'https://images.unsplash.com/photo-1576091160550-2173dba999ef?auto=format&fit=crop&w=1200&q=84',
    },
    support: {
      eyebrow: 'Thắc mắc',
      title: 'Cần hỗ trợ? ClinicOne luôn sẵn sàng.',
      description: 'Chọn kênh phù hợp để được giải đáp về lịch hẹn, hồ sơ và quy trình khám.',
      image: 'https://images.unsplash.com/photo-1551076805-e1869033e561?auto=format&fit=crop&w=1200&q=84',
    },
    contact: {
      eyebrow: 'Liên hệ',
      title: 'Kết nối với ClinicOne.',
      description: 'Gửi câu hỏi hoặc gọi cho chúng tôi trong giờ làm việc để được hỗ trợ nhanh nhất.',
      image: 'https://images.unsplash.com/photo-1521737711867-e3b97375f902?auto=format&fit=crop&w=1200&q=84',
    },
  };

  readonly processSteps = [
    { order: '01', icon: 'event_available', title: 'Đặt lịch', description: 'Chọn chuyên khoa, bác sĩ và khung giờ phù hợp trên ứng dụng.' },
    { order: '02', icon: 'how_to_reg', title: 'Đến phòng khám', description: 'Mang theo thông tin lịch hẹn và đến đúng cơ sở, đúng thời gian.' },
    { order: '03', icon: 'qr_code_scanner', title: 'Nhận số thứ tự', description: 'Quét mã QR trước phòng bác sĩ để check-in và nhận số thứ tự.' },
    { order: '04', icon: 'task_alt', title: 'Theo dõi kết quả', description: 'Xem trạng thái lượt khám, phiếu khám đã ký và lịch tái khám.' },
  ];

  readonly guides = [
    { icon: 'calendar_month', title: 'Đặt lịch khám', text: 'Chọn dịch vụ, chuyên khoa, bác sĩ và khung giờ còn trống. Mã lịch hẹn được tạo ngay sau khi đặt thành công.' },
    { icon: 'qr_code_2', title: 'Check-in tại phòng khám', text: 'Đến đúng phòng bác sĩ, dùng điện thoại quét mã QR được đặt trước cửa để nhận số thứ tự.' },
    { icon: 'edit_note', title: 'Cập nhật thông tin', text: 'Nếu cần đổi lịch hoặc cập nhật hồ sơ, mở lịch hẹn trong tài khoản và làm theo hướng dẫn.' },
  ];

  readonly supportChannels = [
    { icon: 'phone_in_talk', title: 'Tổng đài', value: '1900000', detail: 'Hỗ trợ đặt lịch và hướng dẫn nhanh', href: 'tel:1900000' },
    { icon: 'mail_outline', title: 'Email', value: 'support@clinicone.vn', detail: 'Phản hồi trong giờ làm việc', href: 'mailto:support@clinicone.vn' },
    { icon: 'chat_bubble_outline', title: 'Tin nhắn', value: 'ClinicOne Support', detail: 'Trao đổi về lịch hẹn và hồ sơ', href: '#contact-form' },
  ];

  readonly faqs = [
    { question: 'Tôi có cần đặt lịch trước không?', answer: 'Nên đặt lịch trên ứng dụng trước khi đến để giữ khung giờ phù hợp. Tiếp nhận tại quầy chỉ dùng cho trường hợp ngoại lệ.' },
    { question: 'Tôi nhận số thứ tự ở đâu?', answer: 'Sau khi đến phòng bác sĩ, quét mã QR được đặt trước cửa phòng bằng điện thoại để check-in.' },
    { question: 'Tôi có thể đổi lịch đã đặt không?', answer: 'Bạn có thể yêu cầu đổi sang khung giờ khác còn trống theo hướng dẫn trong mục lịch hẹn.' },
  ];

  readonly content = computed(() => this.pageContent[this.page()]);

  toggleMenu(): void {
    this.mobileMenuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.mobileMenuOpen.set(false);
  }

  submitContact(event: Event, nameInput: HTMLInputElement, phoneInput: HTMLInputElement, textInput: HTMLTextAreaElement): void {
    event.preventDefault();
    if (!nameInput.value.trim() || !phoneInput.value.trim()) {
      return;
    }
    this.contactBusy.set(true);
    setTimeout(() => {
      this.contactBusy.set(false);
      this.contactNotice.set('Cảm ơn bạn! Yêu cầu hỗ trợ đã được gửi thành công. Đội ngũ ClinicOne sẽ liên hệ sớm nhất.');
      nameInput.value = '';
      phoneInput.value = '';
      textInput.value = '';
      setTimeout(() => this.contactNotice.set(''), 5000);
    }, 600);
  }
}
