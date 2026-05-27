# Replit Environment Notes

This file documents Replit-specific configuration quirks learned during development.

## Run Button Visibility

The **Run** button at the top center of the Replit UI only appears when the `.replit` file contains a `run = "..."` line. Without it, the button is hidden entirely.

**Fix:** Add to `.replit`:

```
run = "PYTHONPATH=server python main.py"
```

## Nix Modules

Replit's Nix module system does not support mixing Python and Node.js modules simultaneously on the `stable-25_05` channel. To run both a Python backend and an Expo dev server:

- **Backend**: Start via the Run button or `PYTHONPATH=server python main.py`
- **Expo**: Start manually from a Shell tab with `cd mobile && npx expo start`

Do not add `nodejs-20` to the `modules` line in `.replit` if Python is already there — it causes a Nix build failure.

## Nix Channel

The Expo template uses `stable-24_05` channel. This project uses `stable-25_05`. These are incompatible for combined workflows. Keep modules minimal — use Shell for secondary servers.