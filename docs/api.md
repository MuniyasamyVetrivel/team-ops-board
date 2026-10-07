# API Reference

Base path: `/api`. JSON in and out. Every endpoint except the public auth endpoints requires `Authorization: Bearer <accessToken>`.

## Errors

Every error uses the same body (`com.teamops.common.exception.ApiError`):

```json
{
  "timestamp": "2026-10-07T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/auth/login",
  "fieldErrors": [{ "field": "email", "message": "Enter a valid email address" }]
}
```

| Status | Typical `code` | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST` | Invalid input |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS`, `SESSION_EXPIRED` | Missing, invalid or expired credentials. The client should refresh or sign in |
| 403 | `FORBIDDEN`, `ACCOUNT_DISABLED` | Authenticated but not allowed |
| 404 | `NOT_FOUND` | Unknown resource |
| 409 | e.g. `EMAIL_IN_USE` | Conflict with existing data |
| 500 | `INTERNAL_ERROR` | Unexpected error. Details are logged server-side, never returned |

## Authentication: `/api/auth`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/login` | public | Email + password. Returns an access token and sets the refresh cookie |
| POST | `/api/auth/refresh` | refresh cookie | Rotates the refresh cookie and returns a new access token |
| POST | `/api/auth/logout` | refresh cookie (optional) | Revokes the refresh token and clears the cookie. Always `204` |
| GET | `/api/auth/me` | bearer | Current user, department, roles and effective permissions |

Any bearer header sent to `login`, `refresh` or `logout` is ignored, so a stale access token never blocks them.

### POST /api/auth/login

```json
{ "email": "rakesh@teamops.local", "password": "…" }
```

**200**, with header `Set-Cookie: tob_refresh=…; Path=/api/auth; HttpOnly; SameSite=Strict; Max-Age=604800`:

```json
{
  "accessToken": "eyJ…",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "expiresAt": "2026-10-07T11:00:00Z",
  "user": {
    "id": 1,
    "email": "rakesh@teamops.local",
    "firstName": "Rakesh",
    "lastName": "",
    "fullName": "Rakesh",
    "jobTitle": "Reporting Manager",
    "department": { "id": 1, "name": "IT", "code": "IT" },
    "roles": ["SUPER_ADMIN"],
    "permissions": ["APPROVAL_CONFIGURE", "…"]
  }
}
```

Errors:
- **401 `INVALID_CREDENTIALS`** is returned for both an unknown email and a wrong password, with the same message and similar timing.
- **403 `ACCOUNT_DISABLED`** is returned only when the password is correct.

### POST /api/auth/refresh

Reads the `tob_refresh` cookie. Returns the same body as login and sets a new cookie; the old one is revoked.
- **401 `SESSION_EXPIRED`** if the cookie is missing, unknown, expired or already used.
- Re-using an already-rotated token (outside a 10-second grace window for concurrent tabs) revokes **all** sessions of that user and is audited as `REFRESH_TOKEN_REUSE`.

### GET /api/auth/me

Returns the `user` object shown above. Roles and permissions are computed by the backend; SUPER_ADMIN always receives the full permission catalogue.

## Actuator

| Path | Auth |
|---|---|
| `/actuator/health` | public (no details) |
| `/actuator/info` | public |
