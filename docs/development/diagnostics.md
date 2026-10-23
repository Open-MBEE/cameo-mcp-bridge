# Plugin diagnostics and selection testing

Plugin 3.0.0 retains selection diagnostics and adds generic
[native model export](native-model-export.md). Use the matching Python client;
the 3.0 export contract replaces the earlier migration-specific endpoint.

Diagnostics were introduced in **2.3.6**. Current plugin/client patch version:
**3.0.0**, built for Cameo 2024x with Java 17.

The startup `plugin=...` line reads the version through a runtime accessor, matching
the status endpoint. Earlier incremental archives could retain an inlined 2.3.6
startup string even while their status endpoint reported the newer version. The
corrected 2.4.0 archive was rebuilt with `clean test packagePlugin` and its startup
bytecode checked for the accessor call.

## Where to see records

Diagnostic records go to `java.util.logging` and are mirrored to Cameo's GUI log
without bringing that pane to the front. The GUI update is dispatched to the EDT.
Logging failures do not fail the model operation.

Each request gets an `X-Cameo-Request-Id` response header and start/end records:

```text
CameoMCPBridge: request=<id> start GET /api/v1/ui
CameoMCPBridge: request=<id> ui.browser treeType=<class> selectionType=com.nomagic.magicdraw.ui.browser.Node[] resolved=1
CameoMCPBridge: request=<id> ui.selection browserCount=1 presentationCount=0 elementCount=1 warningCount=0
CameoMCPBridge: request=<id> end GET /api/v1/ui status=200 elapsedMs=<time> completed=true
```

Routes are the registered context prefixes, so `/api/v1/elements/<id>` is logged
as `/api/v1/elements`. Query strings, element identifiers/names, model data,
request/response bodies and macro scripts are not included in these records.
Unknown routes use the `/` fallback, return JSON 404 and are logged too.
Selection warnings returned by the API include unavailable method names and
exception classes, not exception messages. Detailed accessor records use Java
logging's FINE level. Existing handler error logs remain separate.

VM options, effective after restarting Cameo:

| Option | Default | Effect |
|---|---|---|
| `-Dcameo.mcp.diagnostics=false` | `true` | Disable request/selection diagnostic records |
| `-Dcameo.mcp.diagnostics.gui=false` | `true` | Retain Java records but suppress GUI mirroring |

Request IDs remain present in responses even if diagnostic records are disabled.

## Installing the test build

The Resource/Plugin Manager archive is
`plugin/build/cameo-mcp-bridge-2.3.6-2024x.zip`. It includes the installation
descriptor and one current plugin JAR. `assemblePlugin` uses Gradle Sync so stale
JARs do not accumulate in the distribution folder. No deploy task is part of tests.

1. Preserve any unsaved work yourself before updating.
2. Install the new archive using Cameo's Resource/Plugin Manager, then restart
   when prompted. Alternatively, close Cameo and use the documented manual
   plugin copy method before starting it again.
3. Restart Cameo and reopen the test project. Look for the startup record with
   `plugin=2.3.6` and `api=v1`.
4. Restart any long-running Python MCP clients after updating them to the matching
   2.3.6 package. The repository's editable source now expects plugin 2.3.6; the
   compatibility handshake will reject the previous running 2.3.5 plugin.

## Read-only checks after installation

1. Select the `ExamplePackage` package in the containment tree.
2. Request `GET http://127.0.0.1:18740/api/v1/ui/selection` or use a compatible MCP
   `cameo.selection` tool. Expect `browserSelectionCount=1` and the package in
   `selectedElements`/`browserSelection`. Compare its name and ID in the JSON
   response; diagnostic logs deliberately show counts/types instead.
3. Compare the response's request ID with the log records. `selectionType=...Node[]`
   confirms the 2024x array path, and `resolved=1` confirms node-to-element readback.
4. Clear the selection and query again. A successful empty selection should return
   zero counts, not warnings from every unused fallback method.
5. Optionally select multiple nodes and then a diagram symbol; compare returned
   counts with Cameo. Any unavailable accessor appears in the warnings response.

These checks do not create a model session, change the model or save the project.
The live post-install selection check is still required: unit tests cover array,
collection, scalar, empty and fallback accessor behavior, while local HTTP tests
cover request IDs, response status/timing, content exclusion and disabled/failing
diagnostic sinks. No plugin installation or restart was performed by validation.
