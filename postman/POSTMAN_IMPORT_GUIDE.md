# 📦 Guía de Importación - Colección de Postman

## Enterprise Agent Backend - ServiceNow Integration

Esta guía te ayudará a importar y configurar la colección de Postman para probar todos los endpoints del backend.

---

## 📋 Prerrequisitos

1. **Postman instalado** (Desktop o Web)
   - Descargar: https://www.postman.com/downloads/

2. **Backend corriendo**
   ```bash
   mvn spring-boot:run
   ```
   - URL base: `http://localhost:8080`

3. **Credenciales de ServiceNow** configuradas en `application.yml`

---

## 🚀 Paso 1: Importar la Colección

### Opción A: Importar desde archivo

1. Abrir Postman
2. Click en **"Import"** (esquina superior izquierda)
3. Click en **"Upload Files"**
4. Seleccionar: `postman/Enterprise_Agent_Backend.postman_collection.json`
5. Click en **"Import"**

### Opción B: Importar desde texto

1. Abrir Postman
2. Click en **"Import"**
3. Click en **"Raw text"**
4. Copiar y pegar el contenido del archivo JSON
5. Click en **"Continue"** → **"Import"**

---

## ⚙️ Paso 2: Configurar Variables de Entorno

La colección incluye variables predefinidas. Para editarlas:

1. Click derecho en la colección **"Enterprise Agent Backend - ServiceNow Integration"**
2. Seleccionar **"Edit"**
3. Ir a la pestaña **"Variables"**
4. Configurar las siguientes variables:

| Variable | Valor Inicial | Descripción |
|----------|---------------|-------------|
| `baseUrl` | `http://localhost:8080` | URL base del backend |
| `userId` | *(vacío)* | Se llenará después del OAuth flow |

5. Click en **"Update"**

---

## 🔐 Paso 3: Completar Flujo OAuth 2.0

### 3.1 Iniciar OAuth Flow

⚠️ **Este paso DEBE hacerse en el navegador, NO en Postman**

1. Copiar la siguiente URL:
   ```
   http://localhost:8080/oauth/authorize
   ```

2. Abrir en tu navegador web

3. Serás redirigido a ServiceNow para autenticación

4. Hacer login con tus credenciales de ServiceNow

5. Autorizar la aplicación

6. ServiceNow te redirige a:
   ```
   http://localhost:8080/oauth/callback?code=...
   ```

7. El callback retorna un JSON con tu `userId`:
   ```json
   {
     "success": true,
     "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
     "sessionActive": true,
     "message": "OAuth authorization successful"
   }
   ```

8. **COPIAR el valor de `userId`**

### 3.2 Guardar el userId en Postman

1. Volver a Postman
2. Click derecho en la colección
3. **Edit** → **Variables**
4. En la variable `userId`, pegar el valor copiado en **CURRENT VALUE**
5. Click en **"Update"**

---

## ✅ Paso 4: Verificar Configuración

Ejecutar el request **"1.3 System Status"**:

```
GET {{baseUrl}}/api/auth/status
```

**Response esperado:**
```json
{
  "success": true,
  "status": {
    "activeUsers": 1,
    "activeSessions": 1,
    "authenticationFlow": "Authorization Code Flow",
    "tokenStorage": "In-Memory (ConcurrentHashMap)",
    "refreshTokenSupport": true
  }
}
```

Si recibes este response, ¡la configuración es correcta! ✅

---

## 📂 Estructura de la Colección

### 1. OAuth 2.0 Flow
- **1.1 Iniciar OAuth Flow** → Ejecutar en navegador
- **1.2 OAuth Callback** → Automático (llamado por ServiceNow)
- **1.3 System Status** → Verificar estado del sistema

### 2. User Management
- **2.1 Validate User** → Validar usuario autenticado
- **2.2 Test API Call** → Probar llamada a ServiceNow API
- **2.3 Invalidate Session** → Cerrar sesión

### 3. Incident Management
- **3.1 Get User Incidents** → Obtener incidentes del usuario
- **3.2 Create Test Incident** → Crear incidente de prueba
- **3.3 Get All Incidents** → Listar todos los incidentes
- **3.4 Get Incident by Number** → Obtener incidente específico

### 4. AI Agent (MVP Phase 1)
- **4.1 Agent Query** → Consultar al agente de IA

---

## 🧪 Paso 5: Probar Endpoints

### Test 1: Validar Usuario

```
GET {{baseUrl}}/api/auth/validate/{{userId}}
```

**Response esperado:**
```json
{
  "success": true,
  "user": {
    "userId": "...",
    "sysId": "...",
    "username": "admin",
    "firstName": "System",
    "lastName": "Administrator",
    "email": "admin@example.com",
    "authenticated": true
  }
}
```

### Test 2: Obtener Incidentes del Usuario

```
GET {{baseUrl}}/api/auth/incidents/{{userId}}
```

### Test 3: Crear Incidente de Prueba

```
POST {{baseUrl}}/api/auth/test-incident/{{userId}}
Content-Type: application/json

{
  "shortDescription": "Test desde Postman",
  "description": "Incidente creado para validar integración OAuth",
  "priority": "4"
}
```

### Test 4: Consultar Agente de IA

```
POST {{baseUrl}}/api/agent/query
Content-Type: application/json

{
  "query": "¿Cuáles son los incidentes abiertos de alta prioridad?",
  "conversationId": "test-conv-001"
}
```

---

## 🔄 Renovar Sesión

Si tu sesión expira (token inválido):

1. Ejecutar: **2.3 Invalidate Session**
   ```
   DELETE {{baseUrl}}/api/auth/session/{{userId}}
   ```

2. Repetir el **Paso 3** (OAuth Flow) en el navegador

3. Actualizar el `userId` en las variables

---

## 🐛 Troubleshooting

### Error: "401 Unauthorized"

**Causa**: Token expirado o `userId` inválido

**Solución**:
1. Verificar que `userId` esté configurado en las variables
2. Renovar sesión OAuth (ver sección anterior)

### Error: "Connection refused"

**Causa**: Backend no está corriendo

**Solución**:
```bash
cd enterprise-agent-backend
mvn spring-boot:run
```

### Error: "Invalid OAuth credentials"

**Causa**: Credenciales incorrectas en `application.yml`

**Solución**:
1. Verificar `client-id` y `client-secret` en `application.yml`
2. Verificar URL de ServiceNow
3. Reiniciar backend

### No aparece el userId en el callback

**Causa**: Error en el intercambio de código por token

**Solución**:
1. Revisar logs del backend: `src/main/resources/logs/`
2. Verificar configuración OAuth en ServiceNow
3. Verificar que el redirect URI coincida: `http://localhost:8080/oauth/callback`

---

## 📊 Variables Disponibles

| Variable | Uso | Ejemplo |
|----------|-----|---------|
| `{{baseUrl}}` | URL base del backend | `http://localhost:8080` |
| `{{userId}}` | ID de usuario autenticado | `a1b2c3d4-e5f6-7890-...` |

---

## 🔒 Seguridad

⚠️ **Importante**:

- El `userId` es sensible - no compartir públicamente
- Las sesiones son en memoria - se pierden al reiniciar el backend
- En producción, usar almacenamiento persistente para tokens
- El backend actual es **MVP** - no usar en producción sin hardening

---

## 📚 Recursos Adicionales

- **Documentación OAuth ServiceNow**: Ver `OAUTH_MIGRATION_SUMMARY.md`
- **Arquitectura del proyecto**: Ver `README.md`
- **Configuración básica**: Ver `BASIC_AUTH_SETUP.md`

---

## 💡 Tips

1. **Organizar workspace**: Crear un workspace dedicado para este proyecto
2. **Guardar responses**: Usar "Save Response" para comparar resultados
3. **Tests automáticos**: Postman permite crear tests en JavaScript
4. **Environments**: Crear environments separados para Dev/Staging/Prod
5. **Pre-request scripts**: Útiles para generar timestamps o IDs únicos

---

## ✅ Checklist de Verificación

- [ ] Postman instalado
- [ ] Backend corriendo en `localhost:8080`
- [ ] Colección importada exitosamente
- [ ] Variables de entorno configuradas
- [ ] OAuth flow completado en navegador
- [ ] `userId` guardado en variables
- [ ] Endpoint de status funciona
- [ ] Usuario validado correctamente
- [ ] Test de API call exitoso
- [ ] Incidente de prueba creado

---

## 🎯 Próximos Pasos

Una vez configurado Postman:

1. **Explorar todos los endpoints** de la colección
2. **Crear incidentes de prueba** con diferentes prioridades
3. **Probar el agente de IA** con diferentes queries
4. **Documentar casos de uso** específicos de tu proyecto
5. **Configurar tests automáticos** en Postman

---

**¿Necesitas ayuda?**

Revisa los logs del backend en la consola o contacta al equipo de desarrollo.

---

**Versión**: 1.0.0  
**Última actualización**: Mayo 2026  
**Proyecto**: Enterprise Agent Backend - NTT DATA
