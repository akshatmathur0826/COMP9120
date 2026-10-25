// Mirrors the Spring Boot DTO records in backend/src/main/java/com/frauddetection/dto.
// Timestamps arrive as ISO-8601 strings (UTC); NUMERIC values arrive as JSON numbers.

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';
export type AlertStatus = 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'DISMISSED';
export type AuthOutcome = 'APPROVED' | 'DECLINED';

export interface TransactionSummary {
  transactionId: number;
  cardId: number;
  merchantName: string;
  amount: number;
  occurredAt: string;
  authOutcome: AuthOutcome;
  score: number | null;
  riskLevel: RiskLevel | null;
}

export interface MatchedRule {
  ruleCode: string;
  title: string;
  observedValue: number | null;
  explanation: string;
}

export interface TransactionDetail {
  transactionId: number;
  requestId: string;
  cardId: number;
  cardToken: string;
  accountNumber: string;
  customerName: string;
  merchantName: string;
  merchantCategory: string;
  deviceFingerprint: string;
  city: string;
  countryCode: string;
  amount: number;
  occurredAt: string;
  authOutcome: AuthOutcome;
  assessmentId: number | null;
  score: number | null;
  riskLevel: RiskLevel | null;
  ruleSetVersion: string | null;
  alertId: number | null;
  alertStatus: AlertStatus | null;
  matchedRules: MatchedRule[];
}

export interface CreateTransactionRequest {
  requestId: string;
  cardId: number;
  merchantId: number;
  deviceId: number;
  locationId: number;
  amount: number;
  occurredAt: string;
  authOutcome: AuthOutcome;
}

export interface TransactionSubmissionResponse {
  transactionId: number;
  assessmentId: number;
  score: number;
  riskLevel: RiskLevel;
  matchedRules: string[];
  alertId: number | null;
}

export interface AlertSummary {
  alertId: number;
  transactionId: number;
  customerName: string;
  merchantName: string;
  amount: number;
  score: number;
  riskLevel: RiskLevel;
  status: AlertStatus;
  assignedAnalyst: string | null;
  createdAt: string;
}

export interface AlertDetail {
  alertId: number;
  transactionId: number;
  customerName: string;
  merchantName: string;
  amount: number;
  occurredAt: string;
  authOutcome: AuthOutcome;
  score: number;
  riskLevel: RiskLevel;
  ruleSetVersion: string;
  status: AlertStatus;
  assignedAnalyst: string | null;
  createdAt: string;
  reviewedAt: string | null;
  analystNote: string | null;
  matchedRules: MatchedRule[];
}

export interface AlertReview {
  reviewId: number;
  changedBy: string;
  changedAt: string;
  oldStatus: AlertStatus;
  newStatus: AlertStatus;
  note: string | null;
}

export interface UpdateAlertStatusRequest {
  newStatus: Exclude<AlertStatus, 'OPEN'>;
  analystId: number;
  note?: string;
}

export interface CardOption {
  cardId: number;
  cardToken: string;
  cardStatus: string;
  accountNumber: string;
  accountStatus: string;
  customerName: string;
  knownDeviceIds: number[];
}

export interface ReferenceData {
  cards: CardOption[];
  merchants: { merchantId: number; merchantName: string; category: string }[];
  devices: { deviceId: number; deviceFingerprint: string }[];
  locations: { locationId: number; city: string; countryCode: string }[];
  analysts: { analystId: number; displayName: string }[];
  rules: {
    ruleId: number;
    ruleCode: string;
    title: string;
    description: string;
    severityWeight: number;
    active: boolean;
    ruleSetVersion: string;
  }[];
}

export interface Dashboard {
  transactionCount: number;
  declinedCount: number;
  totalAmount: number;
  riskLevelCounts: Record<RiskLevel, number>;
  alertStatusCounts: Record<AlertStatus, number>;
  rulePerformance: {
    ruleCode: string;
    title: string;
    severityWeight: number;
    matchCount: number;
    linkedAlertCount: number;
  }[];
  analystWorkload: {
    analystId: number;
    displayName: string;
    activeAlerts: number;
    resolvedAlerts: number;
    dismissedAlerts: number;
  }[];
  highRiskMerchants: {
    merchantId: number;
    merchantName: string;
    category: string;
    highRiskCount: number;
    highRiskAmount: number;
  }[];
}

export interface ApiErrorBody {
  message: string;
  errors?: Record<string, string>;
}

// Transaction demo page (/api/demo)
export interface TableCounts {
  transactions: number;
  assessments: number;
  ruleMatches: number;
  alerts: number;
}

export interface ForcedRollbackResult {
  requestId: string;
  cardId: number;
  failureMessage: string;
  before: TableCounts;
  after: TableCounts;
  requestStored: boolean;
}

export interface HeldLockResult {
  submission: TransactionSubmissionResponse;
  lockWaitMillis: number;
  totalMillis: number;
  lockAcquiredAt: string;
}

export interface RecentAttempts {
  cardId: number;
  windowSeconds: number;
  minimumEventCount: number;
  attempts: number;
}
