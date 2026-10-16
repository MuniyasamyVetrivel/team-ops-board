import { z } from 'zod';

const text = (max: number) => z.string().trim().max(max, `At most ${max} characters`);

/** Mirrors BenchText: at most 50 skills of up to 100 characters, de-duplicated ignoring case. */
export const MAX_SKILLS = 50;
export const MAX_SKILL_LENGTH = 100;
export const MAX_CERTIFICATIONS = 30;

/** Mirrors BenchDtos.SaveCertification and ConsultantService (expiry on or after the issue date). */
export const certificationSchema = z
  .object({
    id: z.number().nullable(),
    name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
    issuer: text(150),
    issuedOn: z.string(),
    expiresOn: z.string(),
    credentialId: text(100),
  })
  .refine((c) => !c.issuedOn || !c.expiresOn || c.expiresOn >= c.issuedOn, { path: ['expiresOn'], message: 'The expiry date must be on or after the issue date' });

/** Mirrors BenchDtos.SaveConsultant. */
export const consultantSchema = z.object({
  employeeId: z.string().min(1, 'Choose the employee'),
  experienceYears: z
    .string()
    .trim()
    .refine((v) => v === '' || (/^\d{1,2}(\.\d)?$/.test(v) && Number(v) <= 60), 'Enter 0 to 60 years, with at most one decimal'),
  location: text(150),
  availableFrom: z.string(),
  employmentStatus: z.enum(['ACTIVE', 'LEAVE_OF_ABSENCE', 'NOTICE_PERIOD', 'LEFT']),
  skills: z.array(z.string().max(MAX_SKILL_LENGTH, `Skills can be at most ${MAX_SKILL_LENGTH} characters`)).max(MAX_SKILLS, `At most ${MAX_SKILLS} skills`),
  certifications: z.array(certificationSchema).max(MAX_CERTIFICATIONS, `At most ${MAX_CERTIFICATIONS} certifications`),
  notes: text(5000),
});

export type ConsultantValues = z.infer<typeof consultantSchema>;

/** Adds a skill unless it is blank or already listed (ignoring case), as the server de-duplicates. */
export function addSkill(skills: string[], raw: string): string[] {
  const skill = raw.trim();
  if (!skill || skills.some((s) => s.toLowerCase() === skill.toLowerCase())) return skills;
  return [...skills, skill];
}

/** Mirrors BenchDtos.SaveClient. */
export const clientSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
  location: text(150),
  status: z.enum(['ACTIVE', 'INACTIVE']),
  notes: text(5000),
});

export type ClientValues = z.infer<typeof clientSchema>;

/** Mirrors BenchDtos.SaveEngagement and EngagementService (end on or after the start). */
export const engagementSchema = z
  .object({
    clientId: z.string().min(1, 'Choose the client'),
    name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
    location: text(150),
    startDate: z.string().min(1, 'Start date is required'),
    endDate: z.string(),
    accountManagerId: z.string(),
    notes: text(5000),
  })
  .refine((v) => !v.endDate || !v.startDate || v.endDate >= v.startDate, { path: ['endDate'], message: 'The end date must be on or after the start date' });

export type EngagementValues = z.infer<typeof engagementSchema>;

/** Mirrors BenchDtos.SaveAllocation and AllocationService (1–100%, end on or after the start). */
export const allocationSchema = z
  .object({
    consultantId: z.string().min(1, 'Choose the consultant'),
    engagementId: z.string().min(1, 'Choose the engagement'),
    role: z.string().trim().min(1, 'Role is required').max(150, 'At most 150 characters'),
    startDate: z.string().min(1, 'Start date is required'),
    endDate: z.string(),
    allocationPercent: z
      .string()
      .trim()
      .min(1, 'Allocation is required')
      .refine((v) => /^\d+$/.test(v) && Number(v) >= 1 && Number(v) <= 100, 'Enter a whole number from 1 to 100'),
    billable: z.boolean(),
    notes: text(5000),
  })
  .refine((v) => !v.endDate || !v.startDate || v.endDate >= v.startDate, { path: ['endDate'], message: 'The end date must be on or after the start date' });

export type AllocationValues = z.infer<typeof allocationSchema>;

/**
 * Where the allocation falls outside its engagement (the server rejects it with OUTSIDE_ENGAGEMENT), as a message for
 * the field to blame; null when it fits.
 */
export function outsideEngagement(
  values: Pick<AllocationValues, 'startDate' | 'endDate'>,
  engagement: { startDate: string; endDate: string | null },
): { field: 'startDate' | 'endDate'; message: string } | null {
  if (values.startDate && values.startDate < engagement.startDate) return { field: 'startDate', message: 'Starts before the engagement' };
  if (engagement.endDate) {
    if (!values.endDate) return { field: 'endDate', message: 'The engagement ends, so the allocation needs an end date' };
    if (values.endDate > engagement.endDate) return { field: 'endDate', message: 'Ends after the engagement' };
  }
  return null;
}
