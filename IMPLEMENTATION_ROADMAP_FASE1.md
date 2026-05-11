# IMPLEMENTATION ROADMAP - FASE 1

## 🎯 OBJETIVO
Convertir el MVP conceptual en código producible. Enfoque: validar valor de negocio en 4-6 semanas.

---

## 📊 ROADMAP INCREMENTA - 4 ITERACIONES

### ITERACIÓN 1: FUNDAMENTOS (Semana 1-2)
**Meta**: Request autenticado → Intent clasificado → Response básica

**Entregables**:
```
✅ UserContextFilter + SecurityContext
✅ IntentClassifier (LLM-powered)
✅ AgentOrchestrator básico (single-intent)
✅ Endpoint /api/v1/agent/chat funcional
✅ Tests unitarios
✅ Métricas OpenTelemetry básicas
```

**No incluir aún**:
- Multi-step workflows
- RAG completo
- Guardrails complejos
- Human-in-the-loop

---

### ITERACIÓN 2: TOOLS + MEMORY (Semana 3)
**Meta**: Agent puede ejecutar acciones reales en ServiceNow

**Entregables**:
```
✅ ToolRegistry pattern
✅ Handlers: GetIncident, SearchIncidents
✅ ConversationMemory (Redis)
✅ Response formatting + natural language
✅ Error handling + retry logic
✅ Observabilidad mejorada
```

---

### ITERACIÓN 3: ANÁLISIS + RAG (Semana 4)
**Meta**: Sugerencias inteligentes basadas en contexto

**Entregables**:
```
✅ IncidentAnalyzer
✅ SummaryGenerator
✅ Weaviate embeddings + búsqueda semántica
✅ SuggestResolution tool
✅ Ranking de relevancia
```

---

### ITERACIÓN 4: GUARDRAILS + PRODUCTION (Semana 5-6)
**Meta**: MVP listo para UAT interno

**Entregables**:
```
✅ SecurityGate (input/output validation)
✅ RateLimiter + cost monitoring
✅ PII redaction
✅ Audit trail completo
✅ Frontend integration (React wrapper)
✅ Documentation + runbook
```

---

## 🔨 ITERACIÓN 1: FOUNDATION LAYER

### PASO 1.1: UserContextFilter

**¿Por qué?**
- Inyectar contexto de usuario en toda la request (thread-safe)
- Validar OAuth2 token
- Crear SecurityContext para RBAC
- Base para auditoría

**Diseño**:
```java
public class UserContextFilter implements WebFilter {
    // Context holder - thread-safe
    private static final ThreadLocal<UserContext> contextHolder = new ThreadLocal<>();
    
    public UserContext execute(String userId, String accessToken) {
        // 1. Validar token con ServiceNow
        // 2. Obtener user info
        // 3. Validar permisos (si empresa tiene RBAC)
        // 4. Guardar en contextHolder
        // 5. Disponible en toda la request
    }
}

public class UserContext {
    String userId;              // sys_id en ServiceNow
    String username;
    String accessToken;         // OAuth2
    String serviceNowToken;     // Para llamadas a ServiceNow
    Set<String> roles;          // ["analyst", "user", "admin"]
    long requestTimestamp;
}
```

**Implementación**:
```java
@Component
@Log4j2
public class UserContextFilter implements WebFilter {

    private final OAuthAuthorizationService oauthService;
    private final UserContextService userContextService;

    private static final ThreadLocal<UserContext> contextHolder = new ThreadLocal<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            try {
                // 1. Extraer token del header
                String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    return chain.filter(exchange);  // Opcional: permitir endpoints públicos
                }

                String accessToken = authHeader.substring(7);

                // 2. Extraer userId de header (asumiendo está)
                // En prod: extraer del JWT
                String userId = extractUserIdFromToken(accessToken);

                // 3. Validar con OAuth
                UserInfo userInfo = userContextService.validateAndGetUserInfo(userId);
                String serviceNowToken = userContextService.getValidAccessTokenForUser(userId);

                // 4. Crear contexto
                UserContext userContext = UserContext.builder()
                    .userId(userId)
                    .username(userInfo.getUsername())
                    .accessToken(accessToken)
                    .serviceNowToken(serviceNowToken)
                    .roles(userInfo.getRoles())  // O desde RBAC system
                    .requestTimestamp(System.currentTimeMillis())
                    .build();

                // 5. Guardar en ThreadLocal
                contextHolder.set(userContext);

                // 6. Logging para auditoría
                log.info("UserContext set for user={}, roles={}", 
                    userContext.getUsername(), userContext.getRoles());

                // 7. Continuar con request
                return chain.filter(exchange)
                    .doFinally(signalType -> {
                        // Cleanup
                        contextHolder.remove();
                    });

            } catch (Exception e) {
                log.error("Error setting UserContext: {}", e.getMessage());
                return Mono.error(new RuntimeException("Authentication failed"));
            }
        });
    }

    public static UserContext getCurrentContext() {
        UserContext ctx = contextHolder.get();
        if (ctx == null) {
            throw new IllegalStateException("UserContext not initialized");
        }
        return ctx;
    }

    private String extractUserIdFromToken(String accessToken) {
        // Decodificar JWT o llamar a OAuth endpoint
        // Por ahora: simple
        return "user-" + System.currentTimeMillis();
    }
}
```

**Integración en SecurityConfig**:
```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            UserContextFilter userContextFilter) {
        
        http
            .csrf().disable()
            .authorizeExchange()
                .pathMatchers("/api/v1/agent/**").authenticated()
                .anyExchange().permitAll()
            .and()
            .addFilterBefore(userContextFilter, SecurityWebFiltersOrder.FIRST);
        
        return http.build();
    }
}
```

---

### PASO 1.2: IntentClassifier (LLM-powered)

**¿Por qué?**
- Reemplazar regex hardcodeado (AgentService) con LLM
- Clasificar intención del usuario
- Output: JSON estructurado y validado

**Diseño**:
```java
public class IntentClassifier {
    
    // Intenciones soportadas
    enum Intent {
        GET_INCIDENT,
        SEARCH_INCIDENTS,
        CREATE_INCIDENT,
        ANALYZE_INCIDENT,
        CHAT
    }
    
    public IntentResult classify(String userInput, ConversationContext context) {
        // 1. Construir prompt con contexto
        // 2. Llamar a LLM (Ollama)
        // 3. Parsear JSON response
        // 4. Validar intención permitida
        // 5. Retornar structurado
    }
}
```

**Implementación**:
```java
@Service
@Log4j2
public class IntentClassifier {

    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public record IntentResult(
        Intent intent,
        Map<String, Object> parameters,
        double confidence
    ) {}

    public enum Intent {
        GET_INCIDENT,
        SEARCH_INCIDENTS,
        CREATE_INCIDENT,
        ANALYZE_INCIDENT,
        CHAT
    }

    public IntentResult classify(String userInput, ConversationContext context) {
        
        // 1. Build system prompt
        String systemPrompt = buildClassificationPrompt(context);
        
        // 2. Get LLM response (JSON)
        String llmResponse = llmService.classifyIntent(
            systemPrompt + "\n\nUser input: " + userInput
        );
        
        // 3. Parse JSON
        try {
            IntentClassificationResponse response = parseResponse(llmResponse);
            
            // 4. Validate intent is allowed
            validateIntentPermissions(response.intent, context.getUserRoles());
            
            // 5. Return structured
            return new IntentResult(
                Intent.valueOf(response.intent),
                response.parameters != null ? response.parameters : Map.of(),
                response.confidence
            );
            
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse LLM response: {}", llmResponse);
            return new IntentResult(Intent.CHAT, Map.of(), 0.0);
        }
    }

    private String buildClassificationPrompt(ConversationContext context) {
        return """
Eres un clasificador de intención para un sistema de Service Desk.

CONTEXTO DEL USUARIO:
- Rol: %s
- Último ticket: %s
- Historial reciente: %s

INTENCIONES PERMITIDAS:
1. GET_INCIDENT: "dame el estado del ticket INC123"
2. SEARCH_INCIDENTS: "busca mis tickets abiertos"
3. CREATE_INCIDENT: "quiero crear un nuevo ticket"
4. ANALYZE_INCIDENT: "analiza mi incidente INC456"
5. CHAT: conversación libre

RESPONDE SOLO EN JSON:
{
  "intent": "GET_INCIDENT|SEARCH_INCIDENTS|CREATE_INCIDENT|ANALYZE_INCIDENT|CHAT",
  "parameters": { ... },
  "confidence": 0.0-1.0
}

No escribas nada fuera del JSON.
""".formatted(
            context.getUserRoles(),
            context.getLastIncident() != null ? context.getLastIncident() : "ninguno",
            context.getRecentHistory()
        );
    }

    private IntentClassificationResponse parseResponse(String json) throws JsonProcessingException {
        // Remove markdown code blocks if present
        String cleaned = json.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return objectMapper.readValue(cleaned, IntentClassificationResponse.class);
    }

    private void validateIntentPermissions(String intent, Set<String> roles) {
        // Simple RBAC: analysts pueden hacer todo, users solo consulta
        if (roles.contains("user") && 
            (intent.equals("CREATE_INCIDENT") || intent.equals("ANALYZE_INCIDENT"))) {
            throw new SecurityException("User not authorized for " + intent);
        }
    }

    // Inner class for LLM response
    @Data
    static class IntentClassificationResponse {
        String intent;
        Map<String, Object> parameters;
        double confidence;
    }
}
```

---

### PASO 1.3: AgentOrchestrator

**¿Por qué?**
- Core que coordina: UserContext → Intent → Tools → Response
- Maneja flujos multi-step (futuro)
- Inyecta métricas, logging, error handling

**Diseño**:
```java
public class AgentOrchestrator {
    
    public AgentResponse execute(String userMessage, String sessionId) {
        // 1. Load conversation memory
        // 2. Classify intent
        // 3. Route to action
        // 4. Execute tool (si aplica)
        // 5. Build response
        // 6. Save memory
        // 7. Record metrics
        // 8. Return
    }
}
```

**Implementación**:
```java
@Service
@Log4j2
public class AgentOrchestrator {

    private final IntentClassifier intentClassifier;
    private final ConversationMemory conversationMemory;
    private final ActionRouter actionRouter;
    private final AgentMetrics agentMetrics;

    public Mono<AgentResponse> execute(String userMessage, String sessionId) {
        
        long startTime = System.currentTimeMillis();
        UserContext userContext = UserContextFilter.getCurrentContext();
        
        return Mono.defer(() -> {
            try {
                log.info("Starting agent execution for user={}, session={}", 
                    userContext.getUserId(), sessionId);

                // 1. Load conversation memory
                ConversationContext context = conversationMemory.load(sessionId);
                
                // 2. Add current message to memory
                context.addMessage("user", userMessage);
                
                // 3. Classify intent
                IntentClassifier.IntentResult intentResult = 
                    intentClassifier.classify(userMessage, context);
                
                log.debug("Intent classified: intent={}, confidence={}", 
                    intentResult.intent(), intentResult.confidence());
                
                // 4. Route and execute
                Object toolResult = actionRouter.route(
                    intentResult.intent(),
                    intentResult.parameters(),
                    userContext
                );
                
                // 5. Build response
                AgentResponse response = buildResponse(
                    intentResult,
                    toolResult,
                    userContext
                );
                
                // 6. Save memory
                context.addMessage("assistant", response.getMessage());
                return conversationMemory.save(sessionId, context)
                    .then(Mono.just(response));
                    
            } catch (Exception e) {
                log.error("Error in agent execution: {}", e.getMessage(), e);
                return Mono.just(AgentResponse.error(e.getMessage()));
            }
        })
        .doOnSuccess(response -> {
            // 8. Record metrics
            long duration = System.currentTimeMillis() - startTime;
            agentMetrics.recordExecution(
                userContext.getUserId(),
                duration,
                "success"
            );
            log.info("Agent execution completed in {}ms", duration);
        })
        .doOnError(error -> {
            long duration = System.currentTimeMillis() - startTime;
            agentMetrics.recordExecution(
                userContext.getUserId(),
                duration,
                "error"
            );
            log.error("Agent execution failed after {}ms", duration);
        });
    }

    private AgentResponse buildResponse(
            IntentClassifier.IntentResult intentResult,
            Object toolResult,
            UserContext userContext) {
        
        return AgentResponse.builder()
            .status("SUCCESS")
            .message(formatMessage(intentResult, toolResult))
            .intent(intentResult.intent().name())
            .confidence(intentResult.confidence())
            .userId(userContext.getUserId())
            .timestamp(System.currentTimeMillis())
            .build();
    }

    private String formatMessage(IntentClassifier.IntentResult intent, Object result) {
        // TODO: LLM natural language formatting
        return result != null ? result.toString() : "OK";
    }
}

@Data
@Builder
class AgentResponse {
    String status
