# Cameo MCP Bridge 3.3.1 — 2024x and 2022x

Adds Browser session duration (minutes) to Environment Options → Cameo MCP Bridge
→ Browser service. Default 60 minutes; configurable 15–480. Restart Cameo after
changing. Sessions retain an absolute expiry, origin binding and revocation;
independent creation approval still expires after two minutes. The native dialog
and pairing expiresIn response reflect the configured lifetime.

Install only the ZIP matching your Cameo target. Native Python clients and other
exact-version consumers must be updated to expect 3.3.1. Existing 3.3.0 releases
remain available. Build/SDK linkage verification is separate from live acceptance;
no running Cameo instance was restarted or modified during validation.

## Validation

- 2022x: 60 plugin tests, SDK linkage on Java 11 and installer verification passed.
- 2024x: 60 plugin tests, SDK linkage on Java 17 and installer verification passed.
- Python server/client: 118 focused tests passed.
- 2022x ZIP SHA-256: `24a65f6b746d957a0f89d69d3e713c495b0ee8dc8cc530dac1f87ab2ef0fcd48`.
- 2024x ZIP SHA-256: `c922648d45144731b02e74c981dd8157f472725f3c5c6aaa1a84dabedb5c405d`.

Published as a prerelease while the feature PR awaits merge. Live installation,
Environment Options UI and session acceptance must be checked separately.
