# ClinicOne — Quyết định UI và cách làm việc theo branch

## 1. Mục tiêu giao diện

ClinicOne dùng giao diện lấy cảm hứng từ OpenMRS 3 (O3), nhưng không sao chép logo, mã nguồn hoặc tài sản hình ảnh của OpenMRS.

Mục tiêu tương đồng:

- Khoảng 80% về cảm nhận tổng thể.
- Khoảng 85–90% ở app shell: thanh bên, thanh đầu trang, điều hướng, bảng dữ liệu và vùng làm việc.
- Khoảng 75–85% ở màu sắc, khoảng cách, thẻ thông tin, biểu mẫu và trạng thái.
- Phần còn lại dành cho thương hiệu ClinicOne và các luồng riêng: đặt lịch, QR check-in, số thứ tự, hàng đợi và phiếu khám.

## 2. Stack frontend đã chọn

| Thành phần | Cách dùng |
|---|---|
| Angular standalone | Tổ chức màn hình và module theo tính năng nghiệp vụ. |
| Angular Material | Thành phần có hành vi sẵn và hỗ trợ tiếp cận: menu, form field, table, dialog, snackbar, chip, datepicker, sidenav. |
| Tailwind CSS | Bố cục, khoảng cách, responsive, lưới, flex và các utility nhỏ. |
| SCSS | Style riêng của component và các quy tắc không phù hợp để viết bằng utility. |
| TypeScript strict | Kiểm tra kiểu dữ liệu ở ranh giới API và form. |

Nguyên tắc phối hợp:

1. Material chịu trách nhiệm về hành vi và khả năng tiếp cận của component.
2. Tailwind chịu trách nhiệm về layout và khoảng cách, không ghi đè tùy tiện cấu trúc nội bộ của Material.
3. Màu thương hiệu, typography, spacing và trạng thái phải đi qua design token dùng chung.
4. Không trộn quá nhiều class utility vào một component; phần lặp lại phải tách thành component dùng lại.
5. Không ghi dữ liệu bệnh nhân, email, số điện thoại, token hoặc thông tin y tế vào log phía trình duyệt.

## 3. Cấu trúc giao diện dự kiến

```text
frontend/src/app/
├── core/              # phiên đăng nhập, interceptor, API client, guard
├── layout/            # app shell, sidenav, toolbar, breadcrumb
├── shared/            # button, table, status chip, empty/loading/error state
├── features/
│   ├── patient/       # hồ sơ và lịch sử bệnh nhân
│   ├── appointment/   # đặt, hủy, đổi lịch
│   ├── reception/     # tiếp nhận tại quầy
│   ├── queue/         # QR check-in, số thứ tự, gọi bệnh nhân
│   ├── examination/   # lượt khám và phiếu khám
│   └── administration/# cấu hình và báo cáo
└── app.routes.ts
```

Mỗi feature phải có màn hình, model API, service và test riêng. Không gọi trực tiếp endpoint từ template.

## 4. Các màn hình UI ưu tiên

1. App shell dùng chung cho năm actor.
2. Đăng nhập và xác thực OTP.
3. Bệnh nhân: chọn hồ sơ, tìm chuyên khoa/bác sĩ, chọn khung giờ và xem lịch hẹn.
4. Tiếp nhận: tra cứu bằng số điện thoại, hỗ trợ hồ sơ và xem danh sách trong ngày.
5. Hàng đợi: quét QR phòng, nhận số, gọi lại và xử lý người quay lại.
6. Bác sĩ: danh sách lượt, bắt đầu khám, lưu nháp và ký phiếu.
7. Điều phối viên/quản trị viên: lịch làm việc, cấu hình và đối soát.

## 5. Quy tắc làm việc theo branch

Không làm việc trực tiếp trên `main`. Mỗi task phải có branch riêng.

```powershell
git switch main
git pull --ff-only
git switch -c feat/<ten-task-ngan-gon>
```

Quy ước tên branch:

- `feat/<ten-task-ngan-gon>` cho tính năng mới.
- `fix/<ten-task-ngan-gon>` cho sửa lỗi.
- `refactor/<ten-task-ngan-gon>` cho thay đổi cấu trúc nhưng không đổi nghiệp vụ.
- `chore/<ten-task-ngan-gon>` cho công việc kỹ thuật hoặc cấu hình.

Ví dụ:

```text
feat/be-identity-account
feat/be-appointment-booking
feat/fe-app-shell
feat/fe-appointment-screen
fix/fe-login-validation
```

Quy trình hoàn thành task:

1. Đọc task và requirement liên quan.
2. Tạo branch từ `main` mới nhất.
3. Code và test trong branch đó.
4. Backend chạy `mvn test`; frontend chạy `npm run build` và test liên quan.
5. Commit nhỏ, mô tả đúng một mục tiêu.
6. Push branch riêng nếu cần review:

```powershell
git push -u origin feat/<ten-task-ngan-gon>
```

7. Chỉ merge vào `main` sau khi task đạt tiêu chí hoàn thành và đã kiểm tra phần BE/UI liên quan.

## 6. Làm BE và UI song song

- BE và UI không sửa chung một branch nếu hai task độc lập.
- BE phải chốt API contract và dữ liệu mẫu trước khi UI kết nối thật.
- UI có thể dùng mock data trong lúc BE chưa xong, nhưng mock phải được đánh dấu rõ và không đưa dữ liệu bệnh nhân thật vào.
- Khi API thay đổi, cập nhật contract và test trước; không sửa âm thầm ở phía UI.
- Một task tích hợp chỉ được tạo sau khi hai branch BE/UI đã qua kiểm tra riêng.

## 7. Tiêu chí hoàn thành một task UI

- Giao diện đúng design token và responsive.
- Có trạng thái loading, empty, error và thành công.
- Form có validation phía client nhưng vẫn chờ validation phía server.
- Có thao tác bàn phím, nhãn rõ ràng và màu đủ tương phản.
- Không lộ dữ liệu nhạy cảm trong URL, log hoặc thông báo lỗi.
- `npm run build` chạy thành công.

## 8. Quy ước mã nguồn Java

- Không viết tên đầy đủ của kiểu thư viện ngay trong thân mã, ví dụ `java.time.Instant`, `java.time.LocalDate` hoặc `java.util.Optional`.
- Mỗi kiểu dùng trong file phải được khai báo bằng `import` ở đầu file rồi dùng tên ngắn (`Instant`, `LocalDate`, `Optional`). Quy tắc này áp dụng cho cả mã sản phẩm và mã kiểm thử.
- Không dùng cách viết đầy đủ cho các hàm tiện ích hoặc collection như `java.util.Objects`, `java.util.stream.Stream`, `java.util.Comparator`; hãy import đúng lớp cần dùng.
- Khi thêm một kiểu mới, kiểm tra lại import thừa và giữ nhóm import theo thứ tự chuẩn của IDE.
- Trước khi commit, rà bằng tìm kiếm toàn dự án: `rg "\\bjava\\.(time|util|math|nio|sql|net)\\." src/main src/test --glob "*.java"`; kết quả hợp lệ chỉ là các dòng `import`.
