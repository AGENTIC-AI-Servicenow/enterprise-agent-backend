# ARQUITECTURA MVP - FASE 1: AI COPILOT PARA SERVICE DESK

## 📋 ANÁLISIS ESTADO ACTUAL

### Fortalezas Detectadas
- ✅ LLMService con Ollama integrado (local, sin costo de API)
- ✅ OAuth2/ServiceNow integrado (autenticación multi-tenant)
- ✅ Redis para sesiones (contexto multi-usuario)
- ✅ Weaviate listo (base para RAG)
- ✅ Spring Boot 3.5 con WebFlux (reactivo, escalable)
- ✅ OpenTelemetry para observabilidad

### Debilidades Detectadas
- ❌ **AgentService**: Lógica hardcodeada (regex, no productiva)
- ❌ **Sin arquitectura de capas claras** (Controllers llaman directo a services)
- ❌ **Sin contexto de usuario** (falta UserContextFilter)
- ❌ **Sin orquestación de agentes** (single-intent, no multi-step)
- ❌ **Sin validación/guardrails** (security sandbox)
- ❌ **Sin RAG implementado** (Weaviate existe pero no se usa)
- ❌ **Sin observabilidad de IA** (no trackea latencia, tokens, costos)
- ❌ **Sin manejo de errores enterprise** (circuit breakers, retry policies)

---

## 🎯 ARQUITECTURA MVP FASE 1

### Visión General
```
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND (React/Next.js)                 │
│              ServiceNow Portal Integration                   │
└────────┬────────────────────────────────────────────────────┘
         │ REST/WebSocket
         ▼
┌─────────────────────────────────────────────────────────────┐
│                  ENTERPRISE AGENT LAYER                      │
│                                                              │
│  ┌──────────────┐  ┌────────────────┐  ┌──────────────┐    │
│  │ UserContext  │  │  AgentOrch.    │  │ SecurityGate │    │
│  │ Filter       │  │ (Intent→Tools) │  │ (Guardrails) │    │
│  └──────────────┘  └────────────────┘  └──────────────┘    │
│         │                  │                    │            │
│  ┌──────▼──────────────────▼────────────────────▼──────┐   │
│  │           AGENTIC ORCHESTRATOR (Core)               │   │
│  │                                                     │   │
│  │  • Intent Classification (LLM)                      │   │
│  │  • Action Routing                                  │   │
│  │  • Tool Orchestration                              │   │
│  │  • Context Memory Management                       │   │
│  │  • Human-in-the-Loop Decision                      │   │
│  └──────────────────────────────────────────────────────┘  │
│         │              │              │             │       │
└─────────┼──────────────┼──────────────┼─────────────┼───────┘
          │              │              │             │
     ┌────▼────┐  ┌─────▼────┐  ┌──────▼──┐  ┌──────▼────┐
     │ Tools   │  │ Vector   │  │ Context │  │ Monitoring│
     │         │  │ DB RAG   │  │ Memory  │  │ (OTel)    │
     │ -Ticket │  │ (Weav.)  │  │ (Redis) │  │           │
     │ -Search │  │          │  │         │  │ -Metrics  │
     │ -Analyze│  │          │  │         │  │ -Traces   │
     │ -Create │  │          │  │         │  │ -Logs     │
     └────┬────┘  └─────┬────┘  └────┬────┘  └───────────┘
          │             │            │
          └─────────────┼────────────┘
                        │
          ┌─────────────▼──────────────┐
          │    SERVICENOW APIs         │
          │  (OAuth2 authenticated)    │
          │                            │
          │ • Incidents/Tickets        │
          │ • Knowledge Base           │
          │ • Users/Groups             │
          │ • Workflows                │
          └────────────────────────────┘
```

### Componentes Principales

#### 1. **UserContextFilter** (Seguridad + Contexto)
```
Responsabilidad:
- Validar OAuth2 token por request
- Inyectar contexto de usuario en RequestScope
- Obtener access_token de ServiceNow
- Validar permisos (RBAC simple)
- Setear audit trail

Salida: SecurityContext + UserInfo disponible en toda la request
```

#### 2. **AgentOrchestrator** (Core Agéntico)
```
Responsabilidad:
- Recibir input de usuario
- Cargar contexto previo (conversation memory)
- Clasificar intención (via LLMService)
- Mapear intención → acciones permitidas
- Ejecutar acciones en secuencia (stateless)
- Guardar contexto para siguiente request
- Generar respuesta

Patrón: State Machine con LLM como clasificador
Modelo: Ollama (local, sin latencia externa)
Persistencia: Redis (conversa por 24h)
```

#### 3. **ToolRegistry** (Herramientas Seguras)
```
Herramientas Disponibles:

1) SearchIncidents(query, limit=10)
   - Con RAG sobre knowledge base
   - Cached en Weaviate

2) GetIncidentDetails(number)
   - Obtiene ticket completo
   - Con análisis de impacto

3) CreateIncident(title, desc, priority)
   - Con validación de permisos
   - Auto-categorización

4) AnalyzeIncident(number)
   - Genera resumen
   - Sugiere soluciones

5) SuggestResolution(incident_data)
   - Busca en KB similar
   - Con ranking de relevancia

6) GenerateResponseDraft(incident, tone="professional")
   - Redacta respuesta sugerida
   - Necesita approve human

Cada tool:
- Tiene guardrails de entrada (validación)
- Log auditado
- Rate-limited por usuario
- Retry con exponential backoff
```

#### 4. **SecurityGate** (Guardrails)
```
1) Input Validation
   - Max length: 5000 chars
   - No SQL injection patterns
   - No malicious prompts
   
2) Output Validation
   - No PII en respuesta (SSN, email, etc)
   - Sanitizado HTML
   - Max tokens en respuesta: 1000
   
3) Rate Limiting
   - 30 requests/min por usuario
   - 100 concurrent agents max
   
4) Cost Control
   - Ollama local: sin costo
   - Monitor token usage
   - Cache agresivo de resultados comunes
   
5) Human-in-the-Loop
   - Acciones críticas requieren approve:
     * Crear tickets
     * Modificar prioridad alta
     * Cambiar estado crítico
```

---

## 📐 FLUJO DE EJECUCIÓN - CASO DE USO

```
Usuario en ServiceNow Portal:
"¿Cuál es el estado de mi incidente INC0012345 y dame sugerencias?"

┌─ REQUEST ─────────────────────────────────────────┐
│ POST /api/v1/agent/chat                          │
│ {                                                 │
│   "message": "¿Estado INC0012345 + sugerencias?", │
│   "sessionId": "user-123-sess-456"               │
│ }                                                 │
└───────────────────────────────────────────────────┘
                      │
                      ▼
        ┌─────────────────────────────┐
        │ 1. UserContextFilter        │
        │ Valida OAuth token + RBAC   │
        │ Obtiene userContext         │
        └────────┬────────────────────┘
                 │
                 ▼
        ┌─────────────────────────────┐
        │ 2. LoadConversationMemory   │
        │ Redis.get(sessionId)        │
        │ Carga 5 últimos intercambios│
        └────────┬────────────────────┘
                 │
                 ▼
        ┌─────────────────────────────┐
        │ 3. LLMService.classify      │
        │ Prompt: system + context    │
        │ + memoria + input usuario   │
        │ Output: JSON                │
        │ {                           │
        │   "action": "GET_INCIDENT", │
        │   "number": "INC0012345",   │
        │   "next": "SUGGEST_FIX"     │
        │ }                           │
        └────────┬────────────────────┘
                 │
                 ▼
        ┌─────────────────────────────┐
        │ 4. ActionRouter             │
        │ Valida acciones permitidas  │
        │ vs RBAC + guardrails        │
        └────────┬────────────────────┘
                 │
        ┌────────┴──────────┐
        │                   │
        ▼                   ▼
   Tool 1:             Tool 2:
   GetIncident         SuggestFix
   (INC0012345)        (incident)
        │                   │
        │ ServiceNow API    │ Weaviate RAG
        │ OAuth token       │ + LLM
        │                   │
        ▼                   ▼
   Raw incident        Top 3 similar
   data                cases resolved
        │                   │
        └────────┬──────────┘
                 │
                 ▼
        ┌─────────────────────────────┐
        │ 5. BuildResponse            │
        │ Formatea datos              │
        │ LLM redacta respuesta       │
        │ natural + recomendaciones   │
        └────────┬────────────────────┘
                 │
                 ▼
        ┌─────────────────────────────┐
        │ 6. SecurityGate Output      │
        │ Sanitiza PII                │
        │ Valida length               │
        └────────┬────────────────────┘
                 │
                 ▼
        ┌─────────────────────────────┐
        │ 7. SaveConversationMemory   │
        │ Redis.set(sessionId,        │
        │   [...memory, response],    │
        │   ttl=24h)                  │
        └────────┬────────────────────┘
                 │
                 ▼
        ┌─────────────────────────────┐
        │ 8. RecordMetrics            │
        │ OpenTelemetry:              │
        │ • latency                   │
        │ • intent type               │
        │ • tool usage                │
        │ • error rate                │
        └────────┬────────────────────┘
                 │
                 ▼
        ┌──── RESPONSE ───────────────┐
        │ HTTP 200                    │
        │ {                           │
        │   "status": "SUCCESS",      │
        │   "incidentStatus": "...",  │
        │   "suggestions": [          │
        │     {...}, {...}            │
        │   ],                        │
        │   "confidence": 0.92,       │
        │   "nextActions": [...]      │
        │ }                           │
        └─────────────────────────────┘
```

---

## 🔧 ESTRUCTURA DE DIRECTORIOS PROPUESTA

```
src/main/java/com/enterprise/agent/
├── config/
│   ├── AgentConfig.java              (Beans para orquestador)
│   ├── SecurityConfig.java            (OAuth2 + RBAC)
│   ├── WeaviateConfig.java            (Vector DB client)
│   ├── RedisConfig.java               (Session store)
│   └── MonitoringConfig.java          (OpenTelemetry)
│
├── filter/
│   ├── UserContextFilter.java         (Request-scoped context)
│   └── AuditFilter.java               (Audit trail)
│
├── orchestration/
│   ├── AgentOrchestrator.java         (Core - orquesta flujos)
│   ├── ActionRouter.java              (Mapea intención → acciones)
│   └── IntentClassifier.java          (LLM-powered)
│
├── tools/
│   ├── ToolRegistry.java              (Registry centralizado)
│   ├── ServiceNowTool.java            (Base para herramientas)
│   ├── incident/
│   │   ├── SearchIncidentsHandler.java
│   │   ├── GetIncidentHandler.java
│   │   ├── CreateIncidentHandler.java
│   │   └── AnalyzeIncidentHandler.java
│   └── knowledge/
│       ├── SemanticSearchHandler.java (RAG via Weaviate)
│       └── SuggestResolutionHandler.java
│
├── analyzer/
│   ├── IncidentAnalyzer.java          (Análisis de datos)
│   ├── SummaryGenerator.java          (Resumen via LLM)
│   └── ImpactAssessor.java            (Evalúa impacto)
│
├── guardrails/
│   ├── SecurityGate.java              (Input/Output validation)
│   ├── RateLimiter.java               (Token bucket)
│   ├── CostMonitor.java               (Track token usage)
│   └── PiiRedactor.java               (Sanitiza datos)
│
├── memory/
│   ├── ConversationMemory.java        (Session management)
│   ├── ContextStore.java              (Redis backend)
│   └── MemorySerializer.java          (Serialización)
│
├── observable/
│   ├── AgentMetrics.java              (Métricas
