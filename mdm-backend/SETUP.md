# MDM Backend — Setup Guide

Full steps to get this backend running from scratch on a new machine. Verified against the actual running instance, not written from memory.

---

## 1. Prerequisites

- **Java 21** (the Gradle toolchain requires it)
- **PostgreSQL** running locally (or reachable) on port `5432`
- **Gradle** — not required separately, the repo includes the wrapper (`./gradlew`)
- `keytool` (ships with the JDK) and `openssl` — only needed once, to generate the local HTTPS keystore
- `psql` client — for the one-time database bootstrap steps below

---

## 2. Create the database

The backend expects a database named `mdm`, owned by a user `mdm` with password `mdm` (see `application.yml` — override via env vars if you want different credentials, see §5).

```bash
sudo -u postgres psql -c "CREATE USER mdm WITH PASSWORD 'mdm';"
sudo -u postgres psql -c "CREATE DATABASE mdm OWNER mdm;"
```

Flyway owns the schema from here — every migration in `src/main/resources/db/migration/` (`V1` through `V15` as of this writing) runs automatically the first time the app boots. You never run migrations manually.

---

## 3. Secrets directory

Create a `secrets/` folder at the project root (`mdm-backend/secrets/`) — it's gitignored, never committed. Two files go here:

### 3a. Android Management API service account
`secrets/android-management-sa.json` — a GCP service account key JSON with the Android Management API enabled on its project. This is what lets the backend call Google's real API (enterprise creation, policy sync, enrollment tokens, commands). Without this file, the app still boots, but every GMS-related call fails.

### 3b. Local HTTPS keystore
The backend runs HTTPS-only, even in local dev — Google's enterprise-signup callback rejects plain `http://` URLs outright. Generate a self-signed cert once:

```bash
keytool -genkeypair -alias mdm-backend-dev -keyalg RSA -keysize 2048 -validity 3650 \
  -keystore secrets/dev-keystore.p12 -storetype PKCS12 \
  -storepass changeit -keypass changeit \
  -dname "CN=localhost, OU=Dev, O=PrimeOS, L=Unknown, ST=Unknown, C=US"
```

This matches exactly what `application.yml` expects (`key-alias: mdm-backend-dev`, `key-store-password: changeit`). Because the cert is self-signed, any browser hitting `https://localhost:8080` will show a warning — click through it. If you later connect an Android app to this backend, its `network_security_config.xml` needs this same cert added as a trust anchor (export it with `keytool -exportcert -alias mdm-backend-dev -keystore secrets/dev-keystore.p12 -storepass changeit -rfc -file dev-cert.pem`).

---

## 4. Environment variables

All of these have working local-dev defaults baked into `application.yml` — you only need to export them to override those defaults (e.g. for anything beyond local testing). The one you'll set almost every time you start the backend locally is `MDM_GCP_PROJECT_ID`, since Spring Cloud GCP's own credential auto-configuration needs it explicitly or it logs (harmless but noisy) warnings.

| Variable | Default | Purpose |
|---|---|---|
| `MDM_GCP_PROJECT_ID` | *(blank)* | Your GCP project ID (must match the service account's project) |
| `MDM_GCP_CREDENTIALS_PATH` | `file:secrets/android-management-sa.json` | Path to the service account JSON |
| `MDM_CALLBACK_BASE_URL` | `https://localhost:8080` | Base URL Google redirects back to after enterprise sign-up |
| `MDM_GCP_PUBSUB_SUBSCRIPTION_ID` | *(blank)* | Pub/Sub subscription for GMS device-state notifications; blank = listener stays off |
| `MDM_DPC_CHECK_IN_INTERVAL_SECONDS` | `60` | How often non-GMS devices are told to poll `/api/dpc/checkin` |
| `MDM_DPC_DEVICE_ADMIN_COMPONENT_NAME` | *(blank)* | Non-GMS DPC's real `DeviceAdminReceiver` component name — needed only for the real (non-placeholder) Device Owner QR |
| `MDM_DPC_APK_DOWNLOAD_URL` | *(blank)* | Public HTTPS URL to download the non-GMS DPC APK during provisioning |
| `MDM_DPC_APK_SIGNATURE_CHECKSUM` | *(blank)* | Base64 SHA-256 checksum of that APK's signing cert |
| `MDM_JWT_SECRET` | a well-known placeholder | HMAC key for admin JWTs — **must** be overridden outside local dev |
| `MDM_JWT_EXPIRATION_MINUTES` | `480` | Admin JWT lifetime (8 hours) |

---

## 5. Bootstrap the first SUPER_ADMIN

There is **no seed migration and no bootstrap endpoint** — every `admin_users` row is normally created via `POST /api/admin-users`, but that endpoint itself requires an existing `SUPER_ADMIN` JWT to call. The very first account has to be inserted directly into the database.

Generate a real bcrypt hash for your chosen password (the app uses `BCryptPasswordEncoder`, so a plaintext password will never work):

```bash
python3 -c "import bcrypt; print(bcrypt.hashpw(b'YourChosenPassword', bcrypt.gensalt()).decode())"
```

Then insert the row (replace the email and the hash with your own):

```sql
INSERT INTO admin_users (id, organization_id, email, password_hash, role, is_active)
VALUES (gen_random_uuid(), NULL, 'super@yourdomain.com', '<paste the bcrypt hash here>', 'SUPER_ADMIN', true);
```

`organization_id = NULL` is what makes this a platform-level SUPER_ADMIN rather than an org-scoped admin — never set an organization on this row.

---

## 6. Start the backend

```bash
cd mdm-backend
export MDM_GCP_PROJECT_ID=<your-gcp-project-id>
./gradlew bootRun
```

Wait for `Started MdmBackendApplicationKt` in the console. It listens on `https://localhost:8080`.

---

## 7. Verify it's up

```bash
curl -k -X POST https://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"super@yourdomain.com","password":"YourChosenPassword"}'
```

A `200` with a `token` field confirms everything — database, migrations, HTTPS, and your bootstrapped admin account — is working. (`-k` is only because of the self-signed dev cert; drop it once you're on a real certificate.)

---

## 8. Use the dashboard

Open `https://localhost:8080` in a browser (click through the self-signed cert warning) and log in with the same credentials. From there: create an organization, then policies, devices, and enrollment tokens, all from the UI.

---

## 9. Run the test suite

```bash
./gradlew test
```

One test (`AndroidManagementServiceIT`) hits the real Google API and will fail without valid, non-placeholder GCP credentials configured — that's expected in a sandboxed/offline environment and isn't a sign anything else is broken.

---

## 10. GMS Migration Tokens (DPC → AMAPI device migration)

Separate from a fresh GMS enrollment token. This is for a device that's currently Device Owner under our own **non-GMS custom DPC** and needs to migrate to being managed for real via the Android Management API / Google's Android Device Policy app, using Google's documented `enterprises.migrationTokens.create` flow.

**Mint one:**
```
POST /api/devices/{deviceId}/gms-migration-token
```
```json
{
  "playDeviceId": "<from the device's own AccountSetupClient flow>",
  "playUserId": "<from the device's own AccountSetupClient flow>",
  "policyId": "<our internal Policy UUID>",
  "ttlSeconds": null,
  "additionalData": null
}
```
Also reachable from the dashboard: open the device's detail page → **GMS Migration (advanced)** section (only shown for `NON_GMS` devices).

**The hard constraint** (verified against the real AMAPI SDK jar): `playDeviceId`/`playUserId` are **not** something our backend can generate or look up — they only exist as the output of a successful on-device `AccountSetupClient.startAccountSetup()` round trip (itself requires a regular enrollment token first, see below). The caller must supply them; this endpoint will happily accept placeholder values and forward them to Google, which will reject them with its own real error.

**List what's been created:**
```
GET /api/organizations/{organizationId}/gms-migration-tokens
```
Also shown as a table on the dashboard's Enrollment Tokens page ("GMS Migration Tokens"). Every successfully-minted token is persisted (`gms_migration_tokens` table, `V15` migration) — a token is only recorded once Google has actually accepted it, same discipline as every other Google-backed resource in this app.

---

## 11. Known local-dev gotchas (learned the hard way)

- **GMS enrollment tokens expire fast** (~15 min–1 hr) — if a QR/token sits unused for a while, just issue a fresh one; don't assume an old one is still valid even if the dashboard still shows it as `ACTIVE` (nothing proactively flips that status on expiry, it's only checked at actual enroll time).
- **The dashboard's "One-time only" checkbox for GMS tokens defaults to checked.** That's a real `oneTimeOnly: true` sent straight to Google, not just internal bookkeeping — Google's real one-time tokens become invalid after a single successful redemption. If a flow touches Google's servers more than once during setup (e.g. `AccountSetupClient`'s multi-step account-linking), a still-fresh, non-expired token can still come back `FAILURE_REASON_ENROLLMENT_TOKEN_INVALID` / HTTP 403 on a later step. Uncheck it (or pass `oneTimeOnly: false`) for any flow that might redeem the token more than once.
- **Restarting the backend is required after any code or static-resource change** — there's no hot reload; kill the `bootRun` process and start it again.
- **GMS device provisioning may require Google's Android Enterprise EMM certification** to work beyond a very limited testing tier — see the project's own notes on the "usage limit reached" issue if you hit that wall. This same restriction can also surface much later in the flow, after token validation and device registration both succeed, as `com.google.android.gms.common.api.ApiException: 13` / `BAD_AUTHENTICATION` when Google Play Services tries to add the managed Google Play ("work") account to the device — that's not a config bug on either side, it's the same certification wall showing up one layer deeper.
- **Non-GMS device testing over adb**: if testing against a physical device via `adb`, remember `adb reverse tcp:8080 tcp:8080` needs to be re-run any time the adb connection drops.
