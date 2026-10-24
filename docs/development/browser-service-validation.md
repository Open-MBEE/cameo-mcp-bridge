# Browser service validation (plugin 3.3.0)

This record covers the plugin and Python bridge client. Client application builds,
connection UX, browser deployment probes, conversion and staging are documented
with their owning applications.

## Automated results

- 57 Java tests per target, on Java 17 with the 2024x SDK and bundled Java 11
  with the 2022x SDK. Coverage includes native pairing and cancellation, exact
  origins, unpaired requests, typed validation, export ownership, expiry and
  revocation, project changes, exact-plan review, uncertain-write replay and
  nonblocking UI-thread cancellation checks.
- SDK linkage resolved all 94 production bridge classes on both targets without
  initializing the Cameo application or opening a model. The target-specific
  generic-table wait API difference remains explicit.
- Both 3.3.0 installer ZIPs passed CRC, descriptor/version/target, resource-path,
  Java bytecode and dependency-exclusion verification. SDK libraries are not
  bundled. Artifact names and SHA-256 hashes are recorded in
  [browser-service-evidence.json](browser-service-evidence.json).
- All 184 Python bridge tests passed. Two temporary-file workflow tests initially
  hit sandbox file restrictions; the full suite passed outside those restrictions.

`BrowserBridgeTest`, `BrowserHttpServerTest` and `NativeBrowserBackendTest` check
the browser boundary and native dispatch with synthetic backends. Their approval
callback does not exercise a human dialog or mutate a running Cameo model.
The existing export/creation tests cover SDK-facing contract and transaction logic.

## Live acceptance and release scope

The user subsequently reported successful interactive use of the 2024x build.
This is separate from the automated results and is not a recorded per-target
failure-path matrix. Native dialog focus, capture, approved creation, cancellation,
Undo and receipt recovery still require target-specific acceptance records before
a release claims those paths. The earlier 2022x read-only qualification does not
qualify every new browser/write path.

Automated validation did not install/restart the plugin, open a model or mutate
the running installation. Retain the previously functioning installer while
qualifying an upgrade. A browser listener failure must leave the native API
available; test port conflicts and disabled-browser configuration as part of
deployment acceptance.

See the [browser-service contract](browser-service.md),
[native export contract](native-model-export.md),
[guarded creation contract](change-plans.md), and
[build and release workflow](plugin-releases.md).
