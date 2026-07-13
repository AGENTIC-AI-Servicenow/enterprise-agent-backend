// ============================================================================
// DOMAIN TYPES - ServiceNow & AI Agent
// ============================================================================

export type IncidentState = 'New' | 'In Progress' | 'On Hold' | 'Resolved' | 'Closed';
export type IncidentPriority = 'Critical' | 'High' | 'Medium' | 'Low';
export type IncidentImpact = 'High' | 'Medium' | 'Low';
export type IncidentUrgency = 'High' | 'Medium' | 'Low';

export interface Incident {
  sys_id: string;
  number: string;
  short_description: string;
  description: string;
  state: IncidentState;
  priority: IncidentPriority;
  impact: IncidentImpact;
  urgency: IncidentUrgency;
  assigned_to: string;
  assignment_group: string;
  caller_id: string;
  category: string;
  subcategory: string;
  opened_at: string;
  updated_at: string;
  resolved_at?: string;
  closed_at?: string;
  close_notes?: string;
  work_notes?: string;
}

export interface IncidentSummary {
  total: number;
  byState: Record<IncidentState, number>;
  byPriority: Record<IncidentPriority, number>;
  avgResolutionTime: number;
  openIncidents: number;
}

export interface BriefingTicket {
  number: string;
  shortDescription: string;
  state: string;
  priority: string;
  ageHours: number;
  daysWithoutUpdate: number;
  daysSinceClosed: number;
  slaRisk: boolean;
  complianceWindow: boolean;
  reason: string;
  suggestedAction: string;
  riskRank: number;
  topic: string;
}

export interface BriefingMetrics {
  openTickets: number;
  slaRiskTickets: number;
  stalledTickets: number;
  pendingComplianceTickets: number;
}

export interface BriefingContext {
  technician: string;
  generatedAt: string;
  metrics: BriefingMetrics;
  attentionToday: BriefingTicket[];
  complianceWatch: BriefingTicket[];
  patterns: string[];
}

export interface BriefingResponse {
  summary: string;
  context: BriefingContext;
}

export interface StalledDiagnosis {
  ticketNumber: string;
  shortDescription: string;
  state: string;
  priority: string;
  assignedTo: string;
  caller: string;
  ageHours: number;
  daysWithoutUpdate: number;
  contactAttempts: number;
  stalled: boolean;
  holdReason: string;
  nextAction: string;
  validationFlags: string[];
}

export interface StalledDraft {
  flowId: string;
  draftVersion: number;
  approvalState: string;
  channel: string;
  content: string;
}

export interface StalledTicketResponse {
  diagnosis: StalledDiagnosis;
  draft: StalledDraft;
}

// ============================================================================
// AI AGENT TYPES
// ============================================================================

export type AgentAction = 
  | 'CLASSIFY_TICKET'
  | 'SUMMARIZE_INCIDENT'
  | 'SUGGEST_RESOLUTION'
  | 'SEARCH_KNOWLEDGE'
  | 'DETECT_DUPLICATE'
  | 'PRIORITIZE'
  | 'GENERATE_RESPONSE'
  | 'GENERAL_QUERY';

export interface AgentRequest {
  message: string;
  context?: {
    incidentId?: string;
    userId?: string;
    conversationId?: string;
  };
}

export interface AgentResponse {
  message: string;
  action: AgentAction;
  confidence: number;
  suggestedActions?: SuggestedAction[];
  metadata?: Record<string, any>;
  timestamp: string;
}

export interface SuggestedAction {
  id: string;
  label: string;
  action: string;
  confidence: number;
}

export interface AgentDecision {
  action: AgentAction;
  reasoning: string;
  confidence: number;
  parameters: Record<string, any>;
}

// ============================================================================
// CONVERSATION & MEMORY
// ============================================================================

export interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: string;
  metadata?: {
    action?: AgentAction;
    confidence?: number;
    suggestedActions?: SuggestedAction[];
  };
}

export interface Conversation {
  id: string;
  userId: string;
  messages: Message[];
  createdAt: string;
  updatedAt: string;
  title?: string;
}

// ============================================================================
// AUTH & USER
// ============================================================================

export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  role: 'admin' | 'analyst' | 'user';
  avatar?: string;
}

export interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  accessToken?: string;
}

// ============================================================================
// API RESPONSES
// ============================================================================

export interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  error?: string;
  message?: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  hasNext: boolean;
}

// ============================================================================
// UI STATE
// ============================================================================

export interface Toast {
  id: string;
  title: string;
  description?: string;
  variant?: 'default' | 'destructive' | 'success';
  duration?: number;
}
