import type { AuthOutcome, CardOption, ReferenceData } from '../types/api';

export interface FormState {
  cardId: string;
  merchantId: string;
  deviceId: string;
  locationId: string;
  amount: string;
  authOutcome: AuthOutcome;
  repeat: number;
}

export interface Scenario {
  key: string;
  label: string;
  aim: string;
  build: (ref: ReferenceData) => FormState;
}

// Presets target the deterministic seed (cards 1001 Alice, 1002 Ben, 1003 Chloe) but
// fall back gracefully if the reference data differs. Outcomes are decided by the
// backend's rule evaluation, never by the UI.
const pickCard = (ref: ReferenceData, cardId: number, index: number): CardOption | undefined =>
  ref.cards.find((c) => c.cardId === cardId) ?? ref.cards[index] ?? ref.cards[0];

const pickMerchant = (ref: ReferenceData, name: string) =>
  String((ref.merchants.find((m) => m.merchantName === name) ?? ref.merchants[0])?.merchantId ?? '');

const pickLocation = (ref: ReferenceData, city: string) =>
  String((ref.locations.find((l) => l.city === city) ?? ref.locations[0])?.locationId ?? '');

export const knownDevice = (ref: ReferenceData, card?: CardOption) =>
  String(card?.knownDeviceIds[0] ?? ref.devices[0]?.deviceId ?? '');

export const unseenDevice = (ref: ReferenceData, card?: CardOption) =>
  ref.devices.find((d) => !card?.knownDeviceIds.includes(d.deviceId))?.deviceId;

const base = (ref: ReferenceData, card: CardOption | undefined): FormState => ({
  cardId: String(card?.cardId ?? ''),
  merchantId: pickMerchant(ref, 'Harbour Grocer'),
  deviceId: knownDevice(ref, card),
  locationId: pickLocation(ref, 'Sydney'),
  amount: '42.50',
  authOutcome: 'APPROVED',
  repeat: 1,
});

export const SCENARIOS: Scenario[] = [
  {
    key: 'normal',
    label: 'Normal purchase',
    aim: 'Small amount on a known device. Expect LOW with no alert.',
    build: (ref) => base(ref, pickCard(ref, 1001, 0)),
  },
  {
    key: 'large',
    label: 'Large amount',
    aim: 'AUD 5,000 on a card with too little history for R2. Aims for R1 alone (MEDIUM).',
    build: (ref) => {
      const card = pickCard(ref, 1003, 2);
      return {
        ...base(ref, card),
        merchantId: pickMerchant(ref, 'Tech Hub Electronics'),
        locationId: pickLocation(ref, 'Brisbane'),
        amount: '5000.00',
      };
    },
  },
  {
    key: 'new-device',
    label: 'New device + large',
    aim: 'AUD 6,200 from a device this card has never used. Aims for R1 + R4 (HIGH).',
    build: (ref) => {
      const card = pickCard(ref, 1003, 2);
      return {
        ...base(ref, card),
        merchantId: pickMerchant(ref, 'Tech Hub Electronics'),
        deviceId: String(unseenDevice(ref, card) ?? knownDevice(ref, card)),
        locationId: pickLocation(ref, 'Melbourne'),
        amount: '6200.00',
      };
    },
  },
  {
    key: 'velocity',
    label: 'Velocity burst',
    aim: 'Five quick attempts on one card. The fifth should match R3.',
    build: (ref) => {
      const card = pickCard(ref, 1002, 1);
      return { ...base(ref, card), merchantId: pickMerchant(ref, 'City Cafe'), amount: '15.00', repeat: 5 };
    },
  },
  {
    key: 'declines',
    label: 'Repeated declines',
    aim: 'Three declined attempts within five minutes. The third should match R5.',
    build: (ref) => {
      const card = pickCard(ref, 1003, 2);
      return {
        ...base(ref, card),
        merchantId: pickMerchant(ref, 'Tech Hub Electronics'),
        locationId: pickLocation(ref, 'Brisbane'),
        amount: '410.00',
        authOutcome: 'DECLINED',
        repeat: 3,
      };
    },
  },
];
