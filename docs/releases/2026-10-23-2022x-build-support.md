# Additional 2022x build target (3.1.0 source)

The bridge now builds separate 2022x and 2024x installers from the same source.
2024x remains the default. Both targets passed basic live read-only smoke tests.
2022x remains experimental for native export and guarded-write workflows pending
separate live qualification. Version 3.1.0 is packaged as one GitHub release
with separate 2022x and 2024x installer ZIPs; the 3.0.0 release is retained.

The 2022x target uses Java 11 bytecode and the SDK's Gson 2.2.4. Shared code uses
Java 11-compatible constructs and JSON operations that also work with the 2024x
SDK's Gson 2.9.0. Both variants retain the 3.1.0 bridge/client contract, port
option, write guards, model consistency checks, and transaction boundaries.

Target selection validates SDK descriptors before compilation. Generated
installer descriptors and build metadata identify the target, and output
directories are isolated under `plugin/build/2022x/` and `plugin/build/2024x/`.
The 2022x installer bounds both minimum and maximum host versions to 2022x;
Resource Manager behavior for refresh/hotfix variants still needs live testing.
The plugin also checks the exporter version before starting the 2022x bridge.

The 2022x table API has no `waitForCellValuesLoad`. The compatibility adapter
preserves 2024x waiting and exposes a loading warning on 2022x table responses.
Capabilities identify this limitation and the experimental qualification status.
Unknown build targets are rejected by the Python client; older matching 2024x
metadata without platform fields remains accepted.

## Validation

- 38 plugin tests passed on the 2022x installation's Java 11.0.14.1 runtime.
- 38 plugin tests passed on Java 17 against the 2024x Refresh2 SDK.
- Both SDK linkage probes resolved all 83 bridge classes without initializing
  Cameo or a project; the table wait API was absent/present respectively.
- 117 Python client/server tests passed, including platform negotiation.
- Both installer ZIPs passed descriptor, resource path, embedded target,
  bytecode, CRC, and dependency-exclusion verification.
- Unknown targets, missing SDKs, and mismatched SDKs were refused; omitted
  target selection retained 2024x. Installer shell syntax and diff checks passed.
- The original working 3.1.0 2024x ZIP remains unchanged at SHA-256
  `c220196a61c40f99b5cceee9bca5f6704cca4caeaf31e91f01c5a765df5e5f12`.

ZIPs used for the successful basic live smoke tests (release rebuild checksums
are recorded on the GitHub release):

| Target | ZIP under repository root | SHA-256 |
| --- | --- | --- |
| 2022x | `plugin/build/2022x/cameo-mcp-bridge-3.1.0-2022x.zip` | `74c837bbf06281b7481903206267dc07ac5bc5b1287b4d2f05d5c18a9569c584` |
| 2024x | `plugin/build/2024x/cameo-mcp-bridge-3.1.0-2024x.zip` | `3a900f5c709cd8d1e75a25cfb339df97ab09a4d9197409e9ceda932108f88692` |

## Basic live smoke results

Both platforms passed all 11 read-only checks against the shared-source 3.1.0
build: version/target, Python compatibility, project access, UI state, active
diagram, selection, root lookup, bounded element query, diagram listing, and
unchanged project identity/dirty state. The 2022x runtime reported Java
11.0.14.1; 2024x reported Java 17.0.12. Both listened on port 18740. The user
performed installation/restart; the tests performed no writes or saves.

Sanitized evidence (without project names, element IDs, or model contents) is
in [the live smoke record](validation/3.1.0-live-smoke.json).

Native export/read/release, guarded creation/rollback, port persistence after
changing the option, and other refresh/hotfix variants are not qualified by
these basic checks. The 2022x table-loading limitation remains documented.

See [build/release commands](../development/plugin-releases.md#additional-2022x-build)
and the [implementation and acceptance plan](../plans/2022x-plugin-support.md).
