# Enterprise Agent - Arquitectura MVP Fase 1

## 📋 Tabla de Contenidos

1. [Resumen Ejecutivo](#resumen-ejecutivo)
2. [Arquitectura General](#arquitectura-general)
3. [Stack Tecnológico](#stack-tecnológico)
4. [Componentes del Sistema](#componentes-del-sistema)
5. [Flujos de Integración](#flujos-de-integración)
6. [Seguridad y Autenticación](#seguridad-y-autenticación)
7. [Estado Actual de Implementación](#estado-actual-de-implementación)
8. [Próximos Pasos](#próximos-pasos)
9. [Roadmap Fase 2](#roadmap-fase-2)
10. [Decisiones Técnicas y Trade-offs](#decisiones-técnicas-y-trade-offs)

---

## 🎯 Resumen Ejecutivo

**Enterprise Agent** es una capa de inteligencia artificial enterprise que aumenta las capacidades de ServiceNow mediante automatización inteligente y agentes autónomos.

### Objetivos del MVP (Fase 1)

- ✅ **AI Copilot para Service Desk**: Asistencia inteligente para analistas
- ✅ **Clasificación automática de tickets**
- ✅ **Resumen inteligente de incidentes**
- ✅ **Sugerencias de resolución basadas en IA**
- ✅ **Búsqueda semántica en knowledge base**
- ✅ **Detección de duplicados**
- ✅ **Interfaz moderna y responsive**

### Valor de Negocio

1. **Reducción de carga operativa**: Automatización de tareas repetitivas
2. **Mejora en tiempo de resolución**: Sugerencias contextuales inteligentes
3. **Consistencia en respuestas**: Basadas en knowledge base corporativa
4. **Escalabilidad**: Preparado para evolucionar a agentes autónomos

---

## 🏗️ Arquitectura General

### Diagrama de Alto Nivel

```
┌─────────────────────────────────────────────────────────────────┐
│                         FRONTEND (Next.js 14)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────┐   │
│  │  Dashboard   │  │  Incidents   │  │   AI Agent Chat     │   │
│  │  Analytics   │  │  Management  │  │   Copilot Interface │   │
│  └──────────────┘  └──────────────┘  └─────────────────────┘   │
└────────────────────────────────┬────────────────────────────────┘
                                 │ REST API
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    BACKEND (Spring Boot 3.x)                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Agent Orchestration Layer              │   │
│  │  ┌────────────┐  ┌────────────┐  ┌──────────────────┐   │   │
│  │  │ LLM Service│  │Tool Registry│  │ Memory Manager   │   │   │
│  │  │  (OpenAI)  │  │ & Router   │  │ (Conversation)   │   │   │
│  │  └────────────┘  └────────────┘  └──────────────────┘   │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                 ServiceNow Integration Layer              │   │
│  │  ┌────────────┐  ┌────────────┐  ┌──────────────────┐   │   │
│  │  │ OAuth 2.0  │  │REST Client │  │  Incident API    │   │   │
│  │  │   Flow     │  │  (Reactive)│  │    Wrapper       │   │   │
│  │  └────────────┘  └────────────┘  └──────────────────┘   │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────────────┬────────────────────────────────┘
                                 │ OAuth 2.0 + REST API
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                      ServiceNow Instance                         │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────┐   │
│  │   Incident   │  │   Knowledge  │  │    Configuration    │   │
│  │   Management │  │     Base     │  │     Management      │   │
│  └──────────────┘  └──────────────┘  └─────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Principios de Arquitectura

1. **Separation of Concerns**: Capas bien definidas (UI, Orchestration, Integration)
2. **Reactive & Non-blocking**: WebClient para llamadas asíncronas
3. **Stateless Backend**: Conversación en memoria (temporal para MVP)
4. **API-First**: Diseño basado en contratos REST
5. **Security by Design**: OAuth 2.0, CORS configurado, secrets en variables de entorno

---

## 🛠️ Stack Tecnológico

### Backend

```yaml
Framework: Spring Boot 3.4.1
Lenguaje: Java 21
Reactive Stack: Spring WebFlux + WebClient
IA Integration: OpenAI API (GPT-4)
Build Tool: Maven 3.9.9
```

**Dependencias Clave:**
- `spring-boot-starter-webflux`: Programación reactiva
- `spring-boot-starter-security`: Seguridad y OAuth
- `spring-boot-starter-validation`: Validación de datos
- `lombok`: Reducción de boilerplate
- `jackson-databind`: Serialización JSON

### Frontend

```yaml
Framework: Next.js 14.2.3 (App Router)
Lenguaje: TypeScript 5.x
UI Library: React 18
Styling: Tailwind CSS 3.x
State Management: React Query (TanStack Query)
HTTP Client: Fetch API
```

**Dependencias Clave:**
- `@tanstack/react-query`: Cache y sincronización de servidor
- `next-themes`: Dark mode enterprise
- `tailwindcss`: Utility-first CSS
- `typescript`: Type safety

### Infraestructura (Preparada)

```yaml
Containerization: Docker + Docker Compose
CI/CD: GitHub Actions (preparado)
Cloud Target: AWS / Azure / GCP (cloud-agnostic)
```

---

## 🧩 Componentes del Sistema

### Backend Components

#### 1. Agent Service Layer

```java
@Service
public class AgentService {
    // Orchestrates agent decisions
    // Manages conversation flow
    // Routes to appropriate tools
}
```

**Responsabilidades:**
- Interpretar intent del usuario
- Seleccionar herramientas apropiadas
- Mantener contexto de conversación
- Formatear respuestas enriquecidas

#### 2. LLM Service

```java
@Service
public class LLMService {
    // Integrates with OpenAI
    // Manages prompts and function calling
    // Handles token optimization
}
```

**Capabilities:**
- Function calling para tool selection
- System prompts optimizados por dominio
- Control de temperatura y max_tokens
- Manejo de rate limits

#### 3. Tool Registry & Router

```java
public interface AgentTool {
    String getName();
    String getDescription();
    Map<String, Object> execute(Map<String, Object> params);
}

@Component
public class ToolRegistry {
    // Dynamic tool registration
    // Parameter validation
}
```

**Herramientas Implementadas:**
- `search_incidents`: Buscar tickets en ServiceNow
- `get_incident_details`: Obtener detalles completos
- `search_knowledge`: Buscar en knowledge base
- `summarize_incident`: Generar resúmenes inteligentes
- `suggest_resolution`: Proponer soluciones basadas en histórico

#### 4. ServiceNow Integration

```java
@Service
public class ServiceNowClient {
    // OAuth 2.0 token management
    // REST API wrapper
    // Error handling & retries
}
```

**Features:**
- Token refresh automático
- Rate limiting y exponential backoff
- Logging de auditoría
- Circuit breaker pattern (preparado)

#### 5. Conversation Memory

```java
@Component
public class ConversationMemory {
    // In-memory conversation storage
    // Context window management
    // User session tracking
}
```

**Estado Actual:**
- ⚠️ **MVP**: Memoria en RAM (se pierde al reiniciar)
- 🎯 **Producción**: Redis o base de datos persistente

### Frontend Components

#### 1. Layout & Navigation

- **Sidebar**: Navegación principal
- **Header**: Usuario, notificaciones, theme toggle
- **Responsive**: Mobile-first design

#### 2. Dashboard

```typescript
// Métricas en tiempo real
- Total de incidents
- Incidents por prioridad
- Tendencias de resolución
- Indicadores de performance
```

#### 3. Incident Management

```typescript
// CRUD completo de incidents
- Lista paginada y filtrable
- Vista de detalles
- Edición inline
- Estados y prioridades con badges
```

#### 4. AI Agent Chat

```typescript
// Interface conversacional
- Chat con streaming (preparado)
- Markdown rendering
- Code syntax highlighting
- Tool execution feedback
- Context-aware suggestions
```

#### 5. Hooks & State Management

```typescript
// React Query hooks
- useIncidents(): Lista y cache
- useIncidentDetails(): Detalles con refetch
- useAgent(): Chat con optimistic updates
- useAuth(): Autenticación y sesión
```

---

## 🔄 Flujos de Integración

### Flujo de Autenticación OAuth 2.0

```
┌──────────┐                ┌──────────┐                ┌────────────┐
│ Frontend │                │ Backend  │                │ ServiceNow │
└────┬─────┘                └────┬─────┘                └─────┬──────┘
     │                           │                            │
     │ 1. Click "Login"          │                            │
     ├──────────────────────────>│                            │
     │                           │                            │
     │ 2. Redirect to OAuth      │                            │
     │<──────────────────────────┤                            │
     │                           │                            │
     │ 3. User authorizes        │                            │
     ├───────────────────────────┼───────────────────────────>│
     │                           │                            │
     │ 4. Callback with code     │                            │
     │<──────────────────────────┼────────────────────────────┤
     │                           │                            │
     │ 5. Exchange code          │                            │
     ├──────────────────────────>│ 6. Exchange code for token│
     │                           ├───────────────────────────>│
     │                           │                            │
     │                           │ 7. Return access token     │
     │ 8. Return token to client │<───────────────────────────┤
     │<──────────────────────────┤                            │
     │                           │                            │
     │ 9. Store token & redirect │                            │
     │   to dashboard            │                            │
     └───────────────────────────┴────────────────────────────┘
```

### Flujo de Interacción con AI Agent

```
┌──────────┐         ┌──────────┐         ┌─────────┐         ┌────────────┐
│  User    │         │ Frontend │         │ Backend │         │ ServiceNow │
└────┬─────┘         └────┬─────┘         └────┬────┘         └─────┬──────┘
     │                    │                    │                     │
     │ 1. "Muéstrame      │                    │                     │
     │    incidentes      │                    │                     │
     │    críticos"       │                    │                     │
     ├───────────────────>│                    │                     │
     │                    │ 2. POST /agent/chat│                     │
     │                    ├───────────────────>│                     │
     │                    │                    │ 3. LLM analyzes     │
     │                    │                    │    intent           │
     │                    │                    │                     │
     │                    │                    │ 4. Decides tool:    │
     │                    │                    │   search_incidents  │
     │                    │                    │                     │
     │                    │                    │ 5. GET /incidents   │
     │                    │                    ├────────────────────>│
     │                    │                    │                     │
     │                    │                    │ 6. Return incidents │
     │                    │                    │<────────────────────┤
     │                    │                    │                     │
     │                    │                    │ 7. LLM formats      │
     │                    │                    │    response         │
     │                    │                    │                     │
     │                    │ 8. Response with   │                     │
     │                    │    tool results    │                     │
     │ 9. Render response │<───────────────────┤                     │
     │    with data       │                    │                     │
     │<───────────────────┤                    │                     │
     └────────────────────┴────────────────────┴─────────────────────┘
```

### Flujo de Tool Calling (Function Calling)

```yaml
1. User Input: "Resume el incidente INC0012345"
   ↓
2. LLM Analysis:
   - Identifica intent: "obtener resumen"
   - Extrae parámetros: { incident_id: "INC0012345" }
   - Selecciona tool: "get_incident_details"
   ↓
3. Tool Execution:
   - Router invoca ServiceNowClient
   - GET /api/now/table/incident/{id}
   - Obtiene datos del ticket
   ↓
4. LLM Synthesis:
   - Procesa datos estructurados
   - Genera resumen en lenguaje natural
   - Identifica patterns y recomendaciones
   ↓
5. Response Formatting:
   - Markdown con secciones
   - Highlights de información crítica
   - Sugerencias de next steps
   ↓
6. UI Rendering:
   - Parsed markdown
   - Badges para prioridad/estado
   - Action buttons contextuales
```

---

## 🔒 Seguridad y Autenticación

### Capa de Seguridad Backend

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    // OAuth 2.0 configuration
    // CORS policy
    // Rate limiting
}
```

**Implementado:**
- ✅ OAuth 2.0 Authorization Code Flow
- ✅ Token storage seguro (no en código)
- ✅ CORS configurado para frontend
- ✅ Variables de entorno para secrets
- ✅ HTTPS ready (SSL/TLS en producción)

**Pendiente para Producción:**
- ⏳ Token refresh automático
- ⏳ Session management con Redis
- ⏳ Rate limiting por usuario/IP
- ⏳ Audit logging completo
- ⏳ WAF integration

### Secrets Management

```yaml
# application.yml (template)
servicenow:
  instance-url: ${SERVICENOW_INSTANCE_URL}
  client-id: ${SERVICENOW_CLIENT_ID}
  client-secret: ${SERVICENOW_CLIENT_SECRET}

openai:
  api-key: ${OPENAI_API_KEY}
```

**Best Practices:**
- Nunca commitear secrets al repositorio
- Usar variables de entorno
- Rotar credenciales regularmente
- Principio de menor privilegio

---

## 📊 Estado Actual de Implementación

### ✅ Completado (MVP Funcional)

#### Backend
- [x] Estructura Spring Boot con capas bien definidas
- [x] Integración OAuth 2.0 con ServiceNow
- [x] Cliente REST reactivo con WebClient
- [x] Servicio LLM con OpenAI
- [x] Agent orchestration layer
- [x] Tool registry y router dinámico
- [x] Conversation memory (in-memory)
- [x] Controllers REST para todos los endpoints
- [x] DTOs y modelos de dominio
- [x] Manejo de errores centralizado
- [x] Logging estructurado
- [x] CORS configurado

#### Frontend
- [x] Proyecto Next.js 14 con TypeScript
- [x] Sistema de diseño con Tailwind CSS
- [x] Dark mode enterprise
- [x] Componentes UI reutilizables
- [x] Dashboard con métricas
- [x] Lista de incidents con filtros
- [x] Vista detalle de incident
- [x] Chat interface con AI agent
- [x] Hooks React Query para API
- [x] Hook de autenticación
- [x] Layout responsive
- [x] Type safety completo
- [x] Build exitoso sin errores

#### DevOps
- [x] Dockerfiles para backend y frontend
- [x] Docker Compose para desarrollo local
- [x] README con instrucciones
- [x] .env.example con variables requeridas
- [x] .gitignore configurado

### ⏳ Pendiente (Post-MVP)

#### Features
- [ ] Streaming responses del LLM
- [ ] Detección de duplicados con embeddings
- [ ] Búsqueda semántica en knowledge base
- [ ] Clasificación automática de prioridad
- [ ] Sugerencias de asignación inteligente
- [ ] Análisis de sentiment
- [ ] Predicción de tiempo de resolución

#### Infraestructura
- [ ] Base de datos para persistencia (PostgreSQL)
- [ ] Redis para cache y sesiones
- [ ] Vector database (Pinecone/Weaviate/Qdrant)
- [ ] Message broker (RabbitMQ/Kafka)
- [ ] Monitoring con Prometheus + Grafana
- [ ] Distributed tracing (Jaeger/Zipkin)
- [ ] CI/CD pipeline completo
- [ ] Kubernetes manifests

#### Seguridad
- [ ] Token refresh automático
- [ ] Rate limiting avanzado
- [ ] API key rotation
- [ ] Audit logging completo
- [ ] Encryption at rest
- [ ] Security headers
- [ ] Penetration testing

---

## 🚀 Próximos Pasos

### Sprint 1: Refinamiento MVP (2 semanas)

**Objetivos:**
1. Implementar streaming responses
2. Mejorar prompts del LLM
3. Agregar más herramientas al tool registry
4. Testing end-to-end
5. Documentación de API

**Tareas Backend:**
```java
// 1. Streaming SSE endpoint
@GetMapping(value = "/agent/chat/stream", produces = "text/event-stream")
public Flux<ServerSentEvent<String>> chatStream(@RequestBody AgentRequest request)

// 2. Nuevas herramientas
- assign_incident: Asignar automáticamente
- escalate_incident: Escalar basado en reglas
- create_incident: Crear desde descripción natural

// 3. Mejor gestión de contexto
- Implementar sliding window
- Compresión de contexto largo
- Resumen de conversaciones previas
```

**Tareas Frontend:**
```typescript
// 1. Streaming UI
- EventSource o fetch con streaming
- Typewriter effect para responses
- Cancel button para streaming

// 2. Mejoras UX
- Loading skeletons
- Toast notifications
- Error boundaries
- Offline mode

// 3. Analytics
- Track user interactions
- Measure response times
- A/B testing de prompts
```

### Sprint 2: Persistencia y Scale (2 semanas)

**Objetivos:**
1. Migrar memoria a Redis
2. Implementar base de datos PostgreSQL
3. Configurar vector database
4. Deploy en cloud (staging)

**Arquitectura de Datos:**
```yaml
PostgreSQL:
  - user_sessions
  - conversation_history
  - agent_decisions
  - audit_logs

Redis:
  - active_sessions (TTL: 30m)
  - rate_limiting_counters
  - cache de incidents

Vector Database:
  - embeddings de knowledge base
  - embeddings de incidents históricos
  - embeddings de resoluciones exitosas
```

### Sprint 3: Features Avanzadas (3 semanas)

**1. Búsqueda Semántica**
```python
# Flujo de RAG (Retrieval Augmented Generation)
1. User query → embedding
2. Vector search en knowledge base
3. Top-K documentos relevantes
4. Inject en contexto LLM
5. Generate response
```

**2. Detección de Duplicados**
```python
# Similarity search
1. New incident → embedding
2. Cosine similarity con histórico
3. Threshold > 0.85 → potential duplicate
4. Suggest merge o link
```

**3. Auto-clasificación**
```java
// ML model para clasificar
- Category (Hardware, Software, Network...)
- Priority (1-5)
- Urgency (High, Medium, Low)
- Assignment group prediction
```

---

## 🎯 Roadmap Fase 2: Agentes Autónomos

### Visión General

Evolucionar de **Copilot** (human-in-the-loop) a **Agentes Autónomos** (task execution).

### Arquitectura Multiagente

```
┌─────────────────────────────────────────────────────────────────┐
│                     Orchestrator Agent                           │
│              (Planner, Router, Supervisor)                       │
└────────────────────┬────────────────────────────────────────────┘
                     │
         ┌───────────┴───────────┬───────────────┬────────────┐
         ▼                       ▼               ▼            ▼
┌─────────────────┐  ┌─────────────────┐  ┌──────────┐  ┌────────┐
│ Incident Agent  │  │ Research Agent  │  │ Fix Agent│  │KB Agent│
│                 │  │                 │  │          │  │        │
│ - Classify      │  │ - Search KB     │  │ - Execute│  │- Update│
│ - Assign        │  │ - Find similar  │  │ - Verify │  │- Create│
│ - Escalate      │  │ - Extract info  │  │ - Rollback│  │- Tag   │
└─────────────────┘  └─────────────────┘  └──────────┘  └────────┘
```

### Capabilities por Agente

#### 1. Orchestrator Agent
```yaml
Responsibilities:
  - Task decomposition
  - Agent selection & routing
  - Workflow coordination
  - Human approval gates
  - Error recovery

Tools:
  - create_plan()
  - delegate_task()
  - await_approval()
  - rollback_actions()
```

#### 2. Incident Agent
```yaml
Responsibilities:
  - Auto-triage incidents
  - Smart assignment
  - SLA monitoring
  - Escalation logic

Tools:
  - classify_incident()
  - assign_to_group()
  - update_priority()
  - escalate_if_needed()
```

#### 3. Research Agent
```yaml
Responsibilities:
  - Search knowledge base
  - Find similar resolved incidents
  - Extract solutions
  - Validate applicability

Tools:
  - semantic_search()
  - find_duplicates()
  - extract_solution()
  - validate_fix()
```

#### 4. Fix Agent
```yaml
Responsibilities:
  - Execute remediation
  - Run scripts/playbooks
  - Validate results
  - Rollback on failure

Tools:
  - execute_script()
  - restart_service()
  - check_status()
  - rollback()
```

### Guardrails & Safety

```yaml
Approval Gates:
  - High risk actions require human approval
  - Low risk actions auto-execute with audit
  - Critical changes need 2-factor confirmation

Risk Classification:
  Low Risk:
    - Read operations
    - Status checks
    - Report generation
  
  Medium Risk:
    - Update ticket fields
    - Assign incidents
    - Send notifications
  
  High Risk:
    - Execute scripts
    - Modify configurations
    - Delete resources

Circuit Breakers:
  - Max retries: 3
  - Timeout: 5 minutes per action
  - Fallback: Escalate to human
  - Rate limiting per agent
```

### Observability Agéntica

```yaml
Metrics:
  - Actions per agent
  - Success/failure rate
  - Average resolution time
  - Human intervention rate
  - Cost per action (tokens/API calls)

Tracing:
  - Distributed tracing per task
  - Agent decision logs
  - Tool execution traces
  - Error propagation

Dashboards:
  - Real-time agent activity
  - Task completion funnel
  - Performance heatmaps
  - Cost analytics
```

---

## 💡 Decisiones Técnicas y Trade-offs

### 1. Spring WebFlux vs Spring MVC

**Decisión:** WebFlux (Reactive)

**Razón:**
- Non-blocking I/O para llamadas a ServiceNow
- Mejor manejo de concurrencia
- Preparado para streaming
- Escalabilidad horizontal

**Trade-off:**
- Curva de aprendizaje más alta
- Debugging más complejo
- Menos librerías compatibles

### 2. In-Memory vs Redis para Memoria

**Decisión MVP:** In-Memory

**Razón:**
- Simplicidad para validar concepto
- Sin dependencias externas
- Deploy más rápido

**Trade-off:**
- Se pierde estado al reiniciar
- No escalable horizontalmente
- No compartido entre instancias

**Próximo paso:** Redis

### 3. OpenAI vs Modelos Locales

**Decisión MVP:** OpenAI

**Razón:**
- API estable y documentada
- Function calling robusto
- Menor latencia
- Sin infraestructura GPU

**Trade-off:**
- Costo por token
- Dependencia externa
- Data privacy concerns
- Vendor lock-in

**Futuro:** Hybrid approach (local + cloud)

### 4. Next.js vs Create React App

**Decisión:** Next.js 14

**Razón:**
- SSR/SSG capabilities
- App Router moderno
- Built-in optimizations
- TypeScript first-class
- API routes integradas

**Trade-off:**
- Bundle size más grande
- Complejidad inicial mayor
- Vendor-specific patterns

### 5. Monorepo vs Separate Repos

**Decisión MVP:** Separate repos

**Razón:**
- Desacoplamiento
- Deploy independiente
- Teams diferentes
- CI/CD más simple

**Futuro:** Considerar monorepo con Nx/Turborepo

### 6. REST vs GraphQL

**Decisión:** REST

**Razón:**
- ServiceNow usa REST
- Más simple para MVP
- Caching HTTP estándar
- Tooling maduro

**Futuro:** GraphQL gateway para agregación

---

## 📈 Métricas de Éxito

### KPIs Técnicos

```yaml
Performance:
  - API response time: < 200ms (p95)
  - LLM response time: < 3s (p95)
  - Frontend load time: < 2s
  - Uptime: > 99.5%

Quality:
  - Test coverage: > 80%
  - TypeScript errors: 0
  - Security vulnerabilities: 0 critical
  - Code quality: SonarQube A rating

Scalability:
  - Concurrent users: 100+
  - Requests/second: 1000+
  - Database connections: Pooled
  - Cache hit rate: > 80%
```

### KPIs de Negocio

```yaml
Efficiency:
  - Ticket resolution time: -30%
  - First response time: -50%
  - Escalation rate: -20%
  - Agent productivity: +40%

Quality:
  - Ticket classification accuracy: > 90%
  - User satisfaction (CSAT): > 4.5/5
  - Suggestion acceptance rate: > 70%
  - Duplicate detection rate: > 85%

Cost:
  - Cost per ticket: -25%
  - Manual intervention rate: < 20%
  - Training time for new agents: -40%
  - Operational overhead: -35%
```

---

## 🔧 Troubleshooting Común

### Backend

**Problema:** OAuth token expired
```bash
# Solución
1. Verificar token_expiry en ServiceNow
2. Implementar refresh token logic
3. Check clock skew entre sistemas
```

**Problema:** Rate limiting de ServiceNow
```bash
# Solución
1. Implementar exponential backoff
2. Cache responses agresivamente
3. Batch requests donde sea posible
4. Negociar límites con ServiceNow admin
```

**Problema:** OOM en conversación larga
```bash
# Solución
1. Implementar sliding window (últimos N mensajes)
2. Comprimir contexto con summarization
3. Mover a Redis con TTL
4. Garbage collect conversaciones inactivas
```

### Frontend

**Problema:** CORS errors
```bash
# Solución
1. Verificar CORS en backend
2. Check URL en .env.local
3. Proxy en next.config.js si es necesario
```

**Problema:** Build fails con memory error
```bash
# Solución
NODE_OPTIONS="--max-old-space-size=4096" npm run build
```

**Problema:** Hydration mismatch
```bash
# Solución
1. Usar dynamic import con ssr: false
2. Check localStorage access en useEffect
3. Usar Suspense para componentes client-side
```

---

## 📚 Referencias y Recursos

### Documentación Oficial

- **Spring Boot**: https://spring.io/projects/spring-boot
- **Spring WebFlux**: https://docs.spring.io/spring-framework/reference/web/webflux.html
- **Next.js**: https://nextjs.org/docs
- **React Query**: https://tanstack.com/query/latest
- **OpenAI API**: https://platform.openai.com/docs
- **ServiceNow REST API**: https://developer.servicenow.com/dev.do

### Patrones y Arquitectura

- **Reactive Streams**: https://www.reactive-streams.org/
- **Agent Patterns**: https://www.deeplearning.ai/the-batch/
- **Function Calling**: https://platform.openai.com/docs/guides/function-calling
- **RAG Pattern**: Retrieval Augmented Generation
- **Circuit Breaker**: Resilience4j patterns

### Frameworks Agénticos (Fase 2)

- **LangGraph**: https://github.com/langchain-ai/langgraph
- **CrewAI**: https://github.com/joaomdmoura/crewAI
- **Semantic Kernel**: https://github.com/microsoft/semantic-kernel
- **AutoGen**: https://github.com/microsoft/autogen

---

## 🎓 Aprendizajes y Best Practices

### Desarrollo de Agentes

1. **Start Simple**: Comenzar con copilot antes que agentes autónomos
2. **Human-in-the-Loop**: Mantener supervisión humana en MVP
3. **Tool Calling > RAG**: Para acciones estructuradas, function calling es más preciso
4. **Context is King**: Invertir en buena gestión de contexto
5. **Prompt Engineering**: Dedicar tiempo a optimizar prompts

### Integración ServiceNow

1. **OAuth First**: No usar basic auth en producción
2. **Rate Limits**: Respetar límites de API
3. **Caching**: Cachear agresivamente para reducir llamadas
4. **Error Handling**: ServiceNow puede ser inconsistente
5. **Webhooks**: Considerar webhooks para eventos en tiempo real

### Architecture Decisions

1. **Cloud-Native**: Diseñar para containers desde el inicio
2. **Stateless**: Mantener backend stateless para escalabilidad
3. **Observability**: Logging, metrics y tracing desde día 1
4. **Security**: Secrets management y OAuth correctamente
5. **Testing**: E2E tests para flujos críticos

---

## 🚨 Riesgos y Mitigaciones

### Técnicos

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| Rate limiting de OpenAI | Alta | Alto | Cache, fallback a modelo local, rate limiter |
| Alucinaciones del LLM | Media | Alto | Validation layer, guardrails, human approval |
| Token limits excedidos | Media | Medio | Context compression, sliding window |
| ServiceNow downtime | Baja | Alto | Circuit breaker, queue de retry, fallback mode |
| Memory leak en conversaciones | Media | Medio | TTL, garbage collection, Redis migration |

### Negocio

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| Baja adopción de usuarios | Media | Alto | UX excellence, training, quick wins |
| Costos de OpenAI elevados | Alta | Medio | Monitoring, quotas, modelo híbrido |
| Resistencia al cambio | Alta | Medio | Change management, demos, ROI claro |
| Compliance/Privacy concerns | Media | Alto | Data governance, encryption, auditoría |
| Escalabilidad insuficiente | Baja | Alto | Load testing, auto-scaling, caching |

---

## 📝 Checklist de Producción

### Pre-Deploy

- [ ] Todos los tests passing (unit + integration + e2e)
- [ ] Security audit completado
- [ ] Secrets en vault/secrets manager
- [ ] Variables de entorno documentadas
- [ ] Database migrations testeadas
- [ ] Backup strategy definida
- [ ] Rollback plan documentado
- [ ] Load testing ejecutado
- [ ] Monitoring dashboards creados
- [ ] Alerting rules configuradas
- [ ] Runbook de operaciones escrito
- [ ] Disaster recovery plan

### Post-Deploy

- [ ] Smoke tests ejecutados
- [ ] Monitoring activo verificado
- [ ] Logs fluyendo correctamente
- [ ] Metrics reportando
- [ ] Alertas funcionando
- [ ] Performance baseline establecido
- [ ] Users migrados gradualmente (canary/blue-green)
- [ ] Feedback loop activo
- [ ] Incident response plan activado
- [ ] On-call rotation definida

---

## 🔮 Visión Futura

### 6 Meses

- Sistema en producción con 100+ usuarios activos
- 1000+ incidents procesados por IA
- Tiempo de resolución reducido 30%
- ROI positivo comprobado
- Vector search en knowledge base operacional
- Auto-clasificación con >90% accuracy

### 12 Meses

- Agentes autónomos en modo asistido
- Integración con múltiples fuentes (Jira, Slack, Teams)
- Multi-modal capabilities (imágenes, archivos adjuntos)
- Predictive analytics (forecast de incidents)
- Auto-remediation para incidents comunes
- Self-learning system (fine-tuning con feedback)

### 24 Meses

- Platform completa de AI for ITSM
- Marketplace de agentes especializados
- Multi-tenant SaaS
- Advanced analytics y BI
- Compliance automation
- Global deployment en múltiples clientes

---

## 💬 Contacto y Soporte

### Equipo de Desarrollo

```yaml
Tech Lead: [Nombre]
AI Engineer: [Nombre]
Backend Developer: [Nombre]
Frontend Developer: [Nombre]
DevOps Engineer: [Nombre]
```

### Canales de Comunicación

- **Slack**: #enterprise-agent
- **Email**: enterprise-agent@empresa.com
- **Jira**: PROJECT-EA
- **Confluence**: https://confluence.empresa.com/ea
- **GitHub**: https://github.com/empresa/enterprise-agent

---

## 📄 Licencia y Propiedad Intelectual

```
Copyright © 2026 [NTT DATA / Tu Empresa]
Todos los derechos reservados.

Este proyecto es propiedad de [Empresa] y está protegido por
acuerdos de confidencialidad y propiedad intelectual.

Uso interno únicamente. Prohibida su distribución sin autorización.
```

---

## 🙏 Agradecimientos

Este proyecto ha sido posible gracias al esfuerzo del equipo de AI Engineering y la colaboración con:
- Equipo de ServiceNow
- Equipo de IT Operations
- Service Desk Team
- Architecture Review Board
- Security Team

---

**Documento actualizado:** Mayo 2026  
**Versión:** 1.0.0  
**Estado:** MVP Fase 1 Completado

---

*Este documento es un living document y será actualizado conforme el proyecto evolucione.*
