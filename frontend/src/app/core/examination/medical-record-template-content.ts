export type TemplatePrescriptionLine = {
  medicationName: string;
  dosage: string;
  quantity: number;
  unit?: string;
  instructions: string;
};

export type MedicalRecordTemplateContent = {
  reason?: string;
  examinationNotes?: string;
  diagnosis?: string;
  conclusion?: string;
  treatmentPlan?: string;
  followUpDays?: number;
  followUpNote?: string;
  prescriptionLines?: TemplatePrescriptionLine[];
};

const TEXT_LIMITS = {
  reason: 2000,
  examinationNotes: 2000,
  diagnosis: 2000,
  conclusion: 2000,
  treatmentPlan: 2000,
  followUpNote: 500,
} as const;

type TextField = keyof typeof TEXT_LIMITS;

export function parseMedicalRecordTemplateContent(fieldDefinition: string): MedicalRecordTemplateContent {
  const definition = fieldDefinition.trim();
  if (!definition) return {};

  if (definition.startsWith('{')) {
    try {
      return normalizeContent(JSON.parse(definition) as unknown);
    } catch {
      return {};
    }
  }

  const legacyContent: Record<string, unknown> = {};
  for (const line of definition.split(/\r?\n/)) {
    const [field, , , ...defaultValueParts] = line.split('|');
    const defaultValue = defaultValueParts.join('|').trim();
    if (field && defaultValue) legacyContent[field.trim()] = defaultValue;
  }
  return normalizeContent(legacyContent);
}

export function serializeMedicalRecordTemplateContent(content: MedicalRecordTemplateContent): string {
  return JSON.stringify(normalizeContent(content));
}

function normalizeContent(value: unknown): MedicalRecordTemplateContent {
  if (!isRecord(value)) return {};
  const content: MedicalRecordTemplateContent = {};

  (Object.keys(TEXT_LIMITS) as TextField[]).forEach((field) => {
    const text = value[field];
    if (typeof text !== 'string') return;
    const normalized = text.trim();
    if (normalized && normalized.length <= TEXT_LIMITS[field]) content[field] = normalized;
  });

  const followUpDays = value['followUpDays'];
  if (typeof followUpDays === 'number' && Number.isInteger(followUpDays)
    && followUpDays >= 1 && followUpDays <= 365) {
    content.followUpDays = followUpDays;
  }

  const rawLines = value['prescriptionLines'];
  if (Array.isArray(rawLines)) {
    const lines: TemplatePrescriptionLine[] = [];
    rawLines.forEach((item) => {
      if (isRecord(item) && typeof item['medicationName'] === 'string' && item['medicationName'].trim()) {
        const qty = typeof item['quantity'] === 'number' && item['quantity'] > 0 ? Math.min(Math.floor(item['quantity']), 999) : 1;
        lines.push({
          medicationName: item['medicationName'].trim().slice(0, 200),
          dosage: typeof item['dosage'] === 'string' ? item['dosage'].trim().slice(0, 100) : '',
          quantity: qty,
          unit: typeof item['unit'] === 'string' && item['unit'].trim() ? item['unit'].trim().slice(0, 50) : 'Viên',
          instructions: typeof item['instructions'] === 'string' ? item['instructions'].trim().slice(0, 500) : '',
        });
      }
    });
    if (lines.length > 0) content.prescriptionLines = lines.slice(0, 20);
  }

  return content;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
