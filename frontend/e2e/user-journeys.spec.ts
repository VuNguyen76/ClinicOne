import { expect, Page, Route, test } from '@playwright/test';

const profile = {
  accountId: 'account-1',
  phone: '0912345678',
  fullName: 'Nguyễn An',
  dateOfBirth: '2000-01-01',
  gender: 'Nam',
  address: null,
  identityNumber: null,
  nationality: 'Việt Nam',
  ethnicity: 'Kinh',
  provinceCode: null,
  provinceName: null,
  districtCode: null,
  districtName: null,
  wardCode: null,
  wardName: null,
  streetAddress: null,
  status: 'ACTIVE',
  mustChangePassword: false,
};

const patientProfile = {
  id: 'profile-1',
  fullName: 'Nguyễn An',
  relationship: 'Bản thân',
  dateOfBirth: '2000-01-01',
  gender: 'Nam',
  phone: '0912345678',
  identityNumber: null,
  nationality: 'Việt Nam',
  ethnicity: 'Kinh',
  address: null,
  provinceCode: null,
  provinceName: null,
  districtCode: null,
  districtName: null,
  wardCode: null,
  wardName: null,
  streetAddress: null,
  primaryProfile: true,
};

async function fulfillJson(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

function nextWorkingDate(): string {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  if (date.getDay() === 0) date.setDate(date.getDate() + 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

async function mockExistingAccount(page: Page): Promise<void> {
  await page.route('**/api/v1/auth/check-phone', (route) => fulfillJson(route, { accountExists: true }));
  await page.route('**/api/v1/auth/login', (route) => fulfillJson(route, {
    accessToken: 'e2e-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z',
    accountId: profile.accountId, fullName: profile.fullName, mustChangePassword: false,
  }));
  await page.route('**/api/v1/auth/me', (route) => fulfillJson(route, profile));
  await page.route('**/api/v1/appointments', (route) => fulfillJson(route, []));
}

test.describe('luồng người dùng ClinicOne', () => {
  test('chặn người chưa đăng nhập khỏi dashboard', async ({ page }) => {
    await page.goto('/dashboard');

    await expect(page).toHaveURL(/\/login(?:\?.*)?$/);
    await expect(page.getByRole('heading', { name: 'Nhập số điện thoại' })).toBeVisible();
  });

  test('tài khoản đã có đăng nhập bằng số điện thoại và mật khẩu', async ({ page }) => {
    await mockExistingAccount(page);
    await page.goto('/login');

    await page.getByLabel('Số điện thoại').fill(profile.phone);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Nhập mật khẩu' })).toBeVisible();

    await page.getByLabel('Mật khẩu').fill('correct-password');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('heading', { name: /Xin chào, Nguyễn An/ })).toBeVisible();
  });

  test('số chưa đăng ký đi qua OTP và tạo tài khoản', async ({ page }) => {
    await page.route('**/api/v1/auth/check-phone', (route) => fulfillJson(route, { accountExists: false }));
    await page.route('**/api/v1/auth/request-sms-otp', (route) => fulfillJson(route, {
      expiresInSeconds: 300, retryAfterSeconds: 60,
    }));
    await page.route('**/api/v1/auth/verify-sms-otp', (route) => fulfillJson(route, { verified: true }));
    await page.route('**/api/v1/auth/register', (route) => fulfillJson(route, {
      accountId: 'account-new', phone: '0987654321', fullName: 'Trần Bình',
    }, 201));
    await page.route('**tinhthanhpho.com/api/v1/provinces**', (route) => fulfillJson(route, { success: true, data: [] }));

    await page.goto('/login');
    await page.getByLabel('Số điện thoại').fill('0987654321');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page).toHaveURL(/\/register\?phone=0987654321/);

    await page.getByRole('button', { name: 'Nhận mã OTP' }).click();
    await expect(page.getByRole('heading', { name: 'Xác thực số điện thoại' })).toBeVisible();
    await page.getByLabel('Mã OTP').fill('123456');
    await page.getByRole('button', { name: 'Tiếp tục' }).click();

    await expect(page.getByRole('heading', { name: 'Hoàn tất tài khoản' })).toBeVisible();
    await page.getByLabel('Họ và tên').fill('Trần Bình');
    await page.getByLabel('Ngày sinh').fill('1995-05-05');
    await page.getByLabel('Giới tính').selectOption('Nam');
    await page.getByLabel('Mật khẩu', { exact: true }).fill('correct-password');
    await page.getByLabel('Nhập lại mật khẩu').fill('correct-password');
    await page.getByRole('button', { name: 'Tạo tài khoản' }).click();

    await expect(page.getByText('Tài khoản đã tạo.')).toBeVisible();
    await page.getByRole('button', { name: 'Đăng nhập' }).click();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('người dùng chọn chuyên khoa, khung giờ và đặt lịch', async ({ page }) => {
    const appointmentDate = nextWorkingDate();
    const day = appointmentDate.slice(-2);

    await mockExistingAccount(page);
    await page.route('**/api/v1/specialties', (route) => fulfillJson(route, [
      { code: 'NOI', name: 'Nội tổng quát', description: 'Khám và tư vấn sức khỏe tổng quát.' },
    ]));
    await page.route('**/api/v1/patient-profiles', (route) => fulfillJson(route, [patientProfile]));
    await page.route('**/api/v1/appointment-slots**', (route) => fulfillJson(route, [
      {
        specialty: 'Nội tổng quát', appointmentDate, startTime: '08:30:00', endTime: '09:30:00',
        doctorName: 'Bác sĩ chuyên khoa', remainingCapacity: 9,
      },
    ]));
    await page.route('**/api/v1/appointments', async (route) => {
      if (route.request().method() === 'POST') {
        await fulfillJson(route, {
          id: 'appointment-1', appointmentCode: 'CL-E2E-001', specialty: 'Nội tổng quát',
          doctorName: 'Bác sĩ chuyên khoa', appointmentDate, startTime: '08:30:00',
          reason: 'Đau đầu nhẹ', status: 'BOOKED', statusLabel: 'Đã đặt', profileId: patientProfile.id,
          profileName: patientProfile.fullName,
        }, 201);
        return;
      }
      await fulfillJson(route, []);
    });

    await page.goto('/login');
    await page.getByLabel('Số điện thoại').fill(profile.phone);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.getByLabel('Mật khẩu').fill('correct-password');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.getByRole('link', { name: 'Đặt lịch khám', exact: true }).click();

    await page.getByRole('button', { name: /Nội tổng quát/ }).click();
    await expect(page.getByText('Vui lòng chọn ngày và giờ khám', { exact: true })).toBeVisible();
    await page.locator('[data-testid="calendar-date"]').filter({ hasText: day }).first().click();
    await page.getByRole('button', { name: '08:30 - 09:30' }).click();
    await page.getByRole('button', { name: 'Tiếp tục' }).click();
    await page.getByLabel('Lý do khám *').fill('Đau đầu nhẹ');
    await page.getByRole('button', { name: 'Xác nhận đặt lịch' }).click();

    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('đăng nhập từ QR phòng quay lại đúng phòng', async ({ page }) => {
    const qrToken = 'room-qr-token';
    const today = new Date();
    const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    await mockExistingAccount(page);
    await page.route(`**/api/v1/rooms/${qrToken}/check-in`, (route) => fulfillJson(route, {
      code: 'NOI-01', name: 'Phòng Nội tổng quát 01', specialty: 'Nội tổng quát',
    }));
    await page.route('**/api/v1/appointments', (route) => fulfillJson(route, [{
      id: 'appointment-1', appointmentCode: 'CL-E2E-001', specialty: 'Nội tổng quát',
      doctorName: 'BS. Nguyễn An', appointmentDate: todayIso, startTime: '09:00:00',
      reason: 'Đau đầu', status: 'BOOKED', statusLabel: 'Đã đặt',
    }]));

    await page.goto(`/queue/check-in/${qrToken}`);
    await expect(page).toHaveURL(new RegExp(`/login\\?returnUrl=${encodeURIComponent(`/queue/check-in/${qrToken}`)}`));
    await page.getByLabel('Số điện thoại').fill(profile.phone);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await page.getByLabel('Mật khẩu').fill('correct-password');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`/queue/check-in/${qrToken}$`));
    await expect(page.getByText('Phòng Nội tổng quát 01')).toBeVisible();
  });

  test('bệnh nhân quét mã phòng và nhận số thứ tự', async ({ page }) => {
    const today = new Date();
    const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    const qrToken = 'room-qr-token';
    await page.addInitScript(() => {
      sessionStorage.setItem('clinicOneAccessToken', 'e2e-token');
      sessionStorage.setItem('clinicOnePatientName', 'Nguyễn An');
    });
    await page.route('**/api/v1/appointments', (route) => fulfillJson(route, [{
      id: 'appointment-1', appointmentCode: 'CL-E2E-001', specialty: 'Nội tổng quát',
      doctorName: 'BS. Nguyễn An', appointmentDate: todayIso, startTime: '09:00:00',
      reason: 'Đau đầu', status: 'BOOKED', statusLabel: 'Đã đặt',
    }]));
    await page.route(`**/api/v1/rooms/${qrToken}/check-in`, (route) => fulfillJson(route, {
      code: 'NOI-01', name: 'Phòng Nội tổng quát 01', specialty: 'Nội tổng quát',
    }));
    await page.route(`**/api/v1/rooms/${qrToken}/queue/check-in`, (route) => fulfillJson(route, {
      id: 'ticket-1', queueNumber: 5, roomCode: 'NOI-01', roomName: 'Phòng Nội tổng quát 01',
      queueDate: todayIso, appointmentTime: '09:00:00', status: 'WAITING', statusLabel: 'Đang chờ',
      appointmentCode: 'CL-E2E-001', specialty: 'Nội tổng quát', doctorName: 'BS. Nguyễn An',
    }));

    await page.goto(`/queue/check-in/${qrToken}`);
    await page.getByTestId('check-in-appointment').click();
    await page.getByTestId('check-in-submit').click();

    await expect(page.getByTestId('queue-number')).toHaveText('05');
    await expect(page.getByTestId('queue-status')).toContainText('Đang chờ');
  });
});
