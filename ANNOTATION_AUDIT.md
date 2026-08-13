# ClinicOne — Kiểm tra annotation

Tài liệu này ghi lại kết quả đối chiếu mã nguồn với Sổ Tay Annotation Toàn Diện. Mục tiêu là dùng annotation ở nơi có tác dụng rõ ràng, không thêm thư viện hoặc tự động hóa làm thay đổi hành vi nghiệp vụ.

## Đã áp dụng

- Các trường chứa dữ liệu bí mật trong entity được đánh dấu `@JsonIgnore`: hash mật khẩu của bệnh nhân/nhân viên, hash mã OTP và hash token phiên đăng nhập. Các entity vẫn dùng được cho JPA nhưng không thể vô tình lộ giá trị qua JSON nếu bị serialize trực tiếp.
- Các câu lệnh bulk `UPDATE`/`DELETE` trong `LoginSessionRepository` và `AppointmentHoldRepository` dùng `@Modifying(clearAutomatically = true, flushAutomatically = true)`. Persistence context được flush trước khi chạy và dọn sau khi chạy, tránh đọc lại entity cũ trong cùng request.
- `@Version`, `@Lock`, `@EntityGraph`, `@Scheduled`, `@Valid` và `@PreAuthorize` hiện có trong các luồng tương ứng; không thay đổi vì đang khớp với nghiệp vụ và kiểm thử hiện tại.

## Không áp dụng hàng loạt

- Không thêm Lombok: dự án chưa khai báo dependency và entity đang có constructor/hành vi nghiệp vụ tường minh; chuyển tự động có thể làm hỏng proxy JPA, test và invariant.
- Không thay `@PrePersist`/`@PreUpdate` bằng `@CreationTimestamp`/`@UpdateTimestamp`: thời gian nghiệp vụ đang lấy từ `Clock` UTC để test và xử lý trạng thái nhất quán.
- Không thêm `@Cacheable` hoặc `@Retryable`: chưa có cấu hình cache/retry và cần quyết định rõ phạm vi dữ liệu, chính sách retry, idempotency trước khi bật.
- Không thêm `@AuthenticationPrincipal`: hệ thống dùng session token riêng qua `SessionAuthenticationFilter`, không phải `UserDetails` của Spring Security.
- Không thêm `@DynamicUpdate`/`@Formula` khi chưa có số liệu truy vấn hoặc yêu cầu tính toán động chứng minh lợi ích.
- Không dùng `@FutureOrPresent` cho ngày hẹn ở lớp binding vì ngày hiện tại và múi giờ nghiệp vụ được kiểm tra ở service; giữ một nguồn quy tắc duy nhất.

## Quy tắc duy trì

1. Controller nhận dữ liệu từ body phải dùng `@Valid`; quyền truy cập đặt bằng `@PreAuthorize` ở controller hoặc service.
2. Bulk query phải dùng `@Modifying` với `clearAutomatically` và `flushAutomatically` nếu có thể chạy trong persistence context đang mở.
3. Không trả entity JPA trực tiếp từ API; dùng response DTO/record. Nếu entity bắt buộc được serialize, mọi secret/hash/token phải có `@JsonIgnore`.
4. Mọi annotation mới phải có lý do nghiệp vụ hoặc lỗi đã quan sát, kèm kiểm thử hồi quy; không thêm chỉ để giảm số dòng code.
