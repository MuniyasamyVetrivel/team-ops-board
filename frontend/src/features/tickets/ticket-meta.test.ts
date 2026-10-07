import { describe, expect, it } from 'vitest';

import { slaStatus } from '@/test/ticket-fixtures';

import { formatMinutes, slaText, ticketHistoryLabel, ticketHistoryValue } from './ticket-meta';

describe('formatMinutes', () => {
  it('uses the largest sensible units', () => {
    expect(formatMinutes(0)).toBe('0m');
    expect(formatMinutes(45)).toBe('45m');
    expect(formatMinutes(60)).toBe('1h');
    expect(formatMinutes(200)).toBe('3h 20m');
    expect(formatMinutes(1440)).toBe('1d');
    expect(formatMinutes(2880 + 240)).toBe('2d 4h');
  });

  it('formats negative (overdue) values by size', () => {
    expect(formatMinutes(-35)).toBe('35m');
  });
});

describe('slaText', () => {
  it('describes open, overdue, paused and completed deadlines', () => {
    expect(slaText(slaStatus({ remainingMinutes: 200 }))).toBe('3h 20m left');
    expect(slaText(slaStatus({ state: 'BREACHED', remainingMinutes: -35 }))).toBe('Overdue by 35m');
    expect(slaText(slaStatus({ paused: true, remainingMinutes: 120 }))).toBe('Paused · 2h left');
    expect(slaText(slaStatus({ met: true, remainingMinutes: 20 }))).toBe('Met');
    expect(slaText(slaStatus({ met: false, state: 'BREACHED', remainingMinutes: -90 }))).toBe('Missed by 1h 30m');
  });
});

describe('ticket history', () => {
  it('labels fields and values', () => {
    expect(ticketHistoryLabel('first response')).toBe('sent the first response');
    expect(ticketHistoryLabel('custom')).toBe('custom');
    expect(ticketHistoryValue('WAITING_FOR_REQUESTER')).toBe('Waiting for requester');
    expect(ticketHistoryValue('URGENT')).toBe('Urgent');
    expect(ticketHistoryValue(null)).toBe('—');
  });
});
