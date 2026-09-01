# MDM Backend — Full Architecture Reference

A from-the-source-code snapshot of everything the backend does, package by package, endpoint by endpoint, table by table. Written from a fresh read of every file (as of 2026-08-26) — not from memory.

---

## 1. What this is

A Spring Boot 3.3.4 / Kotlin backend for an MDM/EMM system that manages two fundamentally different kinds of Android devices through one unified admin API:

- **GMS devices** — real Android Enterprise devices managed via Google's **Android Management API**. The device runs Google's own "Android Device Policy" app; this backend never talks to the device directly, only to Google, and learns about device state via Pub/Sub push notifications.
- **Non-GMS devices** — devices with no Google Play Services, managed via a **custom DPC app** (`Google_MDM`, a separate Android/Kotlin project) that this backend talks to directly over a small custom HTTP protocol (`/api/dpc/**`).

The core design idea: an admin defines one vendor-neutral `PolicyDefinition` and issues one vendor-neutral `Command`; the backend translates and dispatches differently per device type, but the admin-facing API (policies, devices, commands, enrollment tokens) is identical for both.

Stack: Kotlin, Spring Boot 3.3.4, Spring Data JPA + Hibernate 6, PostgreSQL + Flyway (V1–V14), Spring Security (two independent auth mechanisms, see §4), `jjwt` 0.12.6, Google's `android-management` API client, Spring Cloud GCP Pub/Sub, a hand-rolled vanilla-JS admin dashboard (no build step).

---

## 2. Package-by-package

### `com.primeos.mdm.organization`
The tenant root. `Organization(id, name, slug unique, status: ACTIVE|SUSPENDED, createdAt, updatedAt)`.
- `POST /api/organizations` (SUPER_ADMIN) — 409 on duplicate slug
- `GET /api/organizations` (SUPER_ADMIN) — list all
- `GET /api/organizations/{id}` (any authenticated role, org-scoped)
- **No update or suspend/delete endpoint exists.** `OrganizationStatus.SUSPENDED` is defined but nothing in the codebase ever sets it — dead enum value.

### `com.primeos.mdm.admin`
Auth, roles, admin-user management.
- `AdminUser(id, organization: Organization?, email unique, passwordHash, role, createdAt, lastLoginAt, isActive)`. `organization == null` means a platform-level SUPER_ADMIN not tied to any org.
- `AdminRole` = `SUPER_ADMIN | ORG_ADMIN | ORG_VIEWER`.
- `AuthController` → `POST /api/auth/login` (public) → `AuthService.login`: looks up by email, `BCryptPasswordEncoder.matches`, then checks `isActive` — **in that order**, deliberately, so a deactivated account doesn't short-circuit past bcrypt's timing delay (would otherwise let an attacker distinguish "wrong password" from "deactivated"). All three failure modes throw the same `InvalidCredentialsException` → `401 {"error":"Invalid email or password"}`.
- `JwtService`: HS256, signs `{sub: adminUserId, role, organizationId}`, key from `mdm.auth.jwt-secret` (`Keys.hmacShaKeyFor`), expiry from `mdm.auth.jwt-expiration-minutes` (default 480 = 8h).
- `AdminAuthenticationFilter`: `OncePerRequestFilter`, skips `/api/dpc/**` and `/api/auth/login`; on a valid Bearer JWT, populates `SecurityContextHolder` with `AdminTokenClaims(adminUserId, role, organizationId)` as principal and a `ROLE_<role>` authority. **Never itself rejects a request** — a missing/invalid token just leaves it unauthenticated; `SecurityConfig`'s `authorizeHttpRequests` does the actual gating.
- `AdminAccessGuard.requireOrganizationAccess(orgId)`: reads `AdminTokenClaims` back out of the security context; if the token's `organizationId` is non-null and doesn't match `orgId`, throws `OrganizationAccessDeniedException` (403). A SUPER_ADMIN's `organizationId == null` always passes. This is the mechanism that enforces "which org's data", separate from role-based "what kind of action".
- `AdminUserController` (`/api/admin-users`, SUPER_ADMIN-only via `SecurityConfig` path rules): `POST` create, `GET` list-all, `POST /{id}/deactivate`.
- `OrgScopedAdminUserController`: `GET /api/organizations/{orgId}/admin-users` — any authenticated role, org-scoped via `AdminAccessGuard` inside the service.
- **Role matrix**: SUPER_ADMIN can touch any org and is the only role allowed to manage admin users or create/list organizations. ORG_ADMIN can do anything under `/api/**` except those SUPER_ADMIN-locked paths, scoped to their own org. **ORG_VIEWER is read-only everywhere** — not by an explicit deny rule, but because every POST/PUT/DELETE rule in `SecurityConfig` requires `SUPER_ADMIN`/`ORG_ADMIN` specifically and ORG_VIEWER only ever satisfies the generic `authenticated()` GET rule.

### `com.primeos.mdm.common`
Cross-cutting: security wiring and error handling.
- `SecurityConfig` — `authorizeHttpRequests`, in exact order: `/error` permitAll → `/api/auth/login` permitAll → `/api/dpc/**` permitAll (device auth handled by a separate filter) → `GET /api/organizations/*/gms-enterprise/callback` permitAll (Google's redirect carries no admin JWT) → admin-user & org create/list SUPER_ADMIN-only → generic `GET /api/**` authenticated() → generic `POST`/`PUT`/`DELETE /api/**` → SUPER_ADMIN or ORG_ADMIN → `anyRequest()` permitAll (only ever matches non-`/api/**`, i.e. the dashboard's static files). CSRF disabled, sessions stateless, custom `HttpStatusEntryPoint(401)` so auth failures are 401 not Spring's default 403. Registers `DeviceAuthenticationFilter` and `AdminAuthenticationFilter` via `addFilterBefore` (not `@Component`, to avoid double-registration).
- `DeviceAuthenticationFilter` — see §4.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) — maps every domain exception to a status (full table in §7).
- `PasswordHasherConfig` — `BCryptPasswordEncoder` bean, deliberately contrasted with the fast SHA-256 used for device keys (see §4): admin passwords are human-chosen/low-entropy and need a slow hash; a random 256-bit device key doesn't.

### `com.primeos.mdm.device`
The unified device model for both GMS and non-GMS.
- `Device` (table `devices`, one table for both types): `id, organization, deviceType (GMS|NON_GMS), displayName?, status (PROVISIONING|ACTIVE|INACTIVE|WIPED|DELETED), policy?, enrollmentToken?`, GMS-only (`gmsEnterprise?`, `gmsDeviceResourceName?` unique), non-GMS-only (`deviceUid?` unique, `imei?`, `serialNumber?`), common (`model?, manufacturer?, osVersion?, lastSeenAt?`), non-GMS credential fields (`credentialHash?` unique, `credentialIssuedAt?`, `credentialRevokedAt?`), `metadata` (JSONB), timestamps. A DB CHECK constraint enforces GMS rows have `gms_device_resource_name` and NON_GMS rows have `device_uid`; `DeviceService.validateIdentity` re-checks this in Kotlin so it fails with a real message instead of a raw `PSQLException`.
- `DeviceCredentialService`: `generate()` → 32 `SecureRandom` bytes, base64url no-padding → the raw device API key (shown once). `hash(rawKey)` → lowercase-hex SHA-256. Only the hash is ever persisted.
- `DeviceStatus` transitions found in code: default `PROVISIONING` at creation → `PROVISIONING → ACTIVE` unconditionally on a device's first `/api/dpc/checkin` (non-GMS) or first GMS Pub/Sub status/enrollment notification → `→ DELETED` only via `DeviceLifecycleService.unenroll()`. `INACTIVE`/`WIPED` have no setter anywhere in the codebase — dead status values today.
- Admin endpoints (`DeviceController` + `DeviceLifecycleController`):
  | Method | Path | Behavior |
  |---|---|---|
  | GET | `/api/organizations/{orgId}/devices` | org-scoped list |
  | GET | `/api/devices/{deviceId}` | single device summary |
  | PUT | `/api/devices/{deviceId}/policy` | `{policyId: UUID\|null}` — assign/change/clear; 400 `PolicyOrganizationMismatchException` if policy's org ≠ device's org |
  | PUT | `/api/devices/{deviceId}/display-name` | `{displayName: string\|null}` — trims, blank collapses to null |
  | DELETE | `/api/devices/{deviceId}` | unenroll — see below |
- **Unenroll** (`DeviceLifecycleService.unenroll`, optional body `{wipeDataFlags: [], wipeReasonMessage?}`): for **NON_GMS**, only sets `credentialRevokedAt = now()` — no external call, no final command sent (deliberate: revoking first would make the device unable to check in and receive a wipe command at all — issue+confirm a WIPE command *before* unenrolling if you need one). For **GMS**, calls `AndroidManagementService.deleteDevice(...)`, which wipes and unenrolls atomically via one Google API call. Both paths set `status = DELETED`.
- `DeviceSummary` is the canonical admin-facing shape: `id, deviceType, displayName, status, model, manufacturer, osVersion, lastSeenAt, policyId, policyName, gmsDeviceResourceName, deviceUid`.

### `com.primeos.mdm.dpc`
The entire non-GMS device-facing protocol — exhaustively documented in `CUSTOM_DPC_API_CONTRACT.md` in this repo. Summary:
- `POST /api/dpc/enroll` (public) — trades an enrollment token for a permanent device credential.
- `POST /api/dpc/checkin` (device Bearer key) — heartbeat + policy sync + command delivery, all in one call. `policyVersion` in the response **always** reflects the device's *current* assigned policy version, independent of whether the `policy` payload field is included (`policy` is only non-null when it changed since the client's `lastPolicyVersionApplied`). This is the mechanism that lets a client detect "policy was removed" (version drops to null) as well as "policy changed" (version differs).
- `POST /api/dpc/commands/{commandId}/ack` (device Bearer key) — `{status, errorMessage?, resultData?: Map<String,String>}`. A command belonging to a different device 404s identically to an unknown command (deliberately, to avoid leaking existence). `resultData` (added recently) is the only way collected data (e.g. from `REQUEST_DEVICE_INFO`) gets back to the backend — stored as-is into `Command.resultData` JSONB when present.

### `com.primeos.mdm.enrollment`
Token issuance for both device types, plus shared query/revoke.
- `EnrollmentToken` (table `enrollment_tokens`): `organization, deviceType, tokenValue (unique, plaintext), qrCodeData?, defaultPolicy?, maxUses=1, usedCount=0, status (ACTIVE|EXPIRED|REVOKED|CONSUMED), expiresAt?, metadata (JSONB), createdBy?, claimedByDevice?` (set only when `maxUses==1`), `createdAt`.
- `POST /api/organizations/{orgId}/gms-enrollment-tokens` — body `{policyName (required, raw Google policy resource name), oneTimeOnly=false, allowPersonalUsage="PERSONAL_USAGE_DISALLOWED", durationSeconds?}`. Requires a `GmsEnterprise` to already exist for the org (`NoGmsEnterpriseException` 400 otherwise). Calls Google's real `enrollmentTokens.create`; `qrCodeData` here **is** a genuine Android Device Owner provisioning QR (Google generates it). No internal `policyId` resolution — `policyName` must already be a real Google policy resource, meaning `POST /api/policies/{id}/sync` must have run first to produce it.
- `POST /api/organizations/{orgId}/non-gms-enrollment-tokens` — body `{defaultPolicyId?: UUID, maxUses=1}` (whole body optional). `qrCodeData` here is **only** a placeholder `{"enrollmentToken":"..."}` JSON — not a real Device Owner provisioning payload (would need a package name, APK download URL, and signing checksum, which don't exist until the DPC APK is built and hosted).
- `POST /api/enrollment-tokens/{tokenId}/revoke` — shared by both types, idempotent (re-revoking an already-revoked/consumed/expired token just re-saves, never errors).
- `GET /api/organizations/{orgId}/enrollment-tokens` — shared list view across both types, returns `EnrollmentTokenSummary` (includes the plaintext `tokenValue` on every call, not just once at creation — safe since it's stored in plaintext regardless).

### `com.primeos.mdm.policy`
The vendor-neutral policy engine.
- **Current `PolicyDefinition`** (this has grown over the project's history — this is the real, current shape):
  ```kotlin
  data class PolicyDefinition(
      val passwordPolicy: PasswordPolicy? = null,
      val cameraDisabled: Boolean = false,
      val factoryResetDisabled: Boolean = false,
      val screenCaptureDisabled: Boolean = false,
      val usbFileTransferDisabled: Boolean = false,
      val safeBootDisabled: Boolean = false,
      val addUserDisabled: Boolean = false,
      val outgoingCallsDisabled: Boolean = false,
      val smsDisabled: Boolean = false,
      val kioskMode: KioskModeConfig? = null,           // {enabled, allowedPackageNames: []}
      val appRestrictions: List<AppRestriction> = [],    // {packageName, installType: REQUIRED|BLOCKED|AVAILABLE}
      val wifiConfig: WifiConfig? = null,                // {ssid, securityType: OPEN|WPA2_PSK, password?, hidden}
  )
  ```
- `Policy` entity: `id, organization, name, description?, version=1, isActive=true, definition (JSONB text, via PolicyDefinitionCodec), createdBy?, timestamps`.
- **Lifecycle**:
  1. `POST /api/organizations/{orgId}/policies` — pure insert, `version=1`.
  2. `PUT /api/policies/{id}` — snapshots the pre-update `version`+`definition` into `policy_revisions` (append-only audit log), then overwrites `definition`, bumps `version += 1`. No network call, no device impact — a draft edit.
  3. `POST /api/policies/{id}/sync` — the only step touching the outside world. **Always** computes the non-GMS `CustomDpcPolicyPayload` (pure translation, delivered later via check-in). **Conditionally** pushes to Google via `policies.patch` if the org has a `GmsEnterprise`, using a deterministic resource name `enterprises/{enterpriseId}/policies/{policyId}` (reuses the internal Policy UUID as Google's policy ID segment). Returns `PolicySyncResult{customDpcPayload, gmsPolicyName?}`.
  4. `DELETE /api/policies/{id}` — soft delete (`isActive=false`, row kept — it's referenced by `policy_revisions` and possibly `enrollment_tokens.default_policy_id`). **Also unassigns the policy from every device currently pointing at it** (`device.policy = null` for all matches), so the next non-GMS check-in reports `policyVersion: null` (a real, detectable "policy was removed" signal) instead of silently continuing to serve a deleted policy forever.
- **Translators** (`com.primeos.mdm.policy.translator`), both pure functions, no I/O:
  - `CustomDpcPolicyTranslator` → `CustomDpcPolicyPayload`: straight field-for-field copy of all 8 booleans; `passwordPolicy.quality` becomes the literal string `"PASSWORD_QUALITY_ALPHANUMERIC"`/`"PASSWORD_QUALITY_SOMETHING"` (matches Android's `DevicePolicyManager` constant names on purpose, so the DPC can resolve by name); `appRestrictions[].installType`/`wifiConfig.securityType` become their enum `.name` strings verbatim.
  - `AndroidManagementPolicyTranslator` → Google's real `Policy` model: same 8 booleans via direct setters; `kioskMode` gets folded into the same `applications` list as `appRestrictions` (each allowed kiosk package becomes its own `ApplicationPolicy` with `installType="KIOSK"`), plus `setKioskCustomLauncherEnabled`; `wifiConfig` becomes an **untyped `Map<String,Any>`** in ONC format (flagged in code as unverified against a real device, since this field has no typed model on the client).

### `com.primeos.mdm.command`
Unified command model for both device types.
- `CommandType` = `LOCK | WIPE | REBOOT | RESET_PASSWORD | CLEAR_APP_DATA | REQUEST_DEVICE_INFO` (the DB column is plain TEXT with no CHECK constraint, specifically so new types never need a migration).
- `CommandStatus` = `PENDING | SENT | ACKNOWLEDGED | COMPLETED | FAILED | EXPIRED`. `ACKNOWLEDGED` and `EXPIRED` are never set anywhere in the codebase — dead values today.
- `CommandParams` — one struct for all six types, irrelevant fields just null/empty: `lockDurationSeconds?, newPassword?, resetPasswordFlags=[], wipeDataFlags=[], clearAppsDataPackageNames=[], requestDeviceInfoType?`.
- `Command` entity adds `payload` (JSONB, the params at issue time) and `resultData` (JSONB, free-form `Map<String,String>`, only ever populated via the DPC ack call's `resultData` field, meaningful today only for `REQUEST_DEVICE_INFO`).
- `POST /api/devices/{deviceId}/commands?commandType=X` (body = optional `CommandParams`) forks by device type:
  - **GMS**: requires `gmsDeviceResourceName` (else 400 `InvalidDeviceStateException`), translates via `GmsCommandTranslator`, dispatches **immediately** via `AndroidManagementService.issueCommand` (real Google API call), persisted already at `status=SENT`.
  - **Non-GMS**: no direct channel to the device — persisted at `status=PENDING`, delivered later on the device's next `/api/dpc/checkin`, which flips it to `SENT`.
- `GET /api/devices/{deviceId}/commands` → `CommandSummary` list (payload and resultData decoded to typed/map form), newest first.
- `GmsCommandTranslator` — internal `CommandType` names are a 1:1 identity match to Google's `Command.type` vocabulary. Per-type quirks: `LOCK` formats duration as `"${seconds}s"`, omits the field entirely if null. `RESET_PASSWORD` only sets `resetPasswordFlags` if non-empty. `WIPE` and `CLEAR_APP_DATA` **always** set their params object even if the list is empty (unlike `RESET_PASSWORD`'s guard). `REBOOT` sets only `type`, nothing else.

### `com.primeos.mdm.enterprise`
The GMS-side integration with the real Android Management API.
- `GmsEnterprise` (table `gms_enterprises`, one per org via `@OneToOne`): `organization, enterpriseName (unique), gcpProjectId, pubsubTopic?, serviceAccountSecretRef`.
- `GmsEnterpriseSignup` (table `gms_enterprise_signups`): bridges Google's two-step signup — `organization, signupUrlName, consumedAt?`. Needed because Google's callback redirect only ever carries `enterpriseToken`, never the original `signupUrlName`, so the pairing is self-persisted and looked up by org.
- **Enterprise signup flow**:
  1. `POST /api/organizations/{orgId}/gms-enterprise/signup-url` (admin) → `GmsEnterpriseService.startSignup`: checks no enterprise already exists (409 `GmsEnterpriseAlreadyExistsException`), calls `AndroidManagementService.createSignupUrl(callbackUrl)`, persists an unconsumed `GmsEnterpriseSignup`, returns `{"url": "..."}` for the admin to open in a browser.
  2. Admin completes Google's hosted sign-up UI.
  3. `GET /api/organizations/{orgId}/gms-enterprise/callback?enterpriseToken=...` (public, no admin JWT possible — Google's redirect can't carry one) → `GmsEnterpriseService.completeSignup`: finds the most recent unconsumed signup for the org (400 `NoPendingGmsSignupException` if none), calls `AndroidManagementService.createEnterprise(...)`, marks the signup consumed, persists the `GmsEnterprise` row.
  - **No GET status endpoint exists** to check whether an org already has a GMS enterprise — confirmed absent from the controller.
- `AndroidManagementService` — thin wrapper around the real Google client, methods: `createSignupUrl`, `createEnterprise`, `createEnrollmentToken`, `upsertPolicy` (a `policies.patch` full-replace, no updateMask — creates if absent), `issueCommand` (returns an async `Operation`), `deleteDevice` (wipes + unenrolls atomically in one call, unlike `issueCommand(WIPE)` which wipes but leaves the device enrolled).
- **Pub/Sub notification flow**: `GmsNotificationListener` subscribes only if `mdm.gcp.pubsub-subscription-id` is set (blank = listener stays off rather than failing to start). Every message → `GmsNotificationProcessor.process()`: **always** persists a raw `GmsNotificationEvent` row first (audit trail even on parse failure), tries to parse as `GmsPubSubNotification{notificationType?, device?, enterpriseId?, command?: Map}` (flagged in code as an untested/unverified shape — no generated model class exists for it), dispatches by `notificationType`: `STATUS_REPORT`/`ENROLLMENT`/`COMPLIANCE_REPORT` → updates `lastSeenAt` + flips `PROVISIONING→ACTIVE`; `COMMAND` → finds the device's most-recently-created `SENT` command (**best-effort, not an exact-id match** — also flagged as unverified) and marks it `COMPLETED`/`FAILED` based on whether an `errorCode` is present. Any processing exception is caught and recorded on the event row, not rethrown — one bad message never blocks the subscription.

---

## 3. Full endpoint reference

### Public (no auth at all)
| Method | Path |
|---|---|
| POST | `/api/auth/login` |
| POST | `/api/dpc/enroll` |
| GET | `/api/organizations/{orgId}/gms-enterprise/callback` |

### Device-authenticated (Bearer device API key, via `DeviceAuthenticationFilter`)
| Method | Path |
|---|---|
| POST | `/api/dpc/checkin` |
| POST | `/api/dpc/commands/{commandId}/ack` |

### Admin-authenticated (Bearer admin JWT)
| Method | Path | Min. role |
|---|---|---|
| POST | `/api/organizations` | SUPER_ADMIN |
| GET | `/api/organizations` | SUPER_ADMIN |
| GET | `/api/organizations/{id}` | any |
| POST | `/api/admin-users` | SUPER_ADMIN |
| GET | `/api/admin-users` | SUPER_ADMIN |
| POST | `/api/admin-users/{id}/deactivate` | SUPER_ADMIN |
| GET | `/api/organizations/{orgId}/admin-users` | any (org-scoped) |
| GET | `/api/organizations/{orgId}/devices` | any (org-scoped) |
| GET | `/api/devices/{id}` | any (org-scoped) |
| PUT | `/api/devices/{id}/policy` | ORG_ADMIN+ |
| PUT | `/api/devices/{id}/display-name` | ORG_ADMIN+ |
| DELETE | `/api/devices/{id}` | ORG_ADMIN+ |
| POST | `/api/devices/{id}/commands` | ORG_ADMIN+ |
| GET | `/api/devices/{id}/commands` | any (org-scoped) |
| POST | `/api/organizations/{orgId}/policies` | ORG_ADMIN+ |
| GET | `/api/organizations/{orgId}/policies` | any (org-scoped) |
| GET | `/api/policies/{id}` | any (org-scoped) |
| PUT | `/api/policies/{id}` | ORG_ADMIN+ |
| POST | `/api/policies/{id}/sync` | ORG_ADMIN+ |
| DELETE | `/api/policies/{id}` | ORG_ADMIN+ |
| POST | `/api/organizations/{orgId}/gms-enrollment-tokens` | ORG_ADMIN+ |
| POST | `/api/organizations/{orgId}/non-gms-enrollment-tokens` | ORG_ADMIN+ |
| GET | `/api/organizations/{orgId}/enrollment-tokens` | any (org-scoped) |
| POST | `/api/enrollment-tokens/{id}/revoke` | ORG_ADMIN+ |
| POST | `/api/organizations/{orgId}/gms-enterprise/signup-url` | ORG_ADMIN+ |

("ORG_ADMIN+" = SUPER_ADMIN or ORG_ADMIN; ORG_VIEWER can never call these. "any (org-scoped)" = any of the three roles, restricted to their own org by `AdminAccessGuard`.)

---

## 4. Two independent auth mechanisms

1. **Admin JWT** (`AdminAuthenticationFilter` + `SecurityConfig`'s `authorizeHttpRequests`) — role-based, integrated with Spring Security's `Authentication`/`SecurityContextHolder`. Principal type is `AdminTokenClaims(adminUserId, role, organizationId?)`.
2. **Device credential** (`DeviceAuthenticationFilter`) — a flat Bearer API key, hashed with SHA-256, checked against `devices.credential_hash`, stashed as a plain request attribute (`authenticatedDevice`), **not** wired into Spring Security's `Authentication` at all — no roles, no principal object. Scoped only to `/api/dpc/**` minus `/enroll`.

They're deliberately kept separate because they protect completely different trust models: a human with a password vs. a device with a random high-entropy key. This is also why admin passwords use slow bcrypt while device keys use fast SHA-256 — bcrypt's slowness defends against brute-forcing a low-entropy human-chosen secret; a 256-bit random key has no such weakness for a fast hash to fail to protect.

---

## 5. Full current database schema (V1–V14)

| Table | Key columns | Notable constraints |
|---|---|---|
| `organizations` | name, slug (unique), status | CHECK status IN (ACTIVE, SUSPENDED) |
| `admin_users` | organization_id (FK, CASCADE, nullable), email (unique), password_hash, role, is_active | CHECK role IN (3 values) |
| `gms_enterprises` | organization_id (FK, CASCADE, **unique** — true 1:1), enterprise_name (unique), gcp_project_id, service_account_secret_ref | |
| `policies` | organization_id (FK, CASCADE), name, version, is_active, definition (JSONB) | UNIQUE(organization_id, name); GIN index on definition |
| `policy_revisions` | policy_id (FK, CASCADE), version, definition (JSONB), change_note | UNIQUE(policy_id, version) — append-only |
| `enrollment_tokens` | organization_id (FK, CASCADE), device_type, token_value (unique), default_policy_id (FK, RESTRICT), max_uses, used_count, status, claimed_by_device_id (FK→devices, RESTRICT) | CHECK device_type/status enums |
| `devices` | organization_id (FK, CASCADE), device_type, status, policy_id (FK, RESTRICT), enrollment_token_id (FK, RESTRICT), gms_device_resource_name (unique), device_uid (unique), credential_hash (unique) | CHECK: GMS rows need gms_device_resource_name XOR NON_GMS rows need device_uid |
| `commands` | organization_id (FK, CASCADE), device_id (FK, CASCADE), command_type (no CHECK), status, payload (JSONB), result_data (JSONB, V14) | CHECK status enum only |
| `gms_enterprise_signups` | organization_id (FK, CASCADE), signup_url_name, consumed_at | |
| `gms_notification_events` | notification_type, raw_payload (JSONB), processed_at, processing_error | audit log, always written even on parse failure |

Every FK to `admin_users` and `policies`/`enrollment_tokens` cross-references uses `ON DELETE RESTRICT` (Postgres default) — meaning you cannot hard-delete an admin user, policy, or enrollment token that's still referenced anywhere, which is precisely why deactivation/soft-delete is used everywhere instead of real DELETE for those entities. Only `organization_id` and `device_id`/`policy_id`(on `policy_revisions`) FKs cascade.

---

## 6. Command polling in the dashboard (recent addition)

`static/app.js`'s Device Detail view polls `GET /api/devices/{id}/commands` every 5 seconds while open (`setInterval`), replacing only the command-history table body — so status changes (`SENT → COMPLETED`) show up without a manual page reload. The timer is stopped on navigating away, on unenroll, and on logout, so only one poll ever runs at a time.

---

## 7. Exception → HTTP status map (`GlobalExceptionHandler`)

| Status | Exceptions |
|---|---|
| 400 | `InvalidDeviceStateException`, `InvalidEnrollmentTokenException`, `NoGmsEnterpriseException`, `NoPendingGmsSignupException`, `PolicyOrganizationMismatchException` |
| 401 | `InvalidCredentialsException` |
| 403 | `OrganizationAccessDeniedException` |
| 404 | `OrganizationNotFoundException`, `PolicyNotFoundException`, `DeviceNotFoundException`, `CommandNotFoundException`, `AdminUserNotFoundException`, `EnrollmentTokenNotFoundException` |
| 409 | `DuplicateSlugException`, `DuplicateEmailException`, `GmsEnterpriseAlreadyExistsException` |
| 500 | anything else (catch-all `Exception`) |

Bodies are always `{"error": "<message>"}` except `401`/`404` from `DeviceAuthenticationFilter` and `/api/dpc/**` auth failures, which are empty-bodied (the filter runs before this handler can ever see the request).

---

## 8. Known gaps / dead code (found during this read, not fixed)

- `OrganizationStatus.SUSPENDED` — no code path ever sets it; no suspend/reactivate endpoint.
- `DeviceStatus.INACTIVE` and `DeviceStatus.WIPED` — no setter anywhere.
- `CommandStatus.ACKNOWLEDGED` and `CommandStatus.EXPIRED` — no setter anywhere.
- No `GET` endpoint to check whether an org already has a GMS enterprise — the dashboard's GMS section can't show current status, only offer to start signup again (which would just 409).
- `AndroidManagementPolicyTranslator`'s Wi-Fi ONC JSON shape is untyped and explicitly flagged in code as unverified against a real enrolled device.
- `GmsPubSubNotification`'s shape has no generated model class backing it and is flagged as untested against a real notification.
- `GmsNotificationProcessor`'s command-reconciliation on a `COMMAND` notification matches by "most recently created `SENT` command for this device", not by exact command ID — best-effort, flagged as such in code.
- Non-GMS `qrCodeData` is a placeholder JSON, not a real Android Device Owner provisioning QR (needs APK hosting + signing checksum to become real — this was worked on separately with the Android app; see conversation history for the QR-provisioning design).
- `resultData` on commands is free-form `Map<String,String>` with no schema per `requestDeviceInfoType` — by design, but means nothing validates what a DPC actually sends back.

---

*Compiled from a fresh full read of every file in `src/main/kotlin`, every Flyway migration, and the dashboard frontend, on 2026-08-26.*
