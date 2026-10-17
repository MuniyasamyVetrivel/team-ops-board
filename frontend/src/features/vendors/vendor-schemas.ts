import { z } from 'zod';

import { safeWebsite } from './vendor-meta';

const text = (max: number) => z.string().trim().max(max, `At most ${max} characters`);

/** Mirrors VendorDtos.MAX_CONTACTS. */
export const MAX_CONTACTS = 20;

/** Mirrors ContractRules.MAX_NOTICE_DAYS. */
export const MAX_NOTICE_DAYS = 730;

/** Mirrors VendorDtos.SaveContact. */
export const contactSchema = z.object({
  id: z.number().nullable(),
  name: z.string().trim().min(1, 'Name is required').max(150, 'At most 150 characters'),
  role: text(100),
  email: z
    .string()
    .trim()
    .max(254, 'At most 254 characters')
    .refine((v) => v === '' || z.email().safeParse(v).success, 'Enter a valid email address'),
  phone: text(50),
});

/** Mirrors VendorDtos.SaveVendor and VendorText (website must be an http or https address). */
export const vendorSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
  taxId: text(50),
  category: z.enum(['SOFTWARE', 'HARDWARE', 'RECRUITMENT_AGENCY', 'MARKETING', 'SERVICES', 'OTHER']),
  country: text(100),
  address: text(500),
  website: text(300).refine((v) => v === '' || safeWebsite(v) !== null, 'Start with http:// or https://'),
  paymentTerms: text(100),
  status: z.enum(['ACTIVE', 'INACTIVE', 'BLACKLISTED']),
  rating: z.enum(['', '1', '2', '3', '4', '5']),
  notes: text(5000),
  contacts: z.array(contactSchema).max(MAX_CONTACTS, `At most ${MAX_CONTACTS} contacts`),
});

export type VendorValues = z.infer<typeof vendorSchema>;

/** Mirrors VendorDtos.SaveContract and ContractRules.checkDates (end and renewal on or after the start). */
export const contractSchema = z
  .object({
    vendorId: z.string().min(1, 'Choose the vendor'),
    title: z.string().trim().min(1, 'Title is required').max(200, 'At most 200 characters'),
    startDate: z.string().min(1, 'Start date is required'),
    endDate: z.string(),
    value: z
      .string()
      .trim()
      .refine((v) => v === '' || (/^\d{1,12}(\.\d{1,2})?$/.test(v.replace(/,/g, ''))), 'Enter an amount with up to two decimals'),
    currency: z
      .string()
      .trim()
      .refine((v) => /^[A-Za-z]{3}$/.test(v), 'Use a three-letter code like INR or AUD'),
    renewalDate: z.string(),
    autoRenew: z.boolean(),
    noticePeriodDays: z
      .string()
      .trim()
      .refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) <= MAX_NOTICE_DAYS), `Enter 0 to ${MAX_NOTICE_DAYS} days`),
    notes: text(5000),
  })
  .refine((v) => !v.endDate || !v.startDate || v.endDate >= v.startDate, { path: ['endDate'], message: 'The end date must be on or after the start date' })
  .refine((v) => !v.renewalDate || !v.startDate || v.renewalDate >= v.startDate, { path: ['renewalDate'], message: 'The renewal date must be on or after the start date' });

export type ContractValues = z.infer<typeof contractSchema>;
