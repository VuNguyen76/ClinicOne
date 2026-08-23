export type MedicalRecordTemplateContent = {
  reason?: string;
  examinationNotes?: string;
  diagnosis?: string;
  conclusion?: string;
  treatmentPlan?: string;
  followUpDays?: number;
  followUpNote?: string;
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
  return content;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
