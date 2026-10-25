# Browser service contract (protocol 1, plugin 3.3.1)

The plugin provides a dedicated, origin-bound browser API alongside the native
HTTP API. Both run inside Cameo. The browser service owns session authorization
and native approval dialogs; model access, capture and transactions reuse the
existing SDK handlers. Client application setup, conversion, workspace staging
and deployment are outside this repository's scope.

## Configuration and lifecycle

The native API defaults to `127.0.0.1:18740`; the browser service defaults to
`127.0.0.1:18741`. Their ports must differ. Direct browser requests to the native
API are rejected before its handlers execute.

The browser service starts with the plugin. In **Options > Environment >
Cameo MCP Bridge**, its settings are in the **Browser service** group:

| Setting | Meaning |
| --- | --- |
| Enable browser service | Start the browser listener with Cameo; enabled by default |
| Browser service port | Loopback listening port; default 18741 |
| Browser session duration (minutes) | Absolute lifetime; default 60, range 15–480; restart required |
| Allowed browser origins | Comma-separated exact browser origins permitted to request native approval |

Inspect the configured origin list in Environment Options. A configured origin
is eligible to request pairing, not automatically authorized to read or write.
For example, an HTTPS client at `https://client.example:8443` must be listed
exactly, without a path or trailing slash. Wildcards, user information, query
strings and fragments are refused. HTTP is accepted only for trusted localhost
origins (`localhost`, `127.0.0.1`, `[::1]`); other origins require HTTPS.

Restart Cameo after changing settings. The native `Listening port` setting and
`cameo.mcp.port` system-property override retain their existing behavior. A browser
listener bind/configuration failure is reported separately and leaves the native
API running. Check for another process using the chosen port before startup.
Closing the plugin stops the browser listener, revokes sessions and releases
owned export handles; it does not save or otherwise modify the model.

## HTTP boundary

All browser operations use JSON POST requests. Requests require an exact allowed
`Origin`, literal `Host: 127.0.0.1:<browser-port>` and a loopback peer. Use
`Content-Type: application/json`. Authenticated requests carry
`Authorization: Bearer <session-token>`; cookies do not authorize requests.
Bodies are limited to 64 KiB and responses to 4 MiB. Query strings, unknown routes,
unknown operation arguments and arbitrary upstream URLs are refused.

OPTIONS preflight permits only POST with `Content-Type` and `Authorization` for
an allowed origin. It does not pair a client, read a model or authorize a write.
CORS responses use the exact origin, never a wildcard or ambient credentials.
Clients should omit credentials, avoid redirects and keep tokens out of URLs,
persistent storage, logs and model/provider results. Browser local-network
permission and native pairing are separate checks.

Errors return `ok:false`, `code` and `reason` when a response is readable by the
caller. A successful ordinary request has `protocol:1`, `ok:true` and `value`.
Pairing responses place pairing/session fields directly in the envelope.

## Pairing and revocation

| Route | Request body | Behavior |
| --- | --- | --- |
| `/v1/pairNative` | `{}` | Queue native confirmation for the requesting origin; return `pairingId` and `state:pending` |
| `/v1/pairNative` | `{"pairingId":"..."}` | Poll this origin's pending request; a single successful claim returns the session token |
| `/v1/cancelPair` | `{"pairingId":"..."}` | Cancel that origin's pending pairing, including an unclaimed grant |
| `/v1/revoke` | `{}` | Revoke the bearer session and release its owned exports |

Pairing and cancellation of pairing require the configured origin but no bearer
token. A pairing expires after two minutes. Once approved in Cameo, the successful
claim returns `state:approved`, `token`, `expiresIn:900`,
`reviewedCreationAvailable:true` and `nativeApproval:true`, with protocol 1.
The claim is single use. Decline returns `state:declined`; an expired request
cannot grant access. At most four pairings and four sessions are retained.

The token is bound to the requesting origin and expires after the configured absolute lifetime (default 60 minutes). Native
dialogs identify that origin. Cancellation and expiry dismiss a pending dialog;
its UI-thread cancellation check does not wait on HTTP request monitors.

## Typed requests

POST `/v1/request` with a bearer session and exactly this envelope:

```json
{"protocol":1,"operation":"project","arguments":{}}
```

| Operation | Arguments |
| --- | --- |
| `status` | None |
| `project` | None; refreshes the session's project identity |
| `selection` | None; returns active-diagram state, selected elements, browser selection and selected presentations |
| `read` | `elementId` |
| `children` | Optional `rootId`, `offset` (0-1000000), `limit` (1-200; default 100) |
| `export` | Current `projectId`, 1-20 distinct `rootIds` |
| `exportResource` | Owned `exportId`, opaque `resourceId`, integer `offset` and `limit` (1-262144) |
| `releaseExport` | Owned `exportId` |
| `prepareCreation` | Current `projectId`, `targetPackageId`, ordered `operations` |
| `readCreation` | `planId`, `planHash` |
| `creationReceipt` | `planId`, `planHash` |
| `applyCreation` | `planId`, `planHash`; queues native review rather than accepting a caller write gate |

Browser arguments do not carry the native API's `schemaVersion` or `allowWrite`;
the plugin's internal adapter adds those fields only on the corresponding native
route. The [native export contract](native-model-export.md) and
[guarded creation contract](change-plans.md) define inventories, supported model
operations and authoritative SDK preconditions.

Read `project` before selection, capture or planning. If the active project
changes, the service invalidates pending reviews and exports and requires a fresh
project read and scope selection. Export resources belong to the session that
opened the handle. Release remains available across a project switch. Revocation
and expiry release owned handles; native export expiry is the cleanup backstop.
Capture includes the primary project before any consumer-side interpretation or
filtering. Clients must obtain appropriate capture consent and verify bytes.

Zero selected presentations does not imply a closed diagram. `selection` includes
the explicit active-diagram field from the SDK UI-state handler. Element reads,
capture, planning and release do not save the project.

## Reviewed creation

Creation is available by default but each apply requires an independent native
Cameo confirmation of the requesting origin and exact stored plan. The browser
cannot send `allowWrite`, `approved`, arbitrary commands, model saves, generic
edits/deletes, macros or raw native API routes.

`applyCreation` returns `approvalRequired:true`, `nativeApproval:true`,
`approvalId` and `state`. Poll or cancel it with the same bearer session:

| Route | Request body | Behavior |
| --- | --- | --- |
| `/v1/review` | `{"approvalId":"..."}` | Read this session's review state and completed result |
| `/v1/cancelReview` | `{"approvalId":"..."}` | Decline a pending review; cannot promise rollback after dispatch |

States are `pending`, `applying`, `complete`, `declined` or `outcomeUnknown`.
A completed result is the native handler's creation receipt, not proof that
later edits or Undo have left the model unchanged. At most four pending reviews
and 128 retained review records are accepted. Pending review expires after two
minutes and is bound to the origin, session, project, plan ID and hash.

Before dispatch, the service rechecks the stored plan and current project.
The SDK handler then independently checks fingerprint, ownership, expiry and
transaction preconditions. Model changes use its existing owned transaction and
rollback behavior; no automatic save occurs. Pending cancellation, disconnect
or project changes refuse dispatch. Once applying, treat cancellation or a lost
response as uncertain and recover through `creationReceipt`; never automatically
retry. Repeated requests for a retained reviewed plan do not create a new attempt.

## Compatibility and validation

Status reports `transport:paired-browser-v1`, `nativeApproval:true`, the supported
browser operation list, capability flags and native plugin metadata. The native
metadata's larger endpoint list does not authorize those routes through the
browser API. Negotiate the browser protocol and plugin version explicitly;
protocol 1 is the contract shipped by plugin 3.3.1. Native Python clients retain
their exact plugin/client version checks.

The same contract is built for 2024x/Java 17 and 2022x/Java 11. See
[build and release instructions](plugin-releases.md) and
[bridge validation evidence](browser-service-validation.md). SDK linkage and
synthetic approval tests do not replace live native-dialog and model acceptance.
Automated validation must not install/restart Cameo or mutate a running model.

## Browser session duration (3.3.1)

In Environment Options Ã¢â€ â€™ Cameo MCP Bridge Ã¢â€ â€™ Browser service, set **Browser session
duration (minutes)**. Default: **60**; allowed range: **15Ã¢â‚¬â€œ480**. Restart Cameo
after changing it. This is an absolute lifetime from approval, not an inactivity
timeout; activity does not renew it. Missing settings use 60 minutes. The native
connection dialog shows the configured duration and pairing returns it in seconds.
Disconnect/revocation, origin binding and the separate two-minute model-change
approval remain enforced. Update exact-version clients to 3.3.1 before connecting.
