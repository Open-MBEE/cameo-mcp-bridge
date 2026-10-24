# Cameo MCP Bridge 3.3.0 for 2022x and 2024x

This release adds a paired browser service hosted inside the Cameo plugin.
The existing native HTTP API and Python MCP client remain available, with an
explicit matching plugin/client version of 3.3.0.

## Downloads and installation

Choose the installer matching the Cameo installation:

- [2024x ZIP](https://github.com/Open-MBEE/cameo-mcp-bridge/releases/download/v3.3.0/cameo-mcp-bridge-3.3.0-2024x.zip): Java 17; default build target.
- [2022x ZIP](https://github.com/Open-MBEE/cameo-mcp-bridge/releases/download/v3.3.0/cameo-mcp-bridge-3.3.0-2022x.zip): Java 11; separate SDK build.

Install through Cameo's Resource/Plugin Manager and restart Cameo explicitly.
Do not install the wrong target or run both variants in one installation. Retain
the previously functioning installer for rollback. GitHub's source archives are
not plugin installers, and proprietary SDK libraries are not bundled.

Native Python clients must use the matching 3.3.0 package from this release's
source. The plugin-hosted browser path does not require a separate Python process.

## Changes

- A dedicated loopback browser listener starts with Cameo, normally on port 18741.
  Native API access remains on its separately configured port, normally 18740.
- The **Browser service** options group controls startup, port and allowed exact
  browser origins. A browser listener failure leaves the native API available.
- Pairing requires native Cameo approval. Sessions are origin-bound, held in
  memory, expire after 15 minutes and can be revoked. No codes or review login
  are required by this protocol.
- Typed project, selection/active-diagram, element, children and native-export
  operations reuse existing SDK handlers. Export handles belong to their session.
- Every additive creation apply requires independent native approval of the exact
  stored plan. Existing fingerprint, transaction, rollback and receipt checks
  remain authoritative. No automatic save occurs.
- The native API rejects direct browser requests. Native loopback Python calls
  bypass environment proxies. Arbitrary routes, macros, model saves, generic edits
  and deletes are not exposed through the browser service.
- Native dialog cancellation checks remain nonblocking on the UI thread. Pending
  cancellation, expiry, revocation and project changes prevent dispatch; uncertain
  dispatched outcomes use receipt recovery rather than automatic retries.
- Bridge documentation now describes generic contracts and configuration; client
  application workflows and deployment evidence live with their implementations.

## Validation and limits

Both target builds passed 57 Java tests, SDK linkage for all 94 production classes,
and installer descriptor/bytecode/dependency checks. The Python bridge suite passed
184 tests. See [validation](../development/browser-service-validation.md) and
[artifact checksums](../development/browser-service-evidence.json).

The user reported successful interactive use on 2024x. This is separate from
automated synthetic approval tests and is not a complete per-target failure-path
matrix. The 2022x browser capture/write paths and detailed native-dialog, Undo,
cancellation and uncertain-outcome acceptance still need separate live records.
Guarded creation remains experimental; qualify it on a disposable local project.
Publishing does not install/restart Cameo or modify any running model.

See the [browser-service contract](../development/browser-service.md) and
[build/release workflow](../development/plugin-releases.md). Previous releases
and their assets remain unchanged.
