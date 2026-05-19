import type { 
  AgentRequest, 
  AgentResponse, 
  Incident, 
  IncidentSummary,
  ApiResponse 
} from '@/types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public data?: any
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function fetchApi<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const userId = typeof window !== 'undefined'
    ? localStorage.getItem('user_id')
    : null;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  if (userId) {
    headers['X-User-Id'] = userId;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      ...headers,
      ...options.headers,
    },
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new ApiError(
      response.status,
      errorData.message || `HTTP ${response.status}: ${response.statusText}`,
      errorData
    );
  }

  return response.json();
}

// ============================================================================
// AI AGENT API
// ============================================================================

export const agentApi = {
  /**
   * Chat with the agent.
   *
   * Uses /api/agent/chat as the canonical endpoint.
   * If the backend is running in "MVP/test" mode, it may also expose /api/agent/test.
   * We keep a transparent fallback so the UI works across environments.
   */
  async chat(request: AgentRequest): Promise<AgentResponse> {
    /**
     * Backend expects the "ServiceNow widget" shape:
     * {
     *   message: string,
     *   sessionId?: string,
     *   user?: { sys_id: string, username?: string, ... }
     * }
     *
     * Our frontend uses a simplified AgentRequest shape:
     * { message, context?: { conversationId? } }
     *
     * For local web UI we auto-inject a "dev user" so /api/agent/chat works.
     * In real ServiceNow integration, the widget will pass the real user context.
     */
    const sessionId =
      (request as any)?.context?.conversationId ||
      (request as any)?.sessionId ||
      undefined;

    const payload = {
      message: request.message,
      sessionId,
      user: {
        sys_id: 'web-ui-dev-user',
        username: 'web.ui',
        email: 'web.ui@local',
        fullName: 'Web UI Dev User',
        roles: ['user', 'analyst'],
      },
    };

    try {
      return await fetchApi<AgentResponse>('/api/agent/chat', {
        method: 'POST',
        body: JSON.stringify(payload),
      });
    } catch (e) {
      // Fallback: older/local endpoint used in this repo for development (Ollama/local).
      // This fallback can be disabled via Settings: ea.settings.ollamaEnabled=false
      const ollamaEnabled =
        typeof window !== "undefined"
          ? localStorage.getItem("ea.settings.ollamaEnabled")
          : null;

      const allowFallback = ollamaEnabled === null ? true : ollamaEnabled === "true";

      if (
        allowFallback &&
        e instanceof ApiError &&
        (e.status === 404 || e.status === 405)
      ) {
        return fetchApi<AgentResponse>('/api/agent/test', {
          method: 'POST',
          body: JSON.stringify({ message: request.message, sessionId }),
        });
      }
      throw e;
    }
  },

  async test(request: AgentRequest): Promise<AgentResponse> {
    return fetchApi<AgentResponse>('/api/agent/test', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  async getConversationHistory(conversationId: string) {
    return fetchApi(`/api/agent/conversations/${conversationId}`);
  },
};

// ============================================================================
// INCIDENTS API
// ============================================================================

export const incidentsApi = {
  async getAll(params?: { 
    state?: string; 
    priority?: string; 
    limit?: number;
    offset?: number;
  }): Promise<ApiResponse<Incident[]>> {
    const queryParams = new URLSearchParams();
    if (params?.state) queryParams.append('state', params.state);
    if (params?.priority) queryParams.append('priority', params.priority);
    if (params?.limit) queryParams.append('limit', params.limit.toString());
    if (params?.offset) queryParams.append('offset', params.offset.toString());

    const query = queryParams.toString();
    return fetchApi<ApiResponse<Incident[]>>(
      `/api/incidents${query ? `?${query}` : ''}`
    );
  },

  async getById(id: string): Promise<ApiResponse<Incident>> {
    return fetchApi<ApiResponse<Incident>>(`/api/incidents/${id}`);
  },

  async getSummary(): Promise<ApiResponse<IncidentSummary>> {
    return fetchApi<ApiResponse<IncidentSummary>>('/api/incidents/summary');
  },

  async create(data: Partial<Incident>): Promise<ApiResponse<Incident>> {
    return fetchApi<ApiResponse<Incident>>('/api/incidents', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  async update(
    id: string, 
    data: Partial<Incident>
  ): Promise<ApiResponse<Incident>> {
    return fetchApi<ApiResponse<Incident>>(`/api/incidents/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },
};

// ============================================================================
// AUTH API
// ============================================================================

export const authApi = {
  async login(username: string, password: string) {
    return fetchApi('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
  },

  async logout() {
    return fetchApi('/api/auth/logout', {
      method: 'POST',
    });
  },

  async getCurrentUser() {
    return fetchApi('/api/auth/me');
  },

  async initiateOAuth() {
    return fetchApi<{ authorizationUrl: string }>('/api/oauth/authorize');
  },

  async handleCallback(code: string) {
    return fetchApi('/api/oauth/callback', {
      method: 'POST',
      body: JSON.stringify({ code }),
    });
  },
};

// ============================================================================
// HEALTH CHECK
// ============================================================================

export const healthApi = {
  async check(): Promise<{ status: string; timestamp: string }> {
    return fetchApi('/actuator/health');
  },
};

export { ApiError };
