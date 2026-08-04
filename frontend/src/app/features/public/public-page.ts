import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

export type PublicPageKey = 'about' | 'process' | 'common-issues' | 'support' | 'contact';

@Component({
  selector: 'app-public-page',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatIconModule],
  templateUrl: './public-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicPage {
  private readonly route = inject(ActivatedRoute);

  readonly page = this.route.snapshot.data['page'] as PublicPageKey;

  readonly pageContent: Record<PublicPageKey, { eyebrow: string; title: string; description: string }> = {
    about: {
      eyebrow: 'Giới thiệu ClinicOne',
      title: 'Một hành trình khám bệnh rõ ràng hơn.',
      description: 'ClinicOne kết nối lịch hẹn, tiếp nhận và theo dõi lượt khám trong một hành trình dễ hiểu cho người bệnh và nhân viên y tế.',
    },
    process: {
      eyebrow: 'Quy trình khám',
      title: 'Biết mình cần làm gì ở mỗi bước.',
      description: 'Từ lúc chọn dịch vụ đến khi nhận kết quả, mọi mốc quan trọng đều được hiển thị rõ ràng.',
    },
    'common-issues': {
      eyebrow: 'Hướng dẫn',
      title: 'Giải đáp những việc thường gặp.',
      description: 'Các hướng dẫn ngắn giúp bạn chuẩn bị tốt hơn trước, trong và sau buổi khám.',
    },
    support: {
      eyebrow: 'Thắc mắc',
      title: 'Cần hỗ trợ? ClinicOne luôn sẵn sàng.',
      description: 'Chọn kênh phù hợp để được giải đáp về lịch hẹn, hồ sơ và quy trình khám.',
    },
    contact: {
      eyebrow: 'Liên hệ',
      title: 'Kết nối với ClinicOne.',
      description: 'Gửi câu hỏi hoặc gọi cho chúng tôi trong giờ làm việc để được hỗ trợ nhanh nhất.',
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

  get content() {
    return this.pageContent[this.page];
  }
}
