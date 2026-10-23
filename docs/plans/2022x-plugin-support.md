# Additional 2022x plugin build

Status: implemented on `feat/2022x-plugin-support`; 2022x is experimental
pending native export and guarded-write qualification. Both targets have passed
basic live read-only smoke checks (11/11 each). The shared 3.1.0 source builds
separate 2022x/Java 11 and 2024x/Java 17 installers. Existing published artifacts
and the running 2024x installation remain unchanged.

## Implementation results

- Explicit SDK validation, generated platform descriptors/build metadata, and
  target-isolated output directories; 2024x remains the default.
- Java 11 source backport and compatibility with each host's bundled Gson.
- Both platform unit suites pass, including JSON copy/number preservation,
  digest formatting, guarded creation receipts, export handles, and actual JVM
  version checks. Both SDK linkage probes resolve all 83 bridge classes.
- The one SDK method gap found by compilation was
  `GenericTableManager.waitForCellValuesLoad`. A narrow adapter preserves the
  2024x call and exposes a loading limitation on 2022x instead of claiming the
  same wait guarantee.
- Additive platform metadata in capabilities, target-aware Python negotiation,
  and a ZIP/bytecode/dependency verifier are implemented.
- Installation/load and basic live read-only smoke passed on both targets.
- Remaining acceptance: port persistence after changing its value,
  Resource Manager version bounds (including refresh/hotfix variants), and the
  separately authorized live read/export/guarded-write checks below. No models
  were opened, modified, or saved by the implementation validation.

Commands and output paths are in the
[release guide](../development/plugin-releases.md#additional-2022x-build).
The evidence and original implementation sequence below explain the decisions.

## Evidence

The local SDK is `C:/Program Files/Magic Systems of Systems Architect`.
Its Eclipse UML2 v5 exporter, Diagram Table, and Relation Map descriptors all
report `2022x`. Its bundled JVM is Temurin 11.0.14.1+1. This agrees with the
[vendor's 2022x Java recommendations](https://docs.nomagic.com/plugins/viewsource/viewpagesrc.action?pageId=249579883).
The 2024x baseline uses `C:/Program Files/MSOSA2024xR2` and Java 17.

An isolated compile probe of current source against the 2022x libraries, still
using the current Java 17 target, failed with 107 errors (the compiler displayed
the first 100). Numerous errors concern Gson: 2022x ships `gson-2.2.4.jar`, while
the tested 2024x R2 installation ships `gson-2.9.0.jar`. Missing/inaccessible APIs
include primitive `JsonArray.add` overloads, `JsonParser.parseString/parseReader`,
`JsonObject.keySet/size`, `JsonArray.isEmpty`, and public `deepCopy`.
The failure is evidence of incompatibility, not a complete OpenAPI gap inventory.

The existing build sets `options.release = 17`. Java 17 classes cannot load in
the stock 2022x Java 11 runtime. Source inspection also found:

- Records in `NativeExportStore` and `PrimaryModelTraversal`.
- Pattern matching for `instanceof` in creation, diagram, table, and fingerprint code.
- A switch expression in `CreationPlanHandler`.
- `HexFormat` in hashing and `Stream.toList()` in export code.

Packaging is also fixed to 2024x: the three required plugins in `plugin.xml`
require `2024x`, and the resource descriptor sets `mdVersionMin="2024x"`.
Renaming the existing ZIP will not make it work on 2022x.

There are useful common APIs: the 2022x exporter descriptor includes UML2
5.0.2 and EMF 2.10.2 resource JARs matching the current resource lookup patterns.
Both SDKs ship the Environment Options example, and `NumberProperty` has the
bounded integer constructor used by the new listening-port setting. These are
positive signs, not runtime proof of the export or options lifecycle.

## Implementation sequence and live acceptance

1. **Protect 2024x first.** Develop on a separate branch. Keep 2024x as the
   default Gradle target and retain its existing dependency selection, Java 17
   output, plugin identity, and capability behavior. Keep baseline ZIP/checksum
   and test results. Require the 2024x build/tests to pass after each backport
   stage. Do not install or restart either Cameo instance during build checks.

2. **Add explicit build targets.** Introduce a validated `cameoTarget` property
   accepting only `2024x` and `2022x`, defaulting to `2024x`. Require a matching
   `cameoHome` and fail early for missing/wrong SDKs. Select Java release 17 or
   11 per target and isolate build output by target to prevent stale class/JAR
   reuse. Gradle may run on JDK 17 while compiling with `--release 11`; run
   2022x tests on an actual Java 11 runtime as a separate compatibility gate.
   Make test JVM selection platform-neutral instead of assuming `java.exe`.

3. **Resolve JSON compatibility before API work.** Prefer a small compatibility
   utility and Java source changes that use APIs available in Gson 2.2.4:
   explicit `JsonPrimitive`, parser instances, `entrySet`, size comparisons,
   and a tested recursive deep-copy helper. Preserve null/number/string types,
   deterministic hashes, and refusal behavior. Test with each SDK's Gson.
   If that becomes too invasive, evaluate a shaded/relocated private Gson as
   an alternative with license and classloader tests. Do not replace Cameo's
   Gson or bundle an unrelocated newer copy into the shared classloader.

4. **Backport Java constructs and map remaining API gaps.** Replace records
   with immutable classes, patterns with explicit casts, the switch expression
   with a statement, and newer hashing/stream helpers with Java 11 equivalents.
   Preserve fingerprint bytes, plan hashes, limits, handle expiry, and immutable
   inventories. Re-run compilation with all errors enabled against 2022x to
   produce the remaining SDK method/class inventory. Use narrow per-platform
   adapters only where an actual SDK difference demands one; do not fork all
   handlers or weaken guarded operations to make compilation pass.

5. **Generate target-specific installer metadata.** Keep the plugin ID stable
   and generate required-plugin versions, resource installation paths, archive
   names, and target bounds from one build target. For 2022x, validate resource
   manager upper-bound semantics with the vendor examples before choosing a
   maximum version, so it cannot accidentally replace the 2024x build on a
   newer host. Expected assets are
   `cameo-mcp-bridge-<version>-2022x.zip` and
   `cameo-mcp-bridge-<version>-2024x.zip`. Keep them as separate assets in a new
   release; never overwrite the published 3.0.0 2024x asset.

6. **Keep client negotiation explicit.** Use the same bridge release version
   for both variants only if their contract remains identical. Add target and
   runtime information to capability diagnostics. If a 2022x operation cannot
   meet the contract, advertise the limitation and return a structured refusal;
   update client negotiation/tests accordingly. Preserve dry-run defaults,
   explicit write gates, transaction ownership, and model consistency checks.

7. **Validate both platforms before promoting 2022x.** Run plugin unit tests
   against both SDK/Gson combinations, Java 11 bytecode/linkage checks for 2022x,
   ZIP/descriptor checks, and Python compatibility tests. Include port bounds,
   saved-setting/default/VM-override precedence, fingerprint determinism,
   rollback/at-most-once receipts, and export inventory integrity. Keep 2024x
   a required release gate. SDK-bearing CI runners must use licensed local
   installations; do not publish proprietary dependencies.

8. **Schedule explicit live acceptance.** In separately authorized disposable
   projects, verify plugin loading, saved System Options port after restart,
   loopback binding, status/capability negotiation, UI/diagram/table/Relation
   Map reads, native export/read/release and resource resolution, and guarded
   prepare/apply/receipt with rollback. Check 2024x again before release.
   Record exact refresh/hotfix versions tested. Do not claim all 2022x variants
   are supported solely from compilation. Publish 2022x as a prerelease until
   these checks pass, while retaining the functioning 2024x release.

## Acceptance criteria

The existing supported 2024x behavior and downloadable release stay available.
Both variants build from the same source into separate ZIPs with correct SDK,
bytecode and descriptors. No required library leaks into the installer. Matching
Python clients negotiate each target explicitly. A port setting saved in System
Options survives restart, defaults to 18740, accepts only 1-65535, and yields to
`-Dcameo.mcp.port`; `CAMEO_BRIDGE_PORT` must match on the client. Live evidence
must distinguish tested 2022x support from optional unsupported capabilities.

See [the release guide](../development/plugin-releases.md) for the existing
2024x packaging and publishing workflow.
