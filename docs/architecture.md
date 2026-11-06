# Architecture and current limitations

Android-only native application (Kotlin + Android AppWidget RemoteViews). No WebView or dependency on FinWorkspace Mobile.

Existing FinWorkspace endpoints:
- POST /api/auth/login
- POST /api/auth/refresh?refresh_token=...
- GET /api/accounts/?limit=100
- GET /api/transactions/?page=1&per_page=8

Security: HTTPS is required by the login UI and Android cleartext traffic is disabled. Access and refresh tokens are encrypted with a non-exportable Android Keystore AES-GCM key. Do not store passwords or JWTs in widget views.

Initial prototype limitations:
- One global widget mode and selected account shared by all widget instances. Independent per-widget configuration is next.
- Transactions currently display raw amounts without account currencies; improve by matching account_id to accounts.
- Account list is limited to first 100 accounts.
- No offline cached balances; network errors are shown.
- A refresh tap updates all widgets; background work uses WorkManager and Android may defer it.
- Debug APK from CI is for testing; release signing is not configured.
