# Build and publish the 2024x plugin ZIP

Run these commands from the repository root. The supported release is
[3.0.0 for 2024x](https://github.com/Open-MBEE/cameo-mcp-bridge/releases/tag/v3.0.0).
Keep `2024x` in both the downloadable filename and release title.

## Prerequisites and source

Use a Java 17 JDK, the checked-in Gradle wrapper, and a licensed Cameo/CATIA
Magic 2024x SDK installation. Gradle needs the installation's `lib/` and
required plugin API JARs. It may download Gradle on first use. Publishing needs
GitHub CLI (`gh`), `gh auth login`, and release write access to the target repo.
The proprietary SDK is compile-only and must not be distributed in the ZIP.

Before a new release, commit and push its source and documentation. Check
`git status --short`, `git remote -v`, and `git rev-parse HEAD`. Align versions in
`plugin/build.gradle`, `plugin/plugin.xml`, `plugin/resource-descriptor.xml`,
`plugin/src/com/claude/cameo/bridge/util/BridgeCapabilities.java`,
`mcp-server/pyproject.toml`, and `mcp-server/cameo_mcp/client.py`.
Update the resource version integers/date and installation JAR paths too.
The Python client requires the matching plugin version; a platform suffix in
the ZIP filename does not replace that compatibility check.

Version 3.0.0 is already published. Use a new version for subsequent code
changes, including the System Options port setting; do not replace the existing
2024x asset or move its tag.

## Build and verify

PowerShell (adjust the JDK path to an installed JDK 17):

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk-17'
& ./plugin/gradlew.bat -p plugin clean test packagePlugin '-PcameoHome=C:/Program Files/MSOSA2024xR2' "-Pjdk17Home=$env:JAVA_HOME" --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Plugin build or tests failed' }
```

On Linux/macOS, from `plugin/`, use `./gradlew clean test packagePlugin`
with the same properties and platform-appropriate paths. The currently declared
test launcher uses `bin/java.exe` when `jdk17Home` is supplied, so on those
platforms set `JAVA_HOME` to JDK 17 and omit `jdk17Home`.

The current 3.1.0 output is `plugin/build/cameo-mcp-bridge-3.1.0-2024x.zip`.
`assemblePlugin` only prepares a directory; `packagePlugin` creates the
Resource/Plugin Manager installer. Build/test/package do not deploy the plugin.
Do not run `deploy`, `install.sh`, restart Cameo, or mutate a model as a side
effect of release validation.

Check `plugin/build/reports/tests/test/index.html` for passing tests. List and
extract the ZIP into a temporary directory; it must contain:

```text
data/resourcemanager/MDR_Plugin_Cameo_MCP_Bridge_11974001_descriptor.xml
plugins/com.claude.cameo.bridge/plugin.xml
plugins/com.claude.cameo.bridge/cameo-mcp-bridge-3.1.0.jar
```

Verify every resource descriptor installation source exists in the ZIP, the
plugin descriptor names the packaged JAR, all version fields match, and the JAR
contains `com/claude/cameo/bridge/CameoMCPBridgePlugin.class`. Do not include Cameo
SDK classes/JARs. Record the checksum:

```powershell
Get-FileHash ./plugin/build/cameo-mcp-bridge-3.1.0-2024x.zip -Algorithm SHA256
```

The original 3.0.0 build passed 32 plugin tests against local 2024x R2 libraries.
Its SHA-256 is `9fa1b6a25b14b48f30f244344b34339c61552fd0624edb797a86f4bcc0aaa09f`.
This is a record of the published artifact; later builds may differ.
Compile/unit verification does not establish live runtime support.

## Publish a new release

The following PowerShell example uses **3.0.1 only as a placeholder**. Set it to
the new version already committed and built. Prepare a Markdown notes file with
changes, supported platform, matching Python version, source SHA, checksum,
test results, and any live-validation limitations.

```powershell
$repo = 'Open-MBEE/cameo-mcp-bridge'
$version = '3.0.1'
$tag = "v$version"
$asset = "plugin/build/cameo-mcp-bridge-$version-2024x.zip"
$commit = git rev-parse HEAD
gh auth status
git tag -a $tag $commit -m "Cameo MCP Bridge $version for 2024x"
git push origin $tag
gh release create $tag $asset --repo $repo --verify-tag --draft --title "Cameo MCP Bridge $version for 2024x" --notes-file ./release-notes.md
gh release view $tag --repo $repo --json name,isDraft,tagName,assets,url
```

Stop if any command fails. Verify the draft's source tag, asset name, size, and
release notes before publishing:

```powershell
gh release edit $tag --repo $repo --draft=false
gh release view $tag --repo $repo --json name,isDraft,tagName,assets,url
gh release download $tag --repo $repo --pattern "cameo-mcp-bridge-$version-2024x.zip" --dir ./tmp/release-download
Get-FileHash "./tmp/release-download/cameo-mcp-bridge-$version-2024x.zip" -Algorithm SHA256
```

Compare the downloaded checksum with the local asset. Attach the installer ZIP
as a release asset; GitHub's automatic source archives are not plugin installers.
If `gh` is unavailable, use GitHub's Releases page to create a draft from the
same source tag, attach the ZIP, verify it, and publish.

Command references: [GitHub release create](https://cli.github.com/manual/gh_release_create),
[release edit](https://cli.github.com/manual/gh_release_edit), and
[managing releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).
