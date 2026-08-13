# ClinicOne — Use Case, giao dịch và API v0.1

**Cơ sở:** SRS v1.7, ERD v0.1 và ADR-0002  
**Base path:** `/api/v1`  
**Định dạng:** JSON; ngày/giờ dùng kiểu ISO tương ứng với trường API. Các endpoint hiện tại trả dữ liệu trực tiếp, không bọc trong `data`.

## 1. Quy tắc API chung

- URL dùng danh từ số nhiều, chữ thường và dấu gạch ngang.
- Dữ liệu thành công trả trực tiếp theo kiểu response của endpoint; không mặc định có lớp `{ "data": ... }`.
- Lỗi trả trong `{ "error": { "code", "message", "details" } }`.
- `400` cho JSON/định dạng sai, `401` thiếu đăng nhập, `403` sai quyền, `404` không tìm thấy, `409` xung đột trạng thái hoặc trùng thao tác, `422` dữ liệu hợp lệ về cú pháp nhưng không hợp lệ nghiệp vụ.
- Mọi thao tác ghi nhận có thể bị gửi lại phải nhận `Idempotency-Key`.
- Không trả mật khẩu, OTP, token, chẩn đoán hoặc toàn văn phiếu khám trong lỗi hay nhật ký HTTP.

## 2. Use case và ranh giới giao dịch

### UC-01 — Xác nhận đặt lịch

**Actor:** Bệnh nhân hoặc nhân viên tiếp nhận hỗ trợ.  
**Input:** `specialty`, `doctorName`, `appointmentDate`, `startTime`, `reason`; có thể kèm `profileId`, `doctorId`, `holdId`, `serviceId`.  
**Điều kiện:** khung còn trống hoặc phiên giữ chỗ còn hiệu lực; hồ sơ thuộc quyền sử dụng; lý do 3–500 ký tự.

**Một giao dịch:**

1. Khóa khung giờ và kiểm tra lần cuối.
2. Tạo lịch `BOOKED` và mã lịch hẹn.
3. Chuyển khung sang `OCCUPIED`.
4. Ghi `business_log` với khóa chống trùng.
5. Tạo thông báo và `outbox_event` nếu cần.

Nếu một bước thất bại, rollback toàn bộ; không để lịch và khung giờ lệch nhau.

### UC-02 — Hủy lịch

**Actor:** Bệnh nhân sở hữu lịch hoặc nhân viên có quyền hỗ trợ.  
**Input:** xác nhận hủy, mã lý do khi hủy trong ngưỡng yêu cầu.  
**Kết quả:** lịch `CANCELLED`, lưu người/lý do hủy, khung trả về `AVAILABLE` nếu còn được đặt lại, ghi nhật ký và tạo Outbox.

Retry cùng `Idempotency-Key` được phép ngay; không tạo hủy lần hai.

### UC-03 — Check-in bằng QR phòng

**Actor:** Bệnh nhân.  
**Input:** mã QR phòng cố định trước phòng bác sĩ; mã lịch hẹn lấy từ phiên đăng nhập.

**Một giao dịch:**

1. Xác nhận QR thuộc phòng được cấu hình.
2. Xác nhận lịch thuộc bệnh nhân, ở `BOOKED` và nằm trong ngày cho phép.
3. Tạo đúng một `queue_entry` với số thứ tự.
4. Tạo đúng một `examination_session` ở `CREATED`.
5. Chuyển lịch sang `CHECKED_IN` và ghi nhật ký.

Quét lại trả phần tử hàng đợi và lượt khám cũ, không cấp số mới.

### UC-04 — Gọi bệnh nhân

**Actor:** Bác sĩ.  
**Input:** ca làm việc hiện tại và hành động gọi.

Hệ thống chọn phần tử `WAITING` hợp lệ theo quy tắc ưu tiên/giờ đến, chuyển thành `CALLED`, tăng số lần gọi và ghi nhật ký. Bác sĩ không nhập trạng thái đích.

### UC-05 — Bắt đầu khám

**Actor:** Bác sĩ phụ trách.  
**Điều kiện:** queue đang `CALLED`, lượt đang `CREATED`, bác sĩ đúng người phụ trách.

**Một giao dịch:** lượt → `IN_PROGRESS`, ghi `started_at`, tạo bản chụp hồ sơ và mở phiếu nháp nếu loại lượt yêu cầu phiếu.

### UC-06 — Lưu nháp phiếu

**Actor:** Bác sĩ phụ trách.  
**Điều kiện:** lượt `IN_PROGRESS`, phiếu chưa ký.

Lưu nội dung nháp, giữ nguyên lượt `IN_PROGRESS`, hàng đợi `CALLED`, phiếu còn sửa được và chưa ghi `ended_at`.

### UC-07 — Ký phiếu

**Actor:** Bác sĩ phụ trách.  
**Điều kiện:** phiếu đủ bốn nội dung bắt buộc; từng dòng thuốc đủ liều, số lượng và cách dùng.

**Một giao dịch:** khóa phiếu, ghi người/giờ ký, lượt → `COMPLETED`, lịch → `COMPLETED`, hàng đợi → `CLOSED/EXAM_COMPLETED`, ghi nhật ký và tạo Outbox. Ký lại trả kết quả cũ.

### UC-08 — Kết thúc lượt không cần phiếu

**Actor:** Bác sĩ phụ trách.  
**Điều kiện:** loại lượt có `requiresMedicalRecord = false`, lượt đang `IN_PROGRESS`.

Không kiểm tra bốn trường phiếu; thực hiện cùng giao dịch đóng lượt, lịch và hàng đợi như UC-07 nhưng không tạo phiếu.

## 3. Hợp đồng endpoint

### Đặt lịch

```http
POST /api/v1/appointments
Idempotency-Key: book-20260803-001
Content-Type: application/json
```

```json
{
  "specialty": "Khám tổng quát",
  "doctorName": "Bác sĩ Nguyễn An",
  "appointmentDate": "2026-08-10",
  "startTime": "09:00:00",
  "profileId": "profile-1",
  "doctorId": "doctor-1",
  "serviceId": "service-1",
  "reason": "Đau đầu kéo dài"
}
```

`201 Created`:

```json
{
  "appointmentCode": "CLN-7K2P4M",
  "status": "BOOKED",
  "appointmentDate": "2026-08-10",
  "startTime": "09:00:00"
}
```

### Check-in QR

```http
POST /api/v1/rooms/{roomCode}/queue/check-in
Idempotency-Key: checkin-appointment-1
```

```json
{ "appointmentId": "appointment-1" }
```

`200 OK` trả vé hàng đợi và thông tin lượt khám; gửi lại cùng khóa trả đúng kết quả cũ. QR cố định của phòng được dùng để lấy `roomCode`, không gửi dữ liệu cá nhân trong QR.

### Gọi người tiếp theo

```http
POST /api/v1/doctor/queue/call-next?date=2026-08-10
```

Hệ thống lấy bác sĩ từ phiên đăng nhập; không nhận `doctorId` trong body.

### Bắt đầu khám

```http
POST /api/v1/doctor/examinations/{ticketId}/start
Idempotency-Key: start-examination-1
```

### Lưu nháp

```http
PUT /api/v1/doctor/examinations/{ticketId}/draft
```

```json
{
  "reason": "Đau đầu kéo dài",
  "symptomsAndFindings": "Đau vùng trán, không sốt",
  "diagnosis": "Đau đầu căng thẳng",
  "conclusionAndPlan": "Nghỉ ngơi và theo dõi"
}
```

### Ký phiếu

```http
POST /api/v1/doctor/examinations/{ticketId}/sign
Idempotency-Key: sign-examination-1
```

### Kết thúc lượt không cần phiếu

```http
POST /api/v1/doctor/examinations/{ticketId}/stop
Idempotency-Key: complete-examination-1
```

`stop` chỉ dùng cho loại lượt không cần phiếu. Với lượt cần phiếu, bác sĩ gọi `sign`; hệ thống tự hoàn tất lượt, lịch và hàng đợi trong cùng giao dịch.

## 4. Mã lỗi nghiệp vụ tối thiểu

| Mã | HTTP | Khi dùng |
|---|---:|---|
| `VALIDATION_ERROR` | 422 | Thiếu lý do, phiếu chưa đủ hoặc input ngoài giới hạn. |
| `SLOT_NOT_AVAILABLE` | 409 | Khung đã bị giữ, chiếm dụng, khóa hoặc hết phiên. |
| `IDEMPOTENCY_CONFLICT` | 409 | Cùng khóa nhưng khác nội dung yêu cầu. |
| `APPOINTMENT_NOT_OWNED` | 403 | Tài khoản không sở hữu lịch. |
| `INVALID_QR_ROOM` | 422 | QR không thuộc phòng hợp lệ của lịch. |
| `INVALID_STATE_TRANSITION` | 409 | Hành động không phù hợp trạng thái hiện tại. |
| `DOCTOR_NOT_RESPONSIBLE` | 403 | Bác sĩ không phụ trách lượt. |
| `MEDICAL_RECORD_LOCKED` | 409 | Phiếu đã ký, chỉ được xem. |
| `RECONCILIATION_REQUIRED` | 409 | Không xác định được kết quả, cần đối soát. |

## 5. Phân quyền endpoint

| Endpoint | Bệnh nhân | Tiếp nhận | Bác sĩ | Điều phối | Quản trị |
|---|---|---|---|---|---|
| `POST /appointments` | Có | Có | Không | Không | Không |
| `POST /rooms/{roomCode}/queue/check-in` | Có | Ngoại lệ tại quầy | Không | Không | Không |
| `POST /doctor/queue/call-next` | Không | Không | Có | Không | Không |
| `POST /doctor/examinations/{ticketId}/start` | Không | Không | Bác sĩ phụ trách | Không | Không |
| `PUT /doctor/examinations/{ticketId}/draft` | Không | Không | Bác sĩ phụ trách | Không | Không |
| `POST /doctor/examinations/{ticketId}/sign` | Không | Không | Bác sĩ phụ trách | Không | Không |
| `POST /doctor/examinations/{ticketId}/stop` | Không | Không | Bác sĩ phụ trách | Không | Không |

Các endpoint quản trị danh mục, lịch làm việc, đổi lịch, báo cáo, tài khoản và đối soát đã có trong code hiện tại; khi thay đổi controller phải cập nhật bảng quyền và kiểm thử tương ứng, không tự suy diễn thêm actor.
