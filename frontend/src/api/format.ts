// Times are stored in UTC (timestamptz) and displayed in Sydney time, per the architecture doc.
const SYDNEY = 'Australia/Sydney';

const dateTime = new Intl.DateTimeFormat('en-AU', {
  timeZone: SYDNEY,
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const aud = new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' });
const integer = new Intl.NumberFormat('en-AU');

export const formatDateTime = (iso: string | null | undefined) =>
  iso ? dateTime.format(new Date(iso)) : '—';

export const formatAud = (value: number | null | undefined) =>
  value == null ? '—' : aud.format(value);

export const formatCount = (value: number) => integer.format(value);

export const titleCase = (value: string) =>
  value.charAt(0) + value.slice(1).toLowerCase();
