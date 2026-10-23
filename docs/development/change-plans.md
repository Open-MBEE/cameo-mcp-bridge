# Guarded structural change plans

The bridge owns authoritative validation, project preconditions, transaction
boundaries, rollback and durable at-most-once receipts. Consumers own planning
and approval interfaces. No consumer approval interface or agent workflow runs
inside this plugin. Project saving is a separate operation.

POST `/api/v1/creation/prepare` accepts `schemaVersion:1`, current session
`projectId`, an ordinary `targetPackageId` and 1–100 ordered operations. Each has
`id`, `kind` (`package`, `block`, `property`), `name` and `owner`. Owners are
`$target`, an earlier `@id`, or an existing owner ID inside the chosen package.
Property fields are `type` (earlier Block or primary-project Type ID), `lower`
(default 1), `upper` (default 1; -1 unlimited) and `aggregation` (`none` or
`composite`). Unknown fields, duplicate IDs/sibling names and forward references
are refused. Names are bounded to 128 characters and specifications to 60 KB.

Preview creates no model objects/session. It returns a canonical plan ID/hash,
ten-minute expiry, target/project context and primary-model fingerprint. Local
projects only; existing owners must be editable. The SysML profile must already
be applied to the target or an ancestor. No implicit profile application occurs.

POST `/api/v1/creation/read` and `/receipt` take `schemaVersion:1`, `planId` and
`planHash`. `/apply` requires those fields plus boolean `allowWrite:true`.
Consumers must obtain approval for the exact stored plan before applying it.
The plugin independently rechecks plan identity/hash, expiry, source identity,
fingerprint, ownership and current session state. It refuses an existing user
transaction rather than joining, closing or resetting it.

Apply opens one owned session, creates and reads back all elements, then commits;
failure cancels that session. Committed receipts contain real IDs/qualified names
and `saved:false`. Supported operations are additive only; no delete, diagram,
arbitrary script, remote project or automatic save is part of this contract.

Plans/receipts live under `.cameo-mcp-bridge/creation-plans` in the plugin user's
home. A flushed CREATE_NEW receipt claims an attempt before mutation. Final
receipts replace it atomically. Status is `prepared`, `committed`, `rolledBack`
or `outcomeUnknown`. Existing attempted receipts prevent replay across restarts.
An uncertain result must be reconciled by reading the receipt and inspecting the
model; never remove the receipt to retry. Receipt status is historical: later Undo,
edits or saves can change the current model independently.

The store refuses new plans at 2,000 entries. Archive reconciled records only as
an explicit maintenance action. Generic source fingerprints are implemented by
`PrimaryModelFingerprint`, shared with native export, independently of consumers.
