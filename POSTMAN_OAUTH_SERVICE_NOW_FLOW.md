# 🔐 ServiceNow OAuth 2.0 – Authorization Code Flow  
## Enterprise Agent Backend (Spring Boot)

---

# 📌 Overview

This document describes the complete OAuth 2.0 Authorization Code integration between:

- ✅ Spring Boot Backend
- ✅ ServiceNow Instance
- ✅ Postman for API execution
- ✅ User-based authentication (no Basic Auth)

This flow authenticates a real ServiceNow user and executes REST operations in that user's context.

---

# 🏗️ Architecture Summary

```
Browser → Spring Boot → ServiceNow OAuth → Token Exchange → ServiceNow REST API
```

1. User authenticates via browser
2. Authorization Code is issued
3. Backend exchanges code for access_token
4. Token stored in memory
5. Backend calls ServiceNow APIs using Bearer token
6. Incidents created in user context

---

# ✅ PART 1 — Authorization (Browser Only)

> ⚠️ This step CANNOT be executed from Postman.

## Step 1 – Start Authorization

Open browser:

```
http://localhost:8080/oauth/authorize
```

### What happens:
- Redirects to ServiceNow
- User logs in
- User clicks **Allow**
- ServiceNow redirects back

---

## Step 2 – OAuth Callback

Example:

```
http://localhost:8080/oauth/callback?code=XXXXX&state=YYYY
```

### Expected Response:

```json
{
  "success": true,
  "message": "OAuth authorization successful",
  "userId": "user_1776189120405",
  "sessionActive": true
}
```

---

## ✅ IMPORTANT

Save the `userId` value:

```
user_1776189120405
```

This will be used in Postman.

---

# 📦 PART 2 — Postman Configuration

---

## ✅ Create Collection

Collection Name:

```
ServiceNow OAuth Integration
```

---

## ✅ Add Collection Variable

Go to Collection → Variables:

| Variable | Value |
|----------|--------|
| userId  | user_1776189120405 |

Now you can use:

```
{{userId}}
```

---

# 🧪 PART 3 — API Endpoints (Postman)

---

## 1️⃣ System Status

### GET

```
http://localhost:8080/api/auth/status
```

### Purpose:
Verify active sessions and authentication flow.

---

## 2️⃣ Validate Authenticated User

### GET

```
http://localhost:8080/api/auth/validate/{{userId}}
```

### Purpose:
Confirms:
- Token exists
- Token is valid
- User data retrieved from ServiceNow

### Expected Response:

```json
{
  "success": true,
  "user": {
    "sysId": "...",
    "username": "...",
    "firstName": "...",
    "lastName": "...",
    "email": "...",
    "authenticated": true
  }
}
```

---

## 3️⃣ Test API Call (User Context Validation)

### GET

```
http://localhost:8080/api/auth/test-api/{{userId}}
```

### Purpose:
Ensures:
- Access token works
- ServiceNow REST API is reachable
- User data retrieved from sys_user table

### Expected:

```json
{
  "success": true,
  "apiResponse": {
    "username": "...",
    "sysId": "...",
    "firstName": "...",
    "lastName": "...",
    "email": "..."
  }
}
```

---

## 4️⃣ Create Incident (User Context)

### POST

```
http://localhost:8080/api/auth/test-incident/{{userId}}
```

### Body (JSON):

```json
{
  "shortDescription": "Incident from Postman OAuth",
  "description": "Testing Authorization Code Flow",
  "priority": "4"
}
```

### Purpose:
Creates incident in ServiceNow as authenticated user.

### Expected:

```json
{
  "success": true,
  "incident": {
    "number": "INC0012345",
    "sysId": "...",
    "state": "1"
  }
}
```

---

## ✅ Verification in ServiceNow

Navigate to:

```
Incident → All
```

Check:

- ✅ Created By = Authenticated user
- ✅ Caller = Authenticated user

---

## 5️⃣ Retrieve User Incidents

### GET

```
http://localhost:8080/api/auth/incidents/{{userId}}
```

Returns incidents filtered by caller_id.

---

## 6️⃣ Invalidate Session

### DELETE

```
http://localhost:8080/api/auth/session/{{userId}}
```

Removes access token from memory.

---

# 🔄 Token Behavior

- Tokens stored in memory (ConcurrentHashMap)
- Restarting Spring Boot clears all sessions
- Login must be repeated after restart

---

# 🔐 Security Characteristics

✅ No Basic Authentication  
✅ OAuth 2.0 Authorization Code  
✅ Bearer token authentication  
✅ User-context API execution  
✅ Token refresh supported  
✅ Clean separation of concerns  

---

# 🧠 Technical Flow

1. `/oauth/authorize`
2. ServiceNow login
3. Authorization code issued
4. Backend exchanges code at `/oauth_token.do`
5. Access token stored
6. REST calls executed with:
   ```
   Authorization: Bearer {access_token}
   ```

---

# 📊 Endpoint Layers

| Layer | Endpoint | Versioned |
|--------|-----------|------------|
| Backend | /api/auth/test-incident/{userId} | ❌ |
| ServiceNow | /api/now/v1/table/incident | ✅ |

`v1` applies ONLY to ServiceNow.

---

# ✅ Final Checklist

- [x] OAuth configured
- [x] Client Secret validated
- [x] Redirect URI correct
- [x] Scope useraccount active
- [x] Token exchange working
- [x] REST calls authenticated
- [x] Incident creation validated
- [x] Postman flow documented

---

# 🚀 Optional Enhancements

- Persist tokens in database
- Implement JWT internal session
- Add CSRF state validation
- Add Swagger documentation
- Add token revocation endpoint
- Add production-level session store (Redis)

---

# ✅ Status

🎉 Fully Functional OAuth 2.0 Authorization Code Flow  
✅ Ready for demonstration  
✅ Ready for Postman execution  
✅ Ready for technical presentation  

---

**Author:** Enterprise Agent Backend  
**Flow Type:** OAuth 2.0 Authorization Code  
**Environment:** Localhost (Spring Boot) + ServiceNow Demo
