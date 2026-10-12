import { z } from 'zod';

import { isHttpLink } from './celebration-meta';

/**
 * Mirrors CelebrationDtos.SaveEmployee and EmployeeService: names, designation and reporting manager are required
 * unless a linked app user fills them; the birth date is not in the future (`today` is the business date when known)
 * and joining comes after it.
 */
export function employeeSchema(today: string | null) {
  return z
    .object({
      employeeCode: z.string().trim().min(1, 'Employee ID is required').max(40, 'At most 40 characters'),
      firstName: z.string().trim().max(100, 'At most 100 characters'),
      lastName: z.string().trim().max(100, 'At most 100 characters'),
      dateOfBirth: z.string().min(1, 'Date of birth is required'),
      designation: z.string().trim().max(150, 'At most 150 characters'),
      reportingManager: z.string().trim().max(150, 'At most 150 characters'),
      dateOfJoining: z.string(),
      departmentId: z.string(),
      workEmail: z
        .string()
        .trim()
        .max(254, 'At most 254 characters')
        .refine((v) => v === '' || z.email().safeParse(v).success, 'Enter a valid email address'),
      officeLocation: z.string().trim().max(150, 'At most 150 characters'),
      userId: z.string(),
      status: z.enum(['ACTIVE', 'LEFT']),
    })
    .superRefine((v, ctx) => {
      if (!v.userId) {
        const required = [
          ['firstName', 'First name'],
          ['lastName', 'Last name'],
          ['designation', 'Designation'],
          ['reportingManager', 'Reporting manager'],
        ] as const;
        for (const [field, label] of required) {
          if (!v[field].trim()) ctx.addIssue({ code: 'custom', path: [field], message: `${label} is required (or link an app user)` });
        }
      }
      if (today && v.dateOfBirth > today) ctx.addIssue({ code: 'custom', path: ['dateOfBirth'], message: 'The date of birth cannot be in the future' });
      if (v.dateOfJoining && v.dateOfBirth && v.dateOfJoining <= v.dateOfBirth) {
        ctx.addIssue({ code: 'custom', path: ['dateOfJoining'], message: 'The date of joining must be after the date of birth' });
      }
    });
}

/** Mirrors CelebrationDtos.SaveImportantDay. */
export const importantDaySchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(150, 'At most 150 characters'),
  date: z.string().min(1, 'Date is required'),
  category: z.enum(['FESTIVAL', 'NATIONAL_DAY', 'INDUSTRY_DAY', 'COMPANY_MILESTONE', 'OTHER']),
  region: z.enum(['AUSTRALIA', 'INDIA', 'GLOBAL']),
  recurrence: z.enum(['YEARLY', 'ONE_OFF']),
  description: z.string().max(2000, 'At most 2000 characters'),
  marketingNotes: z.string().max(5000, 'At most 5000 characters'),
});

const link = z
  .string()
  .trim()
  .max(500, 'At most 500 characters')
  .refine((v) => v === '' || isHttpLink(v), 'Enter a full link (https://…)');

/** Mirrors CelebrationDtos.SaveMarketingEvent; times are entered in the browser's zone and sent as instants. */
export const eventSchema = z
  .object({
    title: z.string().trim().min(1, 'Title is required').max(200, 'At most 200 characters'),
    eventType: z.enum(['UPCOMING_EVENT', 'WEBINAR', 'EVENT_MARKETING', 'EMAIL_MARKETING']),
    status: z.enum(['PLANNED', 'CONFIRMED', 'COMPLETED', 'CANCELLED']),
    startAt: z.string().min(1, 'Start date and time are required'),
    endAt: z.string(),
    location: z.string().trim().max(255, 'At most 255 characters'),
    onlineLink: link,
    registrationLink: link,
    description: z.string().max(5000, 'At most 5000 characters'),
    ownerId: z.string().min(1, 'Choose the owner'),
    emailCampaignId: z.string(),
  })
  .superRefine((v, ctx) => {
    if (v.endAt && v.startAt && v.endAt <= v.startAt) ctx.addIssue({ code: 'custom', path: ['endAt'], message: 'The end must be after the start' });
  });
