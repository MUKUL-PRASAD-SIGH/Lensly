# Permissions — ValueLens

---

## Philosophy

Every permission ValueLens requests has a direct, visible user benefit. The app will never request permissions speculatively or for future use. Each permission is explained to the user in plain language before the system dialog appears.

---

## Permission List

### 1. BIND_ACCESSIBILITY_SERVICE

**Type:** Special permission (Accessibility Settings, not runtime dialog)

**Why it is needed:**
This is the core capability of ValueLens. The Accessibility Service reads the structured UI tree of the shopping app currently on screen — product names, prices, weights, and interactive controls like sort and filter buttons. Without this, the app cannot read the screen.

**What it does NOT do:**
- Does not read banking apps, UPI apps, keyboard input, or messages
- Only activates when one of the declared shopping apps (Zepto, Blinkit, etc.) is in the foreground
- Does not store or transmit raw screen content

**User disclosure text:**
> "ValueLens reads product information from shopping apps you're currently viewing. It only activates on apps like Zepto and Blinkit, never on your banking, messaging, or payment apps."

---

### 2. SYSTEM_ALERT_WINDOW (Display over other apps)

**Type:** Special permission (separate settings page)

**Why it is needed:**
The overlay panel and floating button appear on top of the shopping app without switching away from it. This requires the `SYSTEM_ALERT_WINDOW` permission, which allows drawing over other apps.

**What it does NOT do:**
- Does not intercept touch events outside the overlay panel
- Does not appear on the lock screen
- Does not appear over banking or system apps

**User disclosure text:**
> "ValueLens shows a small floating button and result panel on top of your shopping app so you can get recommendations without leaving the app."

---

### 3. FOREGROUND_SERVICE

**Type:** Normal permission (declared in manifest, no user dialog)

**Why it is needed:**
The Accessibility Service and overlay must remain active while the user is inside a shopping app. A foreground service with a persistent notification keeps the process alive and prevents Android from killing it due to battery optimization.

**What the notification shows:**
> "ValueLens is active — tap to open or disable"

The notification is minimal and dismissible by disabling the service in settings.

---

### 4. MEDIA_PROJECTION (Screen capture)

**Type:** Runtime permission (per-session dialog on Android 14)

**Why it is needed:**
Used only as a fallback when the Accessibility tree cannot provide sufficient product data. Takes a single screenshot of the current screen for OCR processing.

**What it does NOT do:**
- Does not record video or continuous screenshots
- Does not capture the screen when ValueLens is not actively processing a query
- The captured bitmap is processed in-memory and never written to disk or sent to a server

**User disclosure text:**
> "For some apps, ValueLens needs to take a screenshot to read product information. The screenshot is never saved or shared — it is analyzed instantly on your device."

---

### 5. INTERNET

**Type:** Normal permission (no user dialog)

**Why it is needed:**
Required to call the ValueLens backend when a complex query needs AI reasoning. The backend proxies the AI call — the app does not directly call any external AI service.

---

### 6. RECEIVE_BOOT_COMPLETED

**Type:** Normal permission (no user dialog)

**Why it is needed:**
Allows the floating button and accessibility service to restart after device reboot without requiring the user to manually open the app.

**Only activates if:** The user had the service enabled before the reboot.

---

### 7. VIBRATE

**Type:** Normal permission (no user dialog)

**Why it is needed:**
A short haptic feedback pulse when the overlay panel opens, confirming the shortcut was recognized. Standard UX for overlay triggers.

---

## Permissions NOT Requested

The following permissions are explicitly excluded and will never be requested:

| Permission | Why excluded |
|-----------|-------------|
| READ_CONTACTS | Not needed |
| READ_CALL_LOG | Not needed |
| ACCESS_FINE_LOCATION | Not needed (no location-based features in V1) |
| READ_SMS | Not needed; OTPs are never captured |
| CAMERA | Not needed |
| RECORD_AUDIO | Not needed in V1; voice input uses on-device SpeechRecognizer which does not require RECORD_AUDIO in the same way |
| READ_EXTERNAL_STORAGE | Screenshots are processed in-memory, never written to storage |

---

## Permission Flow (User Experience)

```
App first launch
    ↓
Onboarding screen 1: "What ValueLens does" (plain language)
    ↓
Onboarding screen 2: "Permissions needed and why"
    ↓
"Grant Screen Reading" button → opens Accessibility Settings
    ↓
User enables ValueLens in Accessibility Settings
    ↓
Return to app → "Grant Display Over Apps" button → opens Settings
    ↓
User enables display over other apps
    ↓
"Everything is set. Open Zepto and tap the floating button."
```

Neither permission is requested via a dialog pop-up. Both are handled via deep links to the relevant settings pages, which gives the user full context before they decide.

---

## Revoking Permissions

Users can revoke any permission at any time:
- Accessibility: Settings → Accessibility → ValueLens → toggle off
- Display over apps: Settings → Apps → ValueLens → Display over other apps → toggle off

When either permission is revoked, the app detects this on next foreground and shows a non-intrusive banner: "ValueLens needs screen reading permission to work. Tap to re-enable."
