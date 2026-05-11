# Guía de Implementación - Enterprise Agent MVP

## 🚀 Quick Start

### Pre-requisitos

1. **Java 17+**
2. **Maven 3.8+**
3. **Ollama** (modelo local) o acceso a OpenAI API
4. **ServiceNow Developer Instance** (o acceso a instancia corporativa)
5. **OAuth 2.0 configurado** en ServiceNow

### Configuración Inicial

#### 1. Clonar y compilar

```bash
git clone <repository>
cd enterprise-agent-backend
mvn clean install
```

#### 2. Configurar `application.yml`

```yaml
server:
  port: 8080

# LLM Configuration
ollama:
  model: llama3.2:latest  # o el modelo que prefieras

# ServiceNow Configuration
servicenow:
  instance-url: https://dev123456.service-now.com
  client-id: <your-oauth-client-id>
  client-secret: <your-oauth-client-secret>
  redirect-uri: http://localhost:8080/oauth/callback
  
# Security
spring:
  security:
    oauth2:
      client:
        registration:
          servicenow:
            client-id: ${servicenow.client-id}
            client-secret: ${servicenow.client-secret}
            authorization-grant-type: authorization_code
            redirect-uri: ${servicenow.redirect-uri}
```

#### 3. Iniciar Ollama (si usas modelo local)

```bash
ollama serve
ollama pull llama3.2:latest
```

#### 4. Ejecutar la aplicación

```bash
mvn spring-boot:run
```

---

## 🧪 Testing

### 1. Testing desde Postman/cURL

#### Autenticación OAuth (primer paso necesario)

```bash
# 1. Abrir en navegador para obtener authorization code
http://localhost:8080/oauth/authorize

# 2. Después del login, serás redirigido a /oauth/callback con el token

# 3. Usar el endpoint del agente
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "message": "Muéstrame el incidente INC0010001",
    "sessionId": "test-session-123"
  }'
```

### 2. Testing de Intenciones

#### GET_INCIDENT
```json
{
  "message": "Muéstrame el incidente INC0010001",
  "sessionId": "session-001"
}
```

**Respuesta esperada**:
```json
{
  "success": true,
  "message": "📋 Incidente INC0010001:\n• Estado: In Progress\n• Prioridad: 3 - Moderate\n• Asignado a: John Doe...",
  "intent": "GET_INCIDENT",
  "confidence": 0.98,
  "executionTimeMs": 1243,
  "metadata": {
    "incidentNumber": "INC0010001"
  }
}
```

#### SEARCH_INCIDENTS
```json
{
  "message": "Busca todos los incidentes de red con prioridad alta",
  "sessionId": "session-001"
}
```

#### ANALYZE_INCIDENT
```json
{
  "message": "Analiza el incidente INC0010002 y dame recomendaciones",
  "sessionId": "session-001"
}
```

#### CREATE_INCIDENT
```json
{
  "message": "Crea un incidente: el servidor de producción no responde, prioridad crítica",
  "sessionId": "session-001"
}
```

#### CHAT
```json
{
  "message": "¿Qué puedes hacer por mí?",
  "sessionId": "session-001"
}
```

### 3. Testing de Memoria Conversacional

```bash
# Turno 1
POST /api/agent/chat
{
  "message": "Muéstrame el incidente INC0010001",
  "sessionId": "memory-test"
}

# Turno 2 (referencia a conversación previa)
POST /api/agent/chat
{
  "message": "Resume ese incidente",
  "sessionId": "memory-test"
}

# Turno 3
POST /api/agent/chat
{
  "message": "¿Qué incidentes hemos revisado?",
  "sessionId": "memory-test"
}
```

---

## 📋 Casos de Uso Implementados

### Caso 1: Consulta de Incidente Específico

**Prompt del Usuario**: "¿Cuál es el estado del ticket INC0010001?"

**Flujo**:
1. IntentClassifier → `GET_INCIDENT` + `incidentNumber: INC0010001`
2. ActionRouter → `handleGetIncident()`
3. ServiceNowClient → GET `/api/now/table/incident?number=INC0010001`
4. LLMService → Formateo natural de la respuesta
5. ConversationMemory → Guardar interacción
6. Response → Usuario recibe respuesta formateada

**Salida**:
```
📋 Incidente INC0010001:
• Estado: In Progress
• Prioridad: 3 - Moderate
• Categoría: Network
• Asignado a: John Doe (Service Desk)
• Descripción: Problemas de conectividad en oficina central
• Abierto hace: 2 días
```

---

### Caso 2: Búsqueda de Incidentes del Usuario

**Prompt del Usuario**: "Muéstrame mis tickets abiertos"

**Flujo**:
1. IntentClassifier → `LIST_MY_INCIDENTS`
2. ActionRouter → `handleListMyIncidents()`
3. ServiceNowClient → GET `/api/now/table/incident?caller_id=<user_sys_id>&active=true`
4. Formateo de lista
5. Response

**Salida**:
```
📊 Tienes 3 incidentes activos:

1. INC0010045 - Laptop no enciende (Abierto: 1 día)
2. INC0010032 - Acceso a VPN no funciona (Abierto: 5 días)
3. INC0010021 - Solicitud de software (Abierto: 7 días)
```

---

### Caso 3: Análisis Inteligente de Incidente

**Prompt del Usuario**: "Analiza el incidente INC0010002 y dame recomendaciones"

**Flujo**:
1. IntentClassifier → `ANALYZE_INCIDENT` + `incidentNumber: INC0010002`
2. ActionRouter → `handleAnalyzeIncident()`
3. ServiceNowClient → Obtener datos completos
4. LLMService → Análisis con contexto enriquecido:
   ```
   Eres un analista ITSM experto.
   
   Analiza el siguiente incidente y proporciona:
   1. Resumen técnico
   2. Posibles causas raíz
   3. Recomendaciones de resolución
   4. Nivel de urgencia
   
   Datos del incidente:
   [JSON completo del incidente]
   ```
5. Response con análisis estructurado

**Salida**:
```
🔍 Análisis del Incidente INC0010002:

📊 Resumen:
Incidente de red con múltiples usuarios afectados en Building A.
Prioridad actual: 2 (High). Tiempo abierto: 4 horas.

🔎 Posibles Causas:
• Switch principal del piso 3 puede estar fallando
• Configuración de VLAN incorrecta tras mantenimiento reciente
• Problema de routing en firewall

✅ Recomendaciones:
1. Verificar estado del switch core (Building A, Rack 3)
2. Revisar logs de red de las últimas 6 horas
3. Validar configuración post-mantenimiento
4. Escalar a Network Operations si no se resuelve en 1 hora

⚠️ Urgencia: ALTA - Múltiples usuarios impactados
```

---

### Caso 4: Creación de Incidente

**Prompt del Usuario**: "Crea un incidente: mi computadora está muy lenta, es urgente"

**Flujo**:
1. IntentClassifier → `CREATE_INCIDENT` + extracción de:
   - `short_description`: "Computadora lenta"
   - `description`: "El equipo presenta lentitud general"
   - `priority`: "2" (inferido de "urgente")
2. ActionRouter → `handleCreateIncident()`
3. ServiceNowClient → POST `/api/now/table/incident` con:
   ```json
   {
     "short_description": "Computadora lenta",
     "description": "El usuario reporta lentitud general en el equipo",
     "priority": "2",
     "caller_id": "<user_sys_id>",
     "category": "hardware",
     "urgency": "2"
   }
   ```
4. Response con confirmación

**Salida**:
```
✅ Incidente creado exitosamente

📋 Detalles:
• Número: INC0010123
• Prioridad: 2 - High
• Estado: New
• Asignado a: Service Desk (auto-assignment)

Tu ticket será atendido según la prioridad. Recibirás notificaciones por email.
```

---

### Caso 5: Conversación Contextual

**Turno 1**:
Usuario: "Muéstrame el incidente INC0010001"
Agente: [Detalles del incidente]

**Turno 2**:
Usuario: "¿Está asignado a alguien?"
Agente: (usando memoria conversacional)
"Sí, el incidente INC0010001 está asignado a John Doe del equipo de Service Desk desde hace 2 días."

**Turno 3**:
Usuario: "Resume ese ticket"
Agente: (referencia contextual)
```
📝 Resumen del INC0010001:

Problema de conectividad de red en oficina central, reportado hace 3 días.
Actualmente en progreso, asignado a John Doe. Prioridad moderada.
Se han identificado issues con el switch del piso 2.
Resolución estimada: dentro de 24 horas.
```

---

## ⚙️ Configuración Avanzada

### Ajuste de Prompts

Los prompts están centralizados en:
- `IntentClassifier.java` → Clasificación de intenciones
- `LLMService.java` → Templates generales
- `ActionRouter.java` → Prompts específicos por acción

**Ejemplo: Mejorar clasificación de CREATE_INCIDENT**

```java
// En IntentClassifier.buildSystemPrompt()

CREATE_INCIDENT:
Si el usuario quiere reportar un problema, crear un ticket o registrar una incidencia.
Debes extraer:
- short_description: Título conciso (máx 100 chars)
- description: Descripción detallada
- priority: 1 (Critical), 2 (High), 3 (Moderate), 4 (Low), 5 (Planning)
  • "urgente", "crítico", "no funciona nada" → priority 1-2
  • "importante", "necesito" → priority 3
  • "cuando puedan", "no urgente" → priority 4-5
- category (opcional): hardware, software, network, database, etc.

Ejemplos:
Input: "Mi laptop no enciende, necesito ayuda urgente"
Output: {
  "intent": "CREATE_INCIDENT",
  "parameters": {
    "short_description": "Laptop no enciende",
    "description": "El equipo no responde al intentar encenderlo. Urgente.",
    "priority": "2",
    "category": "hardware"
  }
}
```

### Tuning de Temperature

```java
// LLMService.java

// Para clasificación: temperatura baja (determinístico)
String classify(...) {
    return generate(prompt, 0.0, 300);
}

// Para análisis: temperatura media (balance)
String analyze(...) {
    return generate(prompt, 0.3, 500);
}

// Para chat: temperatura alta (creativo)
String chat(...) {
    return generate(prompt, 0.7, 200);
}
```

---

## 🐛 Troubleshooting

### Problema 1: IntentClassifier devuelve UNKNOWN constantemente

**Causa**: El LLM no está siguiendo el formato JSON o el prompt no es claro.

**Solución**:
```java
// En IntentClassifier, agregar más ejemplos al prompt
// Verificar logs:
logger.debug("Raw LLM response: {}", rawResponse);

// Si el LLM retorna texto + JSON:
String cleanedJson = rawResponse.substring(rawResponse.indexOf("{"));
```

### Problema 2: Timeout en llamadas a ServiceNow

**Causa**: OAuth token expirado o red lenta.

**Solución**:
```java
// En ServiceNowClient, aumentar timeout
HttpClient httpClient = HttpClient.create()
    .responseTimeout(Duration.ofSeconds(30)); // aumentar si necesario

// Implementar retry logic
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
public JsonNode getIncident(...) { ... }
```

### Problema 3: Memoria conversacional no funciona

**Causa**: SessionId diferente en cada request.

**Solución**:
```javascript
// Frontend debe mantener el mismo sessionId
const sessionId = sessionStorage.getItem('agentSessionId') || uuidv4();
sessionStorage.setItem('agentSessionId', sessionId);

// Enviar en cada request
{
  "message": "...",
  "sessionId": sessionId
}
```

### Problema 4: LLM local muy lento

**Opciones**:
1. **Usar modelo más pequeño**: `llama3.2:1b` en vez de `llama3.2:3b`
2. **Cambiar a OpenAI**: Modificar LLMService para usar OpenAI API
3. **Implementar cache**: Guardar respuestas de clasificación frecuentes
4. **GPU acceleration**: Configurar Ollama con GPU

```yaml
# application.yml - Cambiar a OpenAI
llm:
  provider: openai
  api-key: sk-...
  model: gpt-4o-mini
```

---

## 📈 Métricas y Monitoreo

### Métricas Clave a Trackear

```java
// Agregar en AgentOrchestrator

@Autowired
private MeterRegistry meterRegistry;

public AgentResponse process(AgentRequest request) {
    long startTime = System.currentTimeMillis();
    
    try {
        // ... procesamiento ...
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Registrar métricas
        meterRegistry.counter("agent.requests.total",
            "intent", result.getIntent().name(),
            "status", "success"
        ).increment();
        
        meterRegistry.timer("agent.processing.duration",
            "intent", result.getIntent().name()
        ).record(duration, TimeUnit
