# Security — ValueLens

---

## Core Security Model

ValueLens is built on a **local-first, structured-only** security model:

1. Raw screen content never leaves the device
2. The cloud API receives only structured JSON produced by the on-device parser
3. User preferences and history are stored on-device in an encrypted database
4. No server-side storage of per-user data in V1

This model is not just good privacy practice — it is a user trust requirement. The app reads the screen. Users will not use an app they do not trust with that capability.

---

## Data Classification

| Data Type | Where Stored | Encrypted | Sent to Cloud |
|-----------|-------------|-----------|---------------|
| Raw screenshot | Never stored | — | Never |
| Accessibility tree content | Never stored | — | Never |
| Parsed product structs | In-memory only | — | Yes, anonymized |
| Query text (natural language) | On-device (Room) | Yes (SQLCipher) | Structured intent only |
| User preferences | On-device (Room) | Yes (SQLCipher) | Embedded in AI request |
| Query history | On-device (Room) | Yes (SQLCipher) | Never |
| App usage analytics (V2+) | PostHog | Yes (transit) | Anonymized events only |

---

## Threat Model

### Threat 1 — Malicious screen reader (data exfiltration)

**Risk:** The Accessibility Service could, in theory, capture sensitive content (banking apps, OTPs, personal messages) and send it to a server.

**Mitigation:**
- App explicitly declares a blocklist of package names where the Accessibility Service will not activate (banking apps, UPI apps, keyboard apps, messaging apps)
- The service only activates when the foreground app is in the allowed shopping app list
- Network calls never include raw text from the Accessibility tree

**Allowed app list (V1):**
`in.swiggy.instamart`, `com.zeptoconsumerapp`, `com.grofers.customerapp`, `in.amazon.mShop.android.shopping`, `com.jio.jiomart`

### Threat 2 — API key exposure

**Risk:** If the Claude API key is embedded in the APK or transmitted insecurely, it can be extracted and abused.

**Mitigation:**
- The API key lives only on the backend server (Go service)
- The Android app authenticates to the ValueLens backend using a short-lived JWT issued at app start
- The backend proxies all Claude API calls — the app never calls the Claude API directly
- JWT is bound to the device's Android ID (one device per token)

### Threat 3 — Man-in-the-middle on API traffic

**Risk:** Network interception of product data or AI responses.

**Mitigation:**
- All API traffic uses TLS 1.3
- Certificate pinning on the ValueLens backend endpoint using OkHttp's `CertificatePinner`
- Backend uses HSTS

### Threat 4 — Local database exfiltration

**Risk:** If the device is compromised, stored preferences and query history could be accessed.

**Mitigation:**
- Room database encrypted with SQLCipher
- Encryption key derived from Android Keystore (hardware-backed on devices that support it)
- Key is not stored in shared preferences or any file

### Threat 5 — Overlay clickjacking

**Risk:** The overlay could be used to intercept touches intended for the underlying app.

**Mitigation:**
- Overlay uses `FLAG_NOT_TOUCHABLE` when in minimized/floating-button state
- In expanded state, only the panel area is touch-intercepting
- Touch events outside the panel are passed through to the underlying app via `FLAG_NOT_TOUCH_MODAL`

---

## Android Permission Security

Each permission is requested with a justification dialog before the system prompt appears. Users can revoke permissions at any time from system settings. See `PERMISSIONS.md` for the full permission list with rationale.

The Accessibility Service declaration in `AndroidManifest.xml` includes:
```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowContentChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:packageNames="in.swiggy.instamart,com.zeptoconsumerapp,..."
    />
```

`packageNames` restricts the service to only the declared apps. The OS enforces this — it is not application-level logic.

---

## Play Store Compliance

Google's Accessibility API policy (last reviewed 2025) requires:

- The app must use Accessibility only for accessibility-assistive purposes or features that cannot be implemented another way
- The declared use must be prominently disclosed to users
- Apps must not use Accessibility to perform actions not initiated by the user

ValueLens complies by:
- Displaying a clear pre-permission disclosure screen explaining exactly what the service reads and does not read
- Ensuring all actions (sort, filter) are only executed after an explicit user query
- Not capturing content from non-commerce apps
- Including a link to the privacy policy on the disclosure screen

---

## Security Checklist by Version

### V1
- [x] Allowed app allowlist for Accessibility Service
- [x] No raw screen data in network requests
- [x] SQLCipher encrypted local storage
- [x] Backend-proxied AI calls (no API key in APK)
- [x] Certificate pinning
- [x] Overlay touch passthrough
- [x] Permission disclosure screen

### V2
- [ ] Anonymized analytics (PostHog, self-hosted)
- [ ] Cross-app price data: local only, never synced to cloud
- [ ] Audit log UI: user can see what the app has accessed

### V3
- [ ] Agent mode: every autonomous action requires explicit user confirmation step
- [ ] Action audit trail stored locally with tamper-evident log
- [ ] Rate limiting on agent operations to prevent accidental purchase loops
