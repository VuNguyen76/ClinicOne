import { expect, test } from '@playwright/test';

test('bệnh nhân mở thông báo phiếu khám và đánh dấu đã đọc', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('clinicOneAccessToken', 'patient-e2e-token');
    sessionStorage.setItem('clinicOnePatientName', 'Nguyễn An');
  });

  await page.route('**/api/v1/notifications', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify([{
      id: 'notification-1',
      type: 'MEDICAL_RECORD_SIGNED',
      title: 'Phiếu khám đã có kết quả',
      message: 'Bác sĩ đã ký phiếu khám cho lịch hẹn CL-001.',
      targetUrl: '/medical-records/record-1',
      read: false,
      createdAt: '2026-08-07T09:30:00Z',
    }]),
  }));
  let markedRead = false;
  await page.route('**/api/v1/notifications/notification-1/read', async (route) => {
    markedRead = true;
    await route.fulfill({ status: 204, body: '' });
  });
  await page.route('**/api/v1/medical-records/record-1', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      id: 'record-1', examinationId: 'exam-1', appointmentCode: 'CL-001', doctorName: 'Bác sĩ An',
      reason: 'Đau đầu', examinationNotes: 'Mạch ổn', diagnosis: 'Đau đầu do căng thẳng',
      conclusion: 'Theo dõi thêm', treatmentPlan: 'Nghỉ ngơi', prescription: null, followUpDate: null,
      signedAt: '2026-08-07T09:30:00Z',
    }),
  }));

  await page.goto('/notifications');
  await expect(page.getByTestId('notification-item')).toHaveCount(1);
  await expect(page.getByTestId('notification-unread')).toBeVisible();

  await page.getByTestId('notification-item').click();
  await expect.poll(() => markedRead).toBe(true);
  await expect(page).toHaveURL(/\/medical-records\/record-1$/);
  await expect(page.getByText('Đau đầu do căng thẳng').first()).toBeVisible();
});
