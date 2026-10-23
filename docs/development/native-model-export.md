# Native model export contract (3.0.0)

The bridge captures Cameo's native Eclipse UML2 v5 output without interpreting,
filtering or translating its model. Consumers own processing of the captured data.
This replaces the earlier consumer-specific filtered-export contract.

All routes use POST, JSON and `schemaVersion:1`:

- `/api/v1/model-exports/open`: `projectId` and 1–20 distinct `rootIds` from the
  primary project's packages/classes. Roots are selection metadata. The native
  exporter captures the project, not a filtered selection.
- `/api/v1/model-exports/read`: `exportId`, opaque `resourceId`, integer `offset`
  and `limit` (1–262144). Returns matching identity/offset, `nextOffset` and
  `contentBase64`. File paths and arbitrary resource URIs are never accepted.
- `/api/v1/model-exports/release`: `exportId`. Releases local captured bytes;
  it has no effect on the model. Repeated release is harmless.

Open returns an immutable manifest with source/session identity, requested IDs,
capture scope, dirty state, source fingerprint, timestamp, exporter/plugin versions,
original resource base URI and a bounded file inventory. Each file has an opaque
ID, relative path, byte length and SHA-256. Consumers must verify the inventory
and bytes. The base URI is reference-resolution data, not permission to access
the local filesystem. Never send unfiltered project contents to an external
service merely because the user selected a package.

Capture runs on the EDT, refuses an open editing session and checks primary-model
fingerprints, active project/session identity and dirty state before/after native
export. It never saves or creates a model transaction. Fingerprints cover distinct
primary-project objects; attached-project contents are not fingerprinted. Selected
attached objects are refused. Limits remain 5,000 primary objects, 50,000 containment
encounters, 100,000 feature values and five seconds per fingerprint.

Native output is limited to 16 MiB/124 resources. Four actual installed exporter
catalog resources are captured separately: UML metamodel, UML/Java primitive
libraries and Ecore declarations. `NativeExporterResources` locates these through
the active exporter class; it reads bounded resource bytes and does not interpret
schemas. It never fetches URIs or changes global resource mappings. Missing or
ambiguous installed resources refuse capture.

The store holds at most two immutable exports, 24 MiB each, for five minutes.
Native temporary files are deleted after capture. Handles expire on plugin restart.
Clients should release in a finally block; expiry is the backstop. Capture does
not assert selected-domain completeness. Resource interpretation, reference closure,
schema validation and any transformation/provenance policy belong to consumers.

Python clients use `client.model_export("open"|"read"|"release", arguments)`.
Capability names are `cameo_open_model_export`, `cameo_read_model_export` and
`cameo_release_model_export`; `modelExport.schemaVersion` is 1. Exact plugin/client
compatibility and existing handshake checks remain mandatory.
