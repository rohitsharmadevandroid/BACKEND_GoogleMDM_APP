# Non-GMS Custom DPC API Contract

This document describes the **actual, currently-implemented** backend surface that a non-GMS custom DPC app (the `Google_MDM` Kotlin project) must talk to. Every endpoint, field, and behavior below was read directly from the backend's controller/service/DTO/security/repository/entity code — nothing here is invented or aspirational. Gaps are called out explicitly in Section 11.

Base URL (local dev): `https://localhost:8080` (TLS is on via a self-signed dev cert — see Section 10).

---

## 1. Enrollment

**Endpoint:** `POST /api/dpc/enroll`
**Auth:** none (public route; the enrollment token itself is the one-time proof of identity — see `SecurityConfig`'s `.requestMatchers("/api/dpc/**").permitAll()` plus `DeviceAuthenticationFilter.shouldNotFilter` explicitly exempting this exact path).

### How a token is created (admin-side, for context — the DPC never calls this)
`POST /api/organizations/{organizationId}/non-gms-enrollment-tokens` (admin JWT required)
Request body (optional, defaults shown):
```json
{ "defaultPolicyId": null, "maxUses": 1 }
```
Response:
```json
{
  "id": "9e9c34d0-cd50-4ee0-a51e-5f5856f14074",
  "tokenValue": "4SVcC0v9qRPCWdb0KJ6c0Jo2etatSZON",
  "qrCodeData": "{\"enrollmentToken\":\"4SVcC0v9qRPCWdb0KJ6c0Jo2etatSZON\"}",
  "maxUses": 1
}
```
`qrCodeData` is **not** a real Device Owner provisioning QR payload (no package name / APK URL / signing checksum — those don't exist until the DPC APK is built and hosted). It's a placeholder JSON the DPC can read `enrollmentToken` from directly for now.

### The actual enrollment call
```
curl -k -X POST https://localhost:8080/api/dpc/enroll \
  -H "Content-Type: application/json" \
  -d '{"enrollmentToken":"4SVcC0v9qRPCWdb0KJ6c0Jo2etatSZON"}'
```
Request DTO (`DpcEnrollRequest`):
```json
{ "enrollmentToken": "string" }
```
Success response `200` (`DpcEnrollResponse`):
```json
{
  "deviceId": "2724bdb3-5860-4f01-a9a6-fd1097ea4d14",
  "deviceApiKey": "dBIKveaKIAZ1yTO4_4GWJsa6B2oRiFOndJLi83TZLMA",
  "checkInIntervalSeconds": 60
}
```

**`deviceApiKey` is shown exactly once, in this response.** The backend never stores it — only its SHA-256 hash (`DeviceCredentialService`). If the DPC loses it, the device cannot re-authenticate; an admin must revoke the device and re-enroll it. **Store this immediately and durably** (Android Keystore-backed storage recommended).

### Server-side validation performed, in order (`DpcEnrollmentService`)
1. Token must exist → else `400 {"error":"Unknown enrollment token"}`
2. Token's `deviceType` must be `NON_GMS` → else `400 {"error":"Not a non-GMS enrollment token"}`
3. Token's `status` must be `ACTIVE` → else `400 {"error":"Token is <STATUS>, not usable"}` (statuses: `ACTIVE, EXPIRED, REVOKED, CONSUMED`)
4. Token must not be expired (`expiresAt`) → else `400 {"error":"Token has expired"}`
5. `usedCount < maxUses` → else `400 {"error":"Token has reached its max uses"}`

### What happens on success
- A new `Device` row is created: `deviceType=NON_GMS`, `deviceUid=<random UUID string>`, `status=PROVISIONING`, `policy=<token's defaultPolicy, if any>`, `credentialHash=SHA-256(rawKey)`, `credentialIssuedAt=now`.
- Token's `usedCount` is incremented; if it reaches `maxUses`, `status` becomes `CONSUMED`; if `maxUses==1`, the token also records `claimedByDevice`.
- The raw key returned to you is **32 random bytes, base64url-encoded without padding** — this is your permanent bearer credential (Section 2).
- `checkInIntervalSeconds` is server-configured (`mdm.dpc.check-in-interval-seconds`, default `60`) and is also echoed on every check-in — treat it as authoritative/refreshable, not a hardcoded app constant.

---

## 2. Device Authentication

**Mechanism:** static bearer API key, checked by a dedicated servlet filter (`DeviceAuthenticationFilter`) — completely separate from the admin JWT system, and *not* wired into Spring Security's `Authentication` object (no roles/principal, just a request attribute).

**Header required on every `/api/dpc/**` call except `/api/dpc/enroll`:**
```
Authorization: Bearer dBIKveaKIAZ1yTO4_4GWJsa6B2oRiFOndJLi83TZLMA
```

**Server-side check, exactly:**
1. Header must start with `Bearer ` (case-sensitive prefix) → else `401`.
2. The raw key is hashed with SHA-256 (lowercase hex) and looked up via `deviceRepository.findByCredentialHash(hash)`.
3. If no device matches, or the matched device's `credentialRevokedAt` is non-null → `401`.
4. Otherwise the device entity is attached to the request (`request.setAttribute("authenticatedDevice", device)`) and the request proceeds.

**Failure response body: empty.** The filter sets `response.status = 401` and returns — no JSON error body, no `WWW-Authenticate` header. Your HTTP client should treat any non-2xx on these endpoints as "re-auth needed" based on status code alone, not response body parsing.

**Credential revocation:** a device's credential becomes permanently unusable once `credentialRevokedAt` is set (this happens on admin-initiated unenroll — Section 7). There is **no client-initiated re-issuance/rotation endpoint** — see Section 11.

---

## 3. Check-in / Polling

**Endpoint:** `POST /api/dpc/checkin`
**Auth:** device bearer key (Section 2), required.

```
curl -k -X POST https://localhost:8080/api/dpc/checkin \
  -H "Authorization: Bearer dBIKveaKIAZ1yTO4_4GWJsa6B2oRiFOndJLi83TZLMA" \
  -H "Content-Type: application/json" \
  -d '{"osVersion":"14","model":"Pixel 7","manufacturer":"Google","lastPolicyVersionApplied":1}'
```

Request DTO (`DpcCheckInRequest`) — **every field is optional**, `null`/omitted fields are left untouched server-side:
```json
{
  "osVersion": "string | null",
  "model": "string | null",
  "manufacturer": "string | null",
  "lastPolicyVersionApplied": "int | null"
}
```
There is no separate "heartbeat" endpoint — check-in itself **is** the heartbeat. It's the only place `Device.lastSeenAt` gets updated.

Response DTO (`DpcCheckInResponse`):
```json
{
  "policy": null,
  "policyVersion": null,
  "pendingCommands": [],
  "checkInIntervalSeconds": 60
}
```

### What the server does on every check-in
- Updates `osVersion` / `model` / `manufacturer` on the `Device` row, but **only for fields you actually sent** (non-null).
- Sets `lastSeenAt = now()`.
- Flips device `status` from `PROVISIONING` → `ACTIVE` (the very first check-in after enrollment transitions the device out of provisioning; nothing else does).
- Computes policy diff (Section 4) and pending commands (Section 5) — see those sections for exact shape.
- Always echoes `checkInIntervalSeconds` — poll at this cadence; it can change between calls if the server config changes.

**Polling expectation:** there is no server-pushed notification channel to the custom DPC (Pub/Sub is Google-side only, for GMS devices). The DPC **must** poll `/api/dpc/checkin` on its own timer at `checkInIntervalSeconds`. This is also the only mechanism for command delivery — see Section 5.

---

## 4. Policy Retrieval / Synchronization

Policy is **not** a separate endpoint — it rides on the check-in response, versioned.

**Versioning mechanism:** you send `lastPolicyVersionApplied` (the version you last successfully applied); the server compares it to the device's currently-assigned policy's `version`. Server logic (`DpcCheckInService`):
```
policyChanged = (device has an assigned policy) AND (policy.version != request.lastPolicyVersionApplied)
```
- If changed → `policy` field is populated (full payload, not a diff) and `policyVersion` is the new version number.
- If unchanged, or the device has no policy assigned → `policy: null, policyVersion: null`.

**There is no explicit "no policy assigned" vs "policy unchanged" distinction in the response** — both produce `policy: null`. If you need to tell them apart, you currently can't from this endpoint alone (see Section 11).

### Policy payload shape (`CustomDpcPolicyPayload`) — full JSON when changed:
```json
{
  "password": {
    "minimumLength": 6,
    "quality": "PASSWORD_QUALITY_ALPHANUMERIC",
    "maxFailedAttemptsBeforeWipe": 10
  },
  "cameraDisabled": false,
  "factoryResetDisabled": true,
  "kioskMode": {
    "enabled": false,
    "allowedPackageNames": []
  },
  "appRestrictions": [
    { "packageName": "com.example.app", "installType": "REQUIRED" }
  ],
  "wifi": {
    "ssid": "CorpWifi",
    "securityType": "WPA2_PSK",
    "password": "secret",
    "hidden": false
  }
}
```
Field notes (all verified from `PolicyDefinition` → `CustomDpcPolicyTranslator` → `CustomDpcPolicyPayload`):
- `password.quality` is a string literal matching Android's `DevicePolicyManager.PASSWORD_QUALITY_*` constant **names** (`PASSWORD_QUALITY_ALPHANUMERIC` or `PASSWORD_QUALITY_SOMETHING` — only these two values currently exist; deliberate design so the DPC can resolve by name instead of a separate vocabulary).
- `appRestrictions[].installType` is one of `"REQUIRED" | "BLOCKED" | "AVAILABLE"` (string, from enum `.name`) — the DPC decides what to actually do with each; there's no Play EMM integration on this path to enforce it automatically.
- `wifi.securityType` is one of `"OPEN" | "WPA2_PSK"`.
- Any top-level object (`password`, `kioskMode`, `wifi`) can be `null` if that concern isn't configured on the policy.
- `wifi.password` can be present in plaintext in this payload — it comes straight from the stored `Policy` JSON with no additional encryption at this layer (see Section 10 security notes).

---

## 5. Command Delivery

**No separate "pull commands" endpoint exists.** Commands are delivered exclusively as part of the check-in response's `pendingCommands` array.

```json
"pendingCommands": [
  {
    "commandId": "b4f5ca9a-0000-0000-0000-000000000000",
    "type": "LOCK",
    "params": {
      "lockDurationSeconds": 300,
      "newPassword": null,
      "resetPasswordFlags": [],
      "wipeDataFlags": [],
      "clearAppsDataPackageNames": [],
      "requestDeviceInfoType": null
    }
  }
]
```

**Supported `type` values** (`CommandType` enum — exhaustive, nothing else exists):
`LOCK`, `WIPE`, `REBOOT`, `RESET_PASSWORD`, `CLEAR_APP_DATA`, `REQUEST_DEVICE_INFO`

**`params` is always the full `CommandParams` shape** regardless of `type` — irrelevant fields are just `null`/empty for that command type. There's no per-type payload narrowing; the DPC must know which fields are relevant to which `type`:
| type | relevant params fields |
|---|---|
| `LOCK` | `lockDurationSeconds` |
| `WIPE` | `wipeDataFlags` |
| `REBOOT` | (none) |
| `RESET_PASSWORD` | `newPassword`, `resetPasswordFlags` |
| `CLEAR_APP_DATA` | `clearAppsDataPackageNames` |
| `REQUEST_DEVICE_INFO` | `requestDeviceInfoType` |

**Command status lifecycle** (`CommandStatus` enum): `PENDING → SENT → (your choice) → ...`
- Commands are created by an admin as `PENDING` (`POST /api/devices/{deviceId}/commands` — admin-facing, not called by the DPC).
- The moment a `PENDING` command is included in a check-in response, the server immediately flips it to `SENT` and stamps `dispatchedAt = now()` — **this happens whether or not the DPC actually receives/processes the HTTP response.** If the check-in response is lost in transit after the server commits, the command is marked `SENT` server-side but the DPC never saw it — there is no re-delivery of `SENT`-but-unacked commands beyond what you do in Section 6. Note this is at-most-once delivery from the server's point of view once the DB commit happens; there's no timeout/retry currently.
- Only `PENDING`-status commands for that device are ever included in `pendingCommands` — once sent, a command won't reappear on subsequent check-ins even if never acknowledged.
- `ACKNOWLEDGED` and `EXPIRED` exist in the enum but **nothing in current code ever sets them** — they're unused today. Only `SENT` (set by check-in) and whatever the DPC posts in Section 6 (`COMPLETED`/`FAILED`/anything else in the enum) are actually written.

---

## 6. Command Acknowledgement

**Endpoint:** `POST /api/dpc/commands/{commandId}/ack`
**Auth:** device bearer key, required.

```
curl -k -X POST https://localhost:8080/api/dpc/commands/b4f5ca9a-0000-0000-0000-000000000000/ack \
  -H "Authorization: Bearer dBIKveaKIAZ1yTO4_4GWJsa6B2oRiFOndJLi83TZLMA" \
  -H "Content-Type: application/json" \
  -d '{"status":"COMPLETED"}'
```

Request DTO (`DpcCommandAckRequest`):
```json
{ "status": "COMPLETED", "errorMessage": null }
```
`status` accepts **any** `CommandStatus` enum value verbatim (`PENDING, SENT, ACKNOWLEDGED, COMPLETED, FAILED, EXPIRED`) — the backend does not validate that the transition makes sense (e.g. posting `status: "PENDING"` on an already-`SENT` command is accepted and just overwrites it). In practice the DPC should only ever send `COMPLETED` or `FAILED`.

Success response `200`:
```json
{ "id": "b4f5ca9a-0000-0000-0000-000000000000", "status": "COMPLETED" }
```

**Server behavior:** sets `command.status` and `command.errorMessage` to whatever you sent; if `status` is `COMPLETED` or `FAILED`, also stamps `command.completedAt = now()`. For any other status value, `completedAt` stays null.

**Security detail worth knowing:** if `commandId` doesn't exist, OR exists but belongs to a *different* device than the authenticated one, both cases return the **exact same** `404 {"error":"No command with id <id>"}` — deliberately, so a compromised/malicious device can't use this endpoint to fingerprint whether a given command ID exists for another device.

**Invalid JSON `status` value** (not a valid enum constant) → falls through to the generic `500` handler (`GlobalExceptionHandler.handleUnexpected`) since Jackson deserialization failure isn't one of the explicitly mapped exceptions. This is a real gap — see Section 11.

---

## 7. Device Unenrollment

**There is no DPC-facing unenroll endpoint.** Unenrollment is admin-initiated only.

**Endpoint:** `DELETE /api/devices/{deviceId}` — **admin JWT required** (`hasAnyRole(SUPER_ADMIN, ORG_ADMIN)`), not device-key auth.

```
curl -k -X DELETE https://localhost:8080/api/devices/2724bdb3-5860-4f01-a9a6-fd1097ea4d14 \
  -H "Authorization: Bearer <admin-jwt>"
```
Response `200`:
```json
{
  "id": "2724bdb3-5860-4f01-a9a6-fd1097ea4d14",
  "deviceType": "NON_GMS",
  "displayName": null,
  "status": "DELETED",
  "model": null,
  "manufacturer": null,
  "osVersion": null,
  "lastSeenAt": "2026-08-20T12:41:00Z",
  "policyId": null,
  "policyName": null,
  "gmsDeviceResourceName": null,
  "deviceUid": "..."
}
```

**Credential invalidation:** for a `NON_GMS` device, the service sets `credentialRevokedAt = now()` and `status = DELETED`. **No external call is made and no final command is sent to the device** — this is purely a server-side revocation. From that moment, the device's existing `Authorization: Bearer <key>` on `/api/dpc/**` starts returning `401` (per Section 2's revocation check). The device itself is never notified — its next check-in attempt will simply start failing with `401`. If a final wipe is required, an admin must issue and confirm a `WIPE` command (Section 5) **before** calling this delete endpoint — deleting first means the device can no longer even poll for that wipe command.

**Implication for the DPC app:** treat a `401` on check-in (with a previously-known-good key) as "possibly revoked/unenrolled" and surface that to the user / stop background polling — there's no dedicated "you have been unenrolled" push or distinguishing error code, since the auth filter returns an empty-body `401` for both "unknown key" and "revoked key" alike.

---

## 8. Device State / Heartbeat

There is no separate heartbeat endpoint — as noted in Section 3, `/api/dpc/checkin` **is** the heartbeat.

**All device-state fields settable by the DPC via check-in:**
| field | type | notes |
|---|---|---|
| `osVersion` | `String?` | free text, stored as-is |
| `model` | `String?` | free text, stored as-is |
| `manufacturer` | `String?` | free text, stored as-is |
| `lastPolicyVersionApplied` | `Int?` | used only for the policy diff (Section 4); not persisted as its own device column, only used in-request |

**Fields the backend maintains itself (not settable by the DPC at all):**
- `lastSeenAt` — set to `now()` on every check-in, unconditionally.
- `status` — only ever auto-transitioned `PROVISIONING → ACTIVE` on first check-in; all other transitions (`INACTIVE`, `WIPED`, `DELETED`) are admin/backend-only and have no DPC-facing trigger today.

**Yes, the backend stores the latest state** — directly on the `Device` row (`osVersion`, `model`, `manufacturer`, `lastSeenAt`), overwritten on each check-in, no history/audit trail of prior values.

**Fields conspicuously NOT collectable via check-in today** (present on `Device` entity but with no DPC-facing way to set them): `imei`, `serialNumber`, `displayName`. See Section 11.

---

## 9. Error Handling

**Standard error body shape** (from `GlobalExceptionHandler`), used everywhere except the two cases noted below:
```json
{ "error": "human-readable message" }
```

| Status | When | Example body |
|---|---|---|
| `400` | `InvalidEnrollmentTokenException`, `InvalidDeviceStateException` | `{"error":"Token is REVOKED, not usable"}` |
| `401` | Device auth filter rejects (bad/missing/revoked key) on `/api/dpc/**` **except enroll** | *(empty body)* |
| `404` | `DeviceNotFoundException`, `CommandNotFoundException` (also used to mask cross-device command lookups, Section 6) | `{"error":"No command with id <uuid>"}` |
| `500` | Anything unmapped — including malformed JSON, bad enum values, unexpected nulls | `{"error":"<exception message or class name>"}` |

**Enrollment-specific errors** (all `400`, all from `DpcEnrollmentService`, exact strings):
- `"Unknown enrollment token"`
- `"Not a non-GMS enrollment token"`
- `"Token is EXPIRED, not usable"` / `"Token is REVOKED, not usable"` / `"Token is CONSUMED, not usable"`
- `"Token has expired"`
- `"Token has reached its max uses"`

**Authentication errors on `/api/dpc/**`:** always a bare `401` with no body — do not attempt to parse a JSON error body on 401 from these endpoints, there isn't one.

**Invalid command/policy errors:** there is currently no dedicated validation exception for a malformed `DpcCommandAckRequest.status` (unknown enum string) — Jackson throws during body deserialization, which Spring maps to a generic `400` **before** it would even reach the controller/service layer in the exact case of unparseable JSON, but an unknown enum constant string specifically triggers a deserialization error typically surfaced as `400` by Spring's default `HttpMessageNotReadableException` handling (this is Spring's built-in behavior, not something `GlobalExceptionHandler` explicitly maps — no domain-specific message is produced, just Spring's default Jackson-error response). Treat any non-200 here as failure and retry with a valid enum string.

---

## 10. Security

- **TLS:** required in this deployment — the dev server runs with `server.ssl.enabled: true` against a self-signed PKCS12 keystore (`secrets/dev-keystore.p12`). In production this must be a real cert; the DPC app should pin or at minimum validate against a real CA in production and should **not** ship with certificate validation disabled (the `-k`/`--insecure` curl flag used in this document's examples is a **dev-only** convenience against the self-signed cert).
- **Credential handling:** the device's permanent credential is a 32-byte `SecureRandom` value, base64url-encoded, returned exactly once at enrollment (Section 1). The server stores only its SHA-256 hash — it cannot ever re-display or recover your raw key. Store it in Android Keystore-backed encrypted storage, not plain `SharedPreferences`.
- **No nonce / timestamp / replay protection exists today.** The bearer key is a static, long-lived secret with no request signing, no timestamp window, no nonce — a captured key is fully usable until an admin revokes it. There is no rate limiting on `/api/dpc/enroll` or `/api/dpc/checkin` either.
- **Headers the DPC must send:**
  - `Authorization: Bearer <deviceApiKey>` on every `/api/dpc/**` call except `/api/dpc/enroll`.
  - `Content-Type: application/json` on all `POST` bodies.
  - No custom headers (no API version header, no device-ID header — the device is identified purely by which key hashes match in the DB).
- **No key rotation mechanism.** If a key is suspected compromised, the only remedy today is admin unenroll (Section 7) + re-enrollment with a fresh token — there's no "rotate my own credential" self-service call.

---

## 11. Gaps — what's missing for the Android custom DPC today

These are things the DPC will plausibly need that **do not currently exist** in the backend. Flagging per the "identify anything missing" instruction — not implementing any of it yet.

1. **No device-initiated credential rotation/refresh.** Only path to a new key is full re-enrollment via a new token (requires admin action).
2. **No distinguishable "unenrolled/revoked" vs "just wrong key" signal.** Both produce an identical empty-body `401`. The DPC can't tell "please stop and show a clear unenrollment message" apart from "something is misconfigured."
3. **No way to set `imei`, `serialNumber`, or `displayName` from the device.** These fields exist on `Device` but check-in only accepts `osVersion`/`model`/`manufacturer`. If you need serial/IMEI recorded, there's currently no endpoint for it.
4. **No distinction between "no policy assigned" and "policy unchanged since your version."** Both produce `policy: null, policyVersion: null` on check-in — can't currently detect "you were unassigned from a policy" versus "nothing changed."
5. **No re-delivery / retry semantics for commands.** A command flips `PENDING → SENT` the instant it's included in a check-in response body, regardless of whether the DPC actually received/processed that response. If the network fails mid-response, that command is effectively lost (never resent) unless an admin notices and re-issues it.
6. **No command-side validation of the ack `status` value or of state-machine transitions** — the ack endpoint accepts any enum value in any order (Section 6, 9), and unknown values 500 instead of a clean `400` with a helpful message.
7. **No structured error response on `/api/dpc/**` auth failures** — a `401` with no body makes client-side error UX harder than it needs to be (can't show a specific reason).
8. **No real provisioning-QR payload.** `qrCodeData` is a placeholder `{"enrollmentToken":"..."}` JSON, not an actual Android Device Owner QR provisioning payload (needs package name, APK checksum, download URL — none of which exist until the DPC APK itself is built/hosted). Needed before a genuine "factory reset → scan QR → auto-provisioned as Device Owner" flow works.
9. **No API versioning** in the URL or headers — any future breaking change to these DTOs has no migration path built in.
10. **No rate limiting / brute-force protection** on `/api/dpc/enroll` (guessable/leaked tokens) or repeated bad bearer keys on `/api/dpc/checkin`.
11. **No push channel** — the DPC must poll; there's no equivalent of Google's Pub/Sub notification for non-GMS devices, so command latency is bounded by `checkInIntervalSeconds`, not real-time.

---

## CUSTOM DPC API CONTRACT (summary for the Android project)

| # | Purpose | Method + Path | Auth |
|---|---|---|---|
| 1 | Enroll | `POST /api/dpc/enroll` | none (token in body) |
| 2 | Check-in / heartbeat / policy sync / command pull | `POST /api/dpc/checkin` | `Bearer <deviceApiKey>` |
| 3 | Acknowledge a command | `POST /api/dpc/commands/{commandId}/ack` | `Bearer <deviceApiKey>` |

- **Credential:** `deviceApiKey` from the enroll response — a static bearer token, shown once, store securely, no rotation endpoint exists.
- **Everything else** (unenroll, command issuance, token issuance) is admin-only and not called by the DPC.
- **Poll interval:** server-supplied via `checkInIntervalSeconds` on every enroll/check-in response; don't hardcode it.
- **Policy:** full payload on check-in when `policyVersion` changes vs. what you last sent as `lastPolicyVersionApplied`; `null` means "no change" (can't distinguish from "no policy assigned" — Gap 4).
- **Commands:** delivered inline in check-in's `pendingCommands`; six types (`LOCK, WIPE, REBOOT, RESET_PASSWORD, CLEAR_APP_DATA, REQUEST_DEVICE_INFO`); ack with `COMPLETED`/`FAILED`.
- **Errors:** `{"error": "..."}` JSON except `401` on `/api/dpc/**`, which is always empty-bodied.
- **TLS required**, no replay/nonce protection, no key rotation — treat the bearer key with the same care as a password.

This contract reflects the backend exactly as implemented today (no changes made as part of this task). Any of the 11 gaps above that block a required Android DPC feature should be raised before the Android side builds around a workaround.
