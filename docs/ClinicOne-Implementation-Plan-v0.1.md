# ClinicOne — Kế hoạch triển khai và kiểm chứng

**Trạng thái:** Đang triển khai; backend và frontend đã có các luồng chính  
**Cơ sở:** SRS v1.7, ERD và ranh giới dữ liệu v0.1, bảng trạng thái v0.1  
**Mục tiêu:** giữ nghiệp vụ, dữ liệu, quyền và giao dịch thống nhất trong suốt quá trình triển khai.

## 1. Hiện trạng

Đã có:

- SRS v1.7 làm nguồn nghiệp vụ.
- Năm actor: bệnh nhân, nhân viên tiếp nhận, bác sĩ, điều phối viên, quản trị viên.
- Bảng trạng thái riêng cho lịch hẹn, hàng đợi và lượt khám.
- ERD v0.1 gồm 32 bảng và ranh giới sở hữu dữ liệu theo module.
- Baseline kiến trúc Modular Monolith.
- Bộ Use Case, giao dịch và API v0.1 đã được rà theo SRS.
- Spring Boot backend, Flyway migration V1–V8 và Angular frontend trong cùng repository.
- Các luồng đặt lịch, hủy/đổi lịch, QR check-in, hàng đợi, khám bệnh, tiếp nhận ngoại lệ,
  điều phối lịch, đối soát, thông báo SMS và quản trị danh mục.
- Kiểm thử backend bằng `mvn test -q`; kiểm thử frontend và build Angular trong CI cục bộ.

Chưa hoàn tất:

- Chưa có bộ kiểm thử E2E ổn định chạy cùng PostgreSQL và TextBee thật.
- Chưa có dữ liệu demo an toàn được chuẩn hóa cho toàn bộ luồng bác sĩ, điều phối và tiếp nhận.
- Cần tiếp tục rà contract API, quyền theo actor và các trường hợp mất kết nối/retry trên môi trường tích hợp.
- Cần khắc phục pipeline GitHub Actions đang dừng trước khi chạy step, không tạo log.

## 2. Nguyên tắc thống nhất

1. SRS v1.7 là nguồn ưu tiên cao nhất cho nghiệp vụ.
2. Không tự tạo actor, trạng thái hoặc luồng không có khả năng xảy ra tại phòng khám.
3. Người dùng chọn hành động thực tế; hệ thống tự chuyển trạng thái sau khi hành động ghi nhận thành công.
4. Mỗi giao dịch phải nói rõ dữ liệu nào được đổi cùng nhau và điều gì xảy ra khi lỗi.
5. Phiếu khám đã ký là chỉ đọc trong v1.7.
6. QR chỉ xác định phòng; mã lịch hẹn dùng để tra cứu, không nhúng thông tin cá nhân vào QR.
7. SMS TextBee và thông báo trong ứng dụng chạy sau commit thông qua Outbox; lỗi gửi không làm mất nghiệp vụ đã ghi nhận.
8. Tên trạng thái hiển thị bằng tiếng Việt; tên lưu trữ trong code dùng enum tiếng Anh ổn định và có bảng ánh xạ.

## 3. Baseline thiết kế đang áp dụng

### 3.1. Các phương án đã được duyệt

| Mã | Nội dung đã chốt | Phương án đã chọn | Người duyệt (để truy vết) |
|---|---|---|---|
| D-01 | Trạng thái khung giờ | `AVAILABLE`, `HELD`, `OCCUPIED`, `BLOCKED`, `UNBOOKABLE`; không hiển thị trạng thái kỹ thuật không hợp lệ | Chủ sản phẩm + điều phối |
| D-02 | Outbox | `PENDING`, `PROCESSING`, `SENT`, `RETRY_WAITING`, `FAILED`; không phải trạng thái lịch | Dev lead + vận hành |
| D-03 | Đối soát | Chỉ đóng khi người có quyền chủ động yêu cầu, có mã nhật ký/sự cố, người đóng và ghi chú | Quản trị + vận hành |
| D-04 | Quay lại sau khi hàng đợi đóng | Không mở lại lượt cũ; nếu hàng đợi còn hoạt động thì dùng số cũ, nếu đã rời trước khám thì hỗ trợ đặt lịch mới | Chủ sản phẩm + tiếp nhận |
| D-05 | Loại lượt không cần phiếu | Bác sĩ vẫn bấm **Kết thúc khám**, không tự động kết thúc, không kiểm tra bốn trường phiếu | Chủ sản phẩm + bác sĩ |
| D-06 | Danh mục thuốc | Vô hiệu hóa mềm; phiếu chụp tên thuốc/liều/số lượng/cách dùng tại thời điểm ký | Bác sĩ + điều phối |
| D-07 | Quyền dữ liệu | Ghi qua application service; bác sĩ chỉ sửa lượt phụ trách; bệnh nhân chỉ xem phiếu đã ký của hồ sơ thuộc tài khoản | Chủ sản phẩm + bảo mật |

**Kết quả chọn:** người yêu cầu đã chọn phương án A cho D-01 đến D-07 ngày 03/08/2026. Bảng trên được xem là baseline đã duyệt; cột “người cần duyệt” chỉ còn giá trị truy vết, không phải việc chờ xử lý.

### 3.2. Các điểm đã thống nhất từ SRS

| Nội dung | Quy tắc hiện tại |
|---|---|
| Đặt lịch | Bệnh nhân đặt trên ứng dụng; tiếp nhận chỉ hỗ trợ ngoại lệ hoặc đặt giúp theo số điện thoại. |
| QR check-in | QR cố định trước phòng; bệnh nhân quét bằng điện thoại để nhận số thứ tự. Nhân viên xử lý khi QR hoặc dữ liệu không hợp lệ. |
| Đến muộn | Sau 15 phút chỉ cảnh báo, lịch vẫn `Đã đặt`; không tạo trạng thái `Đến muộn`. |
| Vắng mặt | Sau 24 giờ kể từ thời điểm kết thúc dự kiến, nếu chưa hủy/chưa check-in/chưa xử lý hợp lệ thì chuyển `Vắng mặt`. |
| Vắng mặt nhưng vẫn đến | Giữ lịch cũ là `Vắng mặt`; tiếp nhận hỗ trợ khung còn trống trong ngày hoặc ngày khác, không khôi phục lịch cũ. |
| Quét QR lại | Trả đúng số thứ tự và lượt khám cũ, không tạo bản ghi trùng. |
| Người đã lấy số rời đi | Nếu hàng đợi còn hoạt động thì giữ số; nếu đã `Rời trước khám` thì không mở lại lượt cũ. |
| Ký phiếu | Chỉ bác sĩ phụ trách được ký; ký xong khóa phiếu, đóng lượt và lịch trong một giao dịch. |
| Thông báo | In-app và SMS TextBee tới số điện thoại đã xác thực; không gửi chẩn đoán, đơn thuốc, OTP cũ, mật khẩu hoặc toàn văn phiếu. |

## 4. Thứ tự công việc sau khi duyệt baseline

### Giai đoạn A — Khóa nghiệp vụ

**Đầu ra:** SRS v1.7, bảng trạng thái, ERD, quyết định D-01…D-07 đều cùng một cách hiểu.

- Đã duyệt bảy phương án A.
- Cập nhật glossary và bảng trạng thái nếu có ý kiến khác.
- Chốt bảng ánh xạ trạng thái tiếng Việt ↔ enum lưu trữ.
- Chốt ma trận quyền năm actor.
- Chốt các trường bắt buộc/tùy chọn của từng Use Case.

**Điều kiện qua giai đoạn:** Đạt — không còn điểm chưa chốt ảnh hưởng tới trạng thái, khóa ngoại, quyền hoặc ranh giới giao dịch trong vertical slice.

### Giai đoạn B — Khóa thiết kế kỹ thuật

**Đầu ra:** schema plan, API contract và ADR được duyệt.

- Chốt tên bảng/cột và kiểu dữ liệu PostgreSQL.
- Chốt khóa chống trùng cho từng lệnh ghi.
- Chốt transaction boundary cho đặt lịch, hủy, check-in, bắt đầu khám, ký phiếu.
- Chốt lỗi API và mã HTTP.
- Chốt chiến lược xác thực: session httpOnly hoặc token trong cookie bảo mật; không dùng localStorage.
- Chốt Outbox worker, retry/backoff và thời hạn lưu.

**Điều kiện qua giai đoạn:** Có thể viết migration và test contract mà không phải suy diễn nghiệp vụ.

### Giai đoạn C — Database và seed

**Đầu ra:** Flyway migration và dữ liệu tham chiếu tối thiểu.

- Tạo schema theo ERD.
- Tạo foreign key, unique index, check constraint và index truy vấn.
- Seed năm role, loại lượt có phiếu/không phiếu, cấu hình đơn vị và một phòng mẫu cho môi trường dev.
- Không seed tài khoản thật, mật khẩu thật, OTP hoặc dữ liệu bệnh nhân thật.

**Điều kiện qua giai đoạn:** Migration chạy mới từ đầu và chạy lại an toàn trong môi trường test; rollback/đối soát được kiểm thử.

### Giai đoạn D — Vertical slice

Thứ tự bắt buộc:

```text
Đặt lịch
  → Check-in QR
  → Cấp số và tạo lượt
  → Gọi bệnh nhân
  → Bắt đầu khám
  → Lưu nháp
  → Ký phiếu
```

Các lát cắt đã có code; mỗi lát cắt phải tiếp tục được giữ đủ:

- Use Case/application service.
- Repository port và adapter PostgreSQL.
- API controller + validation + error mapping.
- Business log và khóa chống trùng.
- Unit test, integration test với database test và test lỗi.

**Hiện trạng:** đặt lịch, hủy/đổi lịch, QR check-in, hàng đợi, khám bệnh, tiếp nhận,
điều phối lịch và đối soát đã có controller/service/repository tương ứng. Phần còn thiếu
chủ yếu là kiểm thử E2E và kiểm chứng trên dịch vụ ngoài thật.

### Giai đoạn E — Hoàn thiện và kiểm chứng

Các luồng nghiệp vụ chính ở trên đã có code tương ứng. Giai đoạn này không tạo thêm luồng
mới; chỉ hoàn thiện và kiểm chứng những phần còn thiếu:

1. Chạy kiểm thử tích hợp với PostgreSQL và kiểm tra dữ liệu sau mỗi giao dịch.
2. Chạy E2E từ đặt lịch, QR check-in, gọi số, khám, ký phiếu và xem lịch sử.
3. Kiểm tra các nhánh đến muộn, vắng mặt, rời hàng đợi, đổi lịch và gửi lại cùng khóa chống trùng.
4. Kiểm tra OTP SMS TextBee, Outbox, retry và tình huống dịch vụ ngoài không phản hồi.
5. Hoàn thiện dữ liệu demo an toàn cho bác sĩ, điều phối, tiếp nhận và bệnh nhân.
6. Rà lần cuối phân quyền, nhật ký kỹ thuật, báo cáo và tiêu chí nghiệm thu.

## 5. Quy tắc hoàn thành từng Use Case

Một Use Case chỉ được xem là hoàn thành khi:

- Có precondition, input, actor và permission rõ ràng.
- Có transaction boundary và rollback khi từng bước lỗi.
- Có idempotency key hoặc quy tắc chống gửi lặp phù hợp.
- Có trạng thái trước/sau đúng bảng trạng thái.
- Có business log append-only.
- Có API error có mã ổn định, không lộ chi tiết nội bộ.
- Có test đường bình thường, gửi lặp, tranh chấp, sai quyền và dữ liệu không hợp lệ.
- Không sửa phiếu đã ký hoặc tạo lịch/hàng đợi/lượt trùng.

## 6. Tiêu chí hiện trạng và việc cần hoàn tất

Các điều kiện nền tảng đã đạt; các mục còn lại là điều kiện để chuyển sang nghiệm thu tích hợp:

- [x] D-01 đến D-07 được duyệt.
- [x] ERD không còn quan hệ chưa rõ chủ sở hữu trong phạm vi v1.7.
- [x] Bảng trạng thái không còn chuyển đổi chưa quyết định trong vertical slice.
- [x] Use Case/API v0.1 được rà với năm actor.
- [x] Ma trận quyền được ghi trong quyết định D-07.
- [x] Transaction và retry policy được ghi trong ADR-0002.
- [x] Có dữ liệu khởi tạo tối thiểu cho môi trường local/test.
- [ ] Có bộ dữ liệu demo được duyệt cho toàn bộ luồng nghiệp vụ.
- [ ] Có người phụ trách xác nhận tiêu chí hoàn thành.
- [ ] Có E2E chạy ổn định với backend và frontend cùng lúc.
- [ ] Có kiểm tra tích hợp TextBee/OTP và xử lý retry ngoài môi trường mock.

## 7. Thứ tự tài liệu cần rà và nghiệm thu

1. [ClinicOne-State-Transition-Matrix.md](ClinicOne-State-Transition-Matrix.md)
2. [ClinicOne-ERD-and-Data-Boundaries.md](ClinicOne-ERD-and-Data-Boundaries.md)
3. [ClinicOne-Design-Decisions-v0.1.md](ClinicOne-Design-Decisions-v0.1.md)
4. [ClinicOne-Use-Cases-and-API-v0.1.md](ClinicOne-Use-Cases-and-API-v0.1.md)
5. Tài liệu này

Sau khi hoàn tất dữ liệu demo, E2E và kiểm thử tích hợp, bước tiếp theo là nghiệm thu từng Use Case
theo SRS v1.7. Không mở rộng actor hoặc trạng thái ngoài phạm vi đã thống nhất.
