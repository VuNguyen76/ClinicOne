import { expect, test } from '@playwright/test';

const signedRecord = {
  id: 'record-e2e-1', examinationId: 'exam-e2e-1', appointmentCode: 'CL-E2E-0009',
  doctorName: 'Bác sĩ Nguyễn An', reason: 'Đau đầu', examinationNotes: 'Mạch ổn',
  diagnosis: 'Đau đầu do căng thẳng', conclusion: 'Theo dõi thêm', treatmentPlan: 'Nghỉ ngơi',
  prescription: 'Paracetamol 500mg', followUpDate: '2026-08-20', signedAt: '2026-08-07T09:30:00Z',
};

test('bệnh nhân chỉ xem được phiếu khám đã ký', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('clinicOneAccessToken', 'patient-e2e-token');
    sessionStorage.setItem('clinicOnePatientName', 'Trần Bình');
  });
  await page.route('**/api/v1/medical-records**', (route) => route.request().method() === 'GET'
    ? route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: [signedRecord], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    })
    : route.continue());
  await page.route('**/api/v1/medical-records/record-e2e-1', (route) => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(signedRecord),
  }));

  await page.goto('/medical-records');
  await expect(page.getByRole('heading', { name: 'Phiếu khám bệnh' })).toBeVisible();
  await expect(page.getByText('Đau đầu do căng thẳng')).toBeVisible();
  await expect(page.getByText('Đã ký').first()).toBeVisible();
  await expect(page.getByRole('link', { name: /Đau đầu do căng thẳng/ })).toHaveAttribute('href', '/medical-records/record-e2e-1');

  await page.getByRole('link', { name: /Đau đầu do căng thẳng/ }).click();
  await expect(page).toHaveURL(/\/medical-records\/record-e2e-1$/);
  await expect(page.getByText('Paracetamol 500mg')).toBeVisible();
  await expect(page.getByText('Hẹn tái khám')).toBeVisible();
});
