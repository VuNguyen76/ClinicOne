import { parseMedicalRecordTemplateContent, serializeMedicalRecordTemplateContent } from './medical-record-template-content';

describe('medical record template content', () => {
  it('reads the structured content saved by the template management screen', () => {
    expect(parseMedicalRecordTemplateContent(JSON.stringify({
      examinationNotes: 'Khám theo trình tự nội tổng quát.',
      conclusion: 'Theo dõi diễn tiến.',
      treatmentPlan: 'Tư vấn chăm sóc và tái khám khi cần.',
      followUpDays: 14,
      followUpNote: 'Mang theo kết quả xét nghiệm khi tái khám.',
    }))).toEqual({
      examinationNotes: 'Khám theo trình tự nội tổng quát.',
      conclusion: 'Theo dõi diễn tiến.',
      treatmentPlan: 'Tư vấn chăm sóc và tái khám khi cần.',
      followUpDays: 14,
      followUpNote: 'Mang theo kết quả xét nghiệm khi tái khám.',
    });
  });

  it('keeps compatibility with the previous pipe-separated template format', () => {
    expect(parseMedicalRecordTemplateContent([
      'reason|Lý do khám|required|Đau đầu kéo dài',
      'examinationNotes|Ghi nhận khám|required|Khám thần kinh, đo huyết áp',
      'unknown|Trường cũ|optional|Không dùng',
    ].join('\n'))).toEqual({
      reason: 'Đau đầu kéo dài',
      examinationNotes: 'Khám thần kinh, đo huyết áp',
    });
  });

  it('ignores malformed or unsafe values instead of changing the medical form', () => {
    expect(parseMedicalRecordTemplateContent('{invalid-json')).toEqual({});
    expect(parseMedicalRecordTemplateContent(JSON.stringify({
      reason: 123,
      followUpDays: 900,
      followUpNote: ' '.repeat(10),
    }))).toEqual({});
  });

  it('serializes only normalized content that can be applied safely', () => {
    expect(JSON.parse(serializeMedicalRecordTemplateContent({
      reason: '  Đau đầu  ',
      examinationNotes: '',
      followUpDays: 7,
      followUpNote: '  Mang kết quả cũ. ',
    }))).toEqual({
      reason: 'Đau đầu',
      followUpDays: 7,
      followUpNote: 'Mang kết quả cũ.',
    });
  });
});
