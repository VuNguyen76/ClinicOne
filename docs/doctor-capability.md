# Năng lực vận hành bác sĩ

## Mục tiêu

ClinicOne chỉ mở lịch đặt khám khi bác sĩ đã được gán chuyên khoa, phòng khám và giờ làm hợp lệ. Một khung giờ đặt lịch phải truy ra được bác sĩ và phòng chịu trách nhiệm; hệ thống không dùng tên bác sĩ mặc định.

## Quy tắc cố định

- Chỉ tài khoản có vai trò `DOCTOR` mới được gán hồ sơ bác sĩ.
- Mỗi hồ sơ bác sĩ có một chuyên khoa và một phòng đang hoạt động.
- Chuyên khoa của phòng phải trùng với chuyên khoa của bác sĩ.
- Không được thêm giờ làm nếu bác sĩ chưa được gán phòng và chuyên khoa.
- Các giờ làm của cùng bác sĩ trong cùng một ngày trong tuần không được chồng lấn.
- Thời lượng một lượt khám nằm trong khoảng 15–180 phút.
- Bệnh nhân chỉ được đặt vào khung giờ sinh từ giờ làm đang hoạt động của bác sĩ.
- Lịch hẹn lưu cả mã bác sĩ để không phụ thuộc vào việc đổi tên hiển thị.

## Luồng cấu hình

1. Quản trị viên chọn tài khoản bác sĩ.
2. Chọn chuyên khoa và phòng phù hợp, rồi lưu phân công.
3. Chọn thứ, giờ bắt đầu, giờ kết thúc và thời lượng lượt khám.
4. ClinicOne kiểm tra trùng giờ và lưu giờ làm.
5. Trang đặt lịch lấy các khung giờ còn chỗ, kèm bác sĩ và phòng.

## Phạm vi đợt này

- Hồ sơ phân công bác sĩ.
- Giờ làm lặp theo thứ trong tuần.
- API quản trị để cấu hình và xem lịch.
- Chuẩn bị ràng buộc để nối vào sinh khung giờ và đặt lịch.

## Chưa làm trong đợt này

- Nghỉ phép theo ngày cụ thể.
- Đổi bác sĩ tự động khi bác sĩ nghỉ.
- Phân ca nhiều cơ sở.
- Tính lương hoặc chấm công.
