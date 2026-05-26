---
name: Expo native mode on Replit
description: How to run Expo Metro bundler in native mode so Replit's built-in Android emulator preview connects correctly.
---

## Rule
Use `REPLIT_EXPO_DEV_DOMAIN` (not `REPLIT_DEV_DOMAIN`) as the Metro hostname for Replit's Android emulator preview.

## Correct workflow command
```bash
cd mobile && REACT_NATIVE_PACKAGER_HOSTNAME=$REPLIT_EXPO_DEV_DOMAIN EXPO_PACKAGER_PROXY_URL=https://$REPLIT_EXPO_DEV_DOMAIN npx expo start --port 8080
```

- outputType: "console"
- waitForPort: 8080
- No `--web` flag (native mode required for camera and other device APIs)

**Why:** Replit exposes two env vars:
- `REPLIT_DEV_DOMAIN` — the standard web preview domain (port 5000 proxy)
- `REPLIT_EXPO_DEV_DOMAIN` — a dedicated `*.expo.worf.replit.dev` subdomain that the Replit Android emulator uses to reach the Metro bundler

Without `REPLIT_EXPO_DEV_DOMAIN`, Metro advertises an internal LAN IP that the emulator cannot reach. `EXPO_PACKAGER_PROXY_URL` tells Expo to rewrite bundle URLs to use the HTTPS proxy domain.

**How to apply:** Any Expo project in Replit that needs the native Android emulator preview should use this pattern. Port 8080 is supported by Replit's infrastructure. Port 8081 (Expo default) is NOT in Replit's supported port list.
