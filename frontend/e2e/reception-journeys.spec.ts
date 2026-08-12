import { expect, Locator, Page, Route, test } from '@playwright/test';

const today = new Date();
const todayIso = [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-');

const existingProfile = {
  id: 'profile-existing',
  fullName: 'Nguyễn Thanh Vũ',
  relationship: 'Bản thân',
  dateOfBirth: '2000-01-01',
  gender: 'Nam',
  phone: '0912345678',
  primaryProfile: true,
};

const walkInAppointment = (patientName: string, phone: string, queueNumber: number) => ({
  id: `appointment-${queueNumber}`,
  appointmentCode: `CL-E2E-${queueNumber}`,
  appointmentDate: todayIso,
  startTime: '09:00:00',
  specialty: 'Nội tổng quát',
  doctorName: 'Bác sĩ Nguyễn An',
  roomCode: 'NOI-01',
  roomName: 'Phòng Nội 01',
  patientProfileId: 'profile-existing',
  patientName,
  patientPhone: phone,
  status: 'BOOKED',
  queueNumber,
  queueStatus: 'WAITING',
  queueStatusLabel: 'Đang chờ',
});

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function mockReceptionApis(page: Page): Promise<void> {
  await page.route('**/api/v1/staff/auth/login', (route) => json(route, {
    accessToken: 'staff-e2e-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z',
    accountId: 'staff-reception', fullName: 'Nhân viên tiếp nhận', role: 'RECEPTIONIST',
  }));
  await page.route('**/api/v1/specialties', (route) => json(route, [
    { code: 'NOI', name: 'Nội tổng quát', description: '' },
  ]));
  await page.route('**/api/v1/appointment-slots**', (route) => json(route, [
    { specialty: 'Nội tổng quát', appointmentDate: todayIso, startTime: '09:00:00', endTime: '09:30:00',
      doctorName: 'Bác sĩ Nguyễn An', doctorId: 'doctor-1', roomCode: 'NOI-01', remainingCapacity: 1 },
  ]));
}

async function signInAsReceptionist(page: Page): Promise<void> {
  await page.goto('/staff/login');
  await page.getByLabel('Tên đăng nhập').fill('receptionist');
  await page.getByLabel('Mật khẩu').fill('admin123');
  await page.getByTestId('staff-login-submit').click();
  await expect(page).toHaveURL(/\/reception\/check-in$/);
}

async function fillWalkInDetails(dialog: Locator, phone: string, reason: string): Promise<void> {
  await dialog.getByLabel('Chuyên khoa').selectOption({ label: 'Nội tổng quát' });
  await dialog.getByLabel('Khung giờ còn trống').selectOption('09:00:00');
  await dialog.getByLabel('Lý do khám').fill(reason);
  await dialog.getByLabel('Lý do tiếp nhận ngoại lệ').fill('Người bệnh đến quầy cần hỗ trợ');
  await expect(dialog.getByLabel('Số điện thoại')).toHaveValue(phone);
}

test.describe('liên thông tiếp nhận và hàng đợi bác sĩ', () => {
  test('tài khoản đã có hồ sơ được tiếp nhận và lượt khám hiện ở màn hình bác sĩ', async ({ page, context }) => {
    await mockReceptionApis(page);
    await page.route('**/api/v1/reception/profiles**', (route) => json(route, [existingProfile]));
    await page.route('**/api/v1/reception/walk-in', (route) => json(route, walkInAppointment('Nguyễn Thanh Vũ', '0912345678', 7), 201));

    await signInAsReceptionist(page);
    await page.getByTestId('open-walk-in').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Số điện thoại').fill('0912345678');
    await dialog.getByRole('button', { name: 'Tìm hồ sơ' }).click();
    await expect(dialog.getByLabel('Hồ sơ người đi khám')).toBeVisible();
    await fillWalkInDetails(dialog, '0912345678', 'Đau đầu từ sáng');
    await dialog.getByRole('button', { name: 'Tạo lịch và cấp số' }).click();
    await expect(page.getByText('Đã tạo lịch và cấp số 07')).toBeVisible();

    const doctorPage = await context.newPage();
    await doctorPage.addInitScript(() => {
      sessionStorage.setItem('clinicOneAccessToken', 'doctor-e2e-token');
      sessionStorage.setItem('clinicOneStaffRole', 'DOCTOR');
    });
    await doctorPage.route('**/api/v1/doctor/queue**', (route) => json(route, {
      roomCode: 'NOI-01', roomName: 'Phòng Nội 01',
      tickets: [{ id: 'ticket-7', queueNumber: 7, specialty: 'Nội tổng quát', doctorName: 'Bác sĩ Nguyễn An',
        appointmentCode: 'CL-E2E-7', appointmentTime: '09:00:00', status: 'WAITING', statusLabel: 'Đang chờ' }],
    }));
    await doctorPage.goto('/doctor');
    await expect(doctorPage.getByTestId('queue-row')).toContainText('7');
    await expect(doctorPage.getByTestId('queue-row')).toContainText('Đang chờ');
    await doctorPage.close();
  });

  test('số điện thoại chưa có tài khoản đi qua OTP, đổi mật khẩu rồi mới được tiếp nhận', async ({ page }) => {
    await mockReceptionApis(page);
    let profileLookupCount = 0;
    await page.route('**/api/v1/reception/profiles**', (route) => {
      profileLookupCount += 1;
      return profileLookupCount === 1 ? json(route, { message: 'Chưa tìm thấy tài khoản' }, 404) : json(route, [existingProfile]);
    });
    await page.route('**/api/v1/reception/patients/request-otp', (route) => json(route, { expiresInSeconds: 300, retryAfterSeconds: 60 }));
    await page.route('**/api/v1/reception/patients', (route) => json(route, {
      accountId: 'patient-new', phone: '0900000001', fullName: 'Trần Bình', mustChangePassword: true,
    }, 201));
    await page.route('**/api/v1/auth/activate', (route) => json(route, null, 204));
    await page.route('**/api/v1/reception/walk-in', (route) => json(route, walkInAppointment('Trần Bình', '0900000001', 8), 201));

    await signInAsReceptionist(page);
    await page.getByTestId('open-walk-in').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Số điện thoại').fill('0900000001');
    await dialog.getByRole('button', { name: 'Tìm hồ sơ' }).click();
    await expect(dialog.getByText('Chưa có tài khoản')).toBeVisible();
    await dialog.getByRole('button', { name: 'Gửi OTP' }).click();
    await dialog.getByLabel('Mã OTP').fill('123456');
    await dialog.getByLabel('Họ và tên').fill('Trần Bình');
    await dialog.getByLabel('Ngày sinh').fill('1995-05-05');
    await dialog.getByLabel('Giới tính').selectOption('Nam');
    await dialog.getByRole('button', { name: 'Tạo tài khoản' }).click();
    await expect(dialog).toContainText('Người bệnh cần tự đặt mật khẩu mới trước khi check-in');
    await dialog.getByLabel('Mật khẩu mới').fill('correct-password');
    await dialog.getByLabel('Nhập lại mật khẩu').fill('correct-password');
    await dialog.getByRole('button', { name: 'Đặt mật khẩu và tiếp tục' }).click();
    await expect(dialog.getByLabel('Hồ sơ người đi khám')).toBeVisible();
    await fillWalkInDetails(dialog, '0900000001', 'Người bệnh mới được hỗ trợ tại quầy');
    await dialog.getByRole('button', { name: 'Tạo lịch và cấp số' }).click();
    await expect(page.getByText('Đã tạo lịch và cấp số 08')).toBeVisible();
  });

  test('bác sĩ gọi số, mở phiên khám, lưu nháp và ký phiếu để hoàn tất lượt', async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('clinicOneAccessToken', 'doctor-e2e-token');
      sessionStorage.setItem('clinicOneStaffRole', 'DOCTOR');
    });
    let queueState = 'WAITING';
    const ticket = () => ({ id: 'ticket-9', queueNumber: 9, roomCode: 'NOI-01', roomName: 'Phòng Nội 01',
      queueDate: todayIso, appointmentTime: '09:00:00', status: queueState,
      statusLabel: queueState === 'WAITING' ? 'Đang chờ' : queueState === 'CALLED' ? 'Đã gọi' : queueState === 'IN_SERVICE' ? 'Đang khám' : 'Hoàn tất',
      appointmentCode: 'CL-E2E-9', specialty: 'Nội tổng quát', doctorName: 'Bác sĩ Nguyễn An' });
    await page.route('**/api/v1/doctor/queue**', (route) => json(route, {
      roomCode: 'NOI-01', roomName: 'Phòng Nội 01', specialty: 'Nội tổng quát', tickets: [ticket()],
    }));
    await page.route('**/api/v1/doctor/queue/call-next**', (route) => {
      queueState = 'CALLED';
      return json(route, ticket());
    });
    await page.route('**/api/v1/doctor/examinations/ticket-9/start', (route) => {
      queueState = 'IN_SERVICE';
      return json(route, ticket());
    });
    const draft = {
      ticketId: 'ticket-9', appointmentId: 'appointment-9', examinationId: 'exam-9', queueNumber: 9,
      roomName: 'Phòng Nội 01', appointmentCode: 'CL-E2E-9', specialty: 'Nội tổng quát',
      doctorName: 'Bác sĩ Nguyễn An', appointmentDate: todayIso, startTime: '09:00:00', patientName: 'Trần Bình',
      patientDateOfBirth: '1995-05-05', patientGender: 'Nam', patientPhone: '0900000001', reason: '',
      examinationNotes: '', diagnosis: '', conclusion: '', treatmentPlan: '', prescription: '', followUpDate: null,
      status: 'IN_PROGRESS', signedAt: null,
    };
    await page.route('**/api/v1/doctor/examinations/ticket-9', (route) => json(route, draft));
    await page.route('**/api/v1/doctor/examinations/ticket-9/draft', (route) => json(route, {
      ...draft, reason: 'Đau đầu', examinationNotes: 'Mạch ổn', diagnosis: 'Đau đầu căng thẳng', conclusion: 'Theo dõi thêm',
    }));
    await page.route('**/api/v1/doctor/examinations/ticket-9/sign', (route) => json(route, {
      ...draft, reason: 'Đau đầu', examinationNotes: 'Mạch ổn', diagnosis: 'Đau đầu căng thẳng', conclusion: 'Theo dõi thêm',
      status: 'COMPLETED', signedAt: '2026-08-07T09:30:00Z',
    }));

    await page.goto('/doctor');
    await page.getByTestId('call-next').click();
    await expect(page.getByTestId('queue-row')).toContainText('Đã gọi');
    await page.getByRole('button', { name: 'Vào khám' }).click();
    await expect(page).toHaveURL(/\/doctor\/examinations\/ticket-9$/);
    const form = page.getByTestId('medical-form');
    await form.locator('textarea[formcontrolname="reason"]').fill('Đau đầu');
    await form.locator('textarea[formcontrolname="examinationNotes"]').fill('Mạch ổn');
    await form.locator('textarea[formcontrolname="diagnosis"]').fill('Đau đầu căng thẳng');
    await form.locator('textarea[formcontrolname="conclusion"]').fill('Theo dõi thêm');
    await form.getByTestId('save-draft').click();
    await expect(page.getByText('Đã lưu bản nháp')).toBeVisible();
    await form.getByTestId('sign-record').click();
    await page.getByTestId('confirm-sign-record').click();
    await expect(page.getByText('Đã ký phiếu khám')).toBeVisible();
    await expect(form.locator('textarea[formcontrolname="reason"]')).toBeDisabled();
    await expect(page.getByText('Đã hoàn thành')).toBeVisible();
  });
});
