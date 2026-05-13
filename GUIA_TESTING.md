# Guía de Testing: Enterprise Agent con Frontend

## Requisitos previos

- Java 17+ y Maven instalados
- Node.js 18+ instalado
- Ollama corriendo con el modelo `qwen2.5:7b`
- Acceso a la instancia ServiceNow (`everisspainsludemo3.service-now.com`)

---

## 1. Arrancar el Backend (Spring Boot)

Abre una terminal en la raíz del proyecto:

```bash
mvnw.cmd spring-boot:run
```

Verifica que está corriendo:
```
http://localhost:8080/actuator/health
```
Debe devolver `{"status":"UP"}`.

---

## 2. Configurar el Frontend

### 2.1 Crear el archivo de entorno

```bash
cd frontend
copy .env.example .env.local
```

Edita `frontend/.env.local`:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

> No necesitas `NEXT_PUBLIC_OAUTH_CLIENT_ID` ni `NEXT_PUBLIC_SERVICENOW_INSTANCE` porque
> el flujo OAuth está completamente delegado al backend.

### 2.2 Instalar dependencias y arrancar

```bash
cd frontend
npm install
npm run dev
```

El frontend estará disponible en:
```
http://localhost:3000
```

---

## 3. Flujo de prueba completo

### Paso 1 — Abrir la página de login

Navega a:
```
http://localhost:3000/login
```

Verás el botón **"Iniciar sesión con ServiceNow"**.

---

### Paso 2 — Iniciar sesión OAuth

Haz clic en el botón. El navegador seguirá esta cadena:

```
[Navegador] → GET http://localhost:8080/oauth/authorize
             → 302 → https://everisspainsludemo3.service-now.com/oauth_auth.do?...
             → [Usuario introduce credenciales de ServiceNow]
             → ServiceNow → GET http://localhost:8080/oauth/callback?code=XXX&state=YYY
             → Backend intercambia código por token OAuth
             → 302 → http://localhost:3000/auth/callback?userId=ZZZ&success=true
             → [Frontend guarda userId en localStorage]
             → Redirect automático a /dashboard
```

---

### Paso 3 — Verificar autenticación

Abre las **DevTools del navegador** → pestaña **Application** → **Local Storage** →
`http://localhost:3000`.

Debes ver:
| Clave | Valor |
|-------|-------|
| `user_id` | `user_abc123...` (UUID generado por el backend) |
| `auth_method` | `oauth` |

---

### Paso 4 — Probar el Dashboard

Tras el login aterrizarás en `/dashboard`. Desde aquí puedes:

- Ver el resumen de incidentes
- Navegar a `/incidents` para ver la lista de incidentes de ServiceNow
- Navegar a `/agent` para chatear con el agente IA

---

### Paso 5 — Probar el Agente IA

Ve a `http://localhost:3000/agent` y escribe un mensaje como:

```
¿Cuántos incidentes críticos hay abiertos?
```

```
Muéstrame los incidentes de alta prioridad
```

```
Crea un incidente sobre el fallo del servidor de producción
```

Cada petición incluirá automáticamente la cabecera `X-User-Id` con el valor
guardado en localStorage, lo que permite al backend recuperar el token OAuth
del usuario y llamar a ServiceNow en su nombre.

---

### Paso 6 — Probar los Incidentes

Ve a `http://localhost:3000/incidents`. La página cargará incidentes reales
de ServiceNow a través del endpoint `GET /api/incidents`.

---

## 4. Verificar la cabecera X-User-Id (opcional)

En las DevTools → pestaña **Network**, haz clic en cualquier petición a
`localhost:8080` y comprueba los **Request Headers**:

```
X-User-Id: user_abc123...
Content-Type: application/json
```

---

## 5. Pruebas de error

### Simular fallo en el callback OAuth

Navega manualmente a:
```
http://localhost:3000/auth/callback?error=access_denied&details=User+denied+access
```

Verás la pantalla de error con el mensaje y redirección automática a `/login`.

### Simular sesión sin autenticación

Borra `user_id` de localStorage y recarga `/dashboard`. Deberías ser
redirigido a `/login`.

---

## 6. Logs del backend

Con el nivel `DEBUG` configurado en `application.yml`, el backend imprime:

```
OAuth callback received - code present: true, error: null
OAuth authorization successful for user: user_abc123, redirecting to frontend
```

Útil para diagnosticar problemas en el flujo.

---

## 7. Resumen de URLs

| URL | Descripción |
|-----|-------------|
| `http://localhost:3000` | Frontend (Next.js) |
| `http://localhost:3000/login` | Página de login |
| `http://localhost:3000/auth/callback` | Callback OAuth del frontend |
| `http://localhost:3000/dashboard` | Dashboard principal |
| `http://localhost:3000/incidents` | Lista de incidentes |
| `http://localhost:3000/agent` | Chat con el agente IA |
| `http://localhost:8080/oauth/authorize` | Inicia flujo OAuth (backend) |
| `http://localhost:8080/oauth/callback` | Callback OAuth (backend, llamado por ServiceNow) |
| `http://localhost:8080/api/agent/chat` | API del agente IA |
| `http://localhost:8080/api/incidents` | API de incidentes |
| `http://localhost:8080/actuator/health` | Health check |
