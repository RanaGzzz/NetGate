# Releasing NetGate

Everything in this document is executed by `.github/workflows/release-apk.yml`; the local commands are for preparing secrets and for checking a build before tagging.

## One-time setup

### 1. Create the upload keystore

The keystore is never committed (`my-upload-key.jks` and `debug.keystore` are listed in `.gitignore`). Create it once and keep an offline backup — losing it means no future update can be installed over the current app.

```shell
keytool -genkeypair \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass '<store-password>' \
  -dname "CN=NetGate, O=NetGate, C=US"
```

### 2. Add the secrets

Put these in **Settings → Environments → `release` → Environment secrets** so only the approval-gated release job can read them (repository-level secrets work too, but the environment is what the workflow asks for):

| Name | Value |
| :--- | :--- |
| `SIGNING_KEY` | `base64 -w0 my-upload-key.jks` (single line, no wrapping) |
| `KEY_STORE_PASSWORD` | keystore password |
| `ALIAS` | key alias, e.g. `upload` |
| `KEY_PASSWORD` | key password |

The workflow base64-decodes `SIGNING_KEY` into `my-upload-key.jks` and points `KEYSTORE_PATH` at it; `app/build.gradle.kts` reads `KEYSTORE_PATH`, `STORE_PASSWORD`, `ALIAS` and `KEY_PASSWORD`.

### 3. Record the certificate fingerprint

A **repository variable** (not a secret — fingerprints are public) named `RELEASE_CERT_SHA256`. The release job fails closed if it is missing, and fails if the APK was signed by any other certificate.

```shell
# from the keystore
keytool -list -v -keystore my-upload-key.jks -alias upload -storepass '<store-password>' \
  | grep -i 'SHA256:'

# or from an APK that was already signed with it
apksigner verify --print-certs netgate-release-3.5.apk | grep -i 'SHA-256 digest'
```

Paste the digest in any format: the workflow lowercases it and strips colons and spaces before comparing.

### 4. Require approval

In **Settings → Environments → `release`**, add required reviewers. The job runs with a read-only token and only the release job is granted `contents: write`, so an approval is the last gate before a public asset exists.

## Cutting a release

1. Bump `versionCode` (must be **greater than** the one in the previous `v*` tag) and `versionName` in `app/build.gradle.kts`.
2. Add a `# What's New — NetGate <versionName>` section to `CHANGELOG.md` with at least one non-empty line. The heading must match exactly; the workflow will not fall back to another section.
3. Merge to the default branch. The tagged commit must be an ancestor of `origin/main`, otherwise the release is rejected — this keeps unpublished code out of releases.
4. Tag and push:

   ```shell
   git tag v3.5            # must equal "v" + versionName exactly
   git push origin v3.5
   ```

5. Approve the `release` environment deployment when prompted.

The workflow then runs unit/Robolectric tests, builds the signed release, and verifies every produced APK before anything is published:

- `apksigner verify` passes and the signer's SHA-256 equals `RELEASE_CERT_SHA256`;
- `aapt dump badging` reports the same `versionCode`/`versionName` as the tag;
- each per-ABI APK actually contains `lib/<abi>/*.so` (a split that lost its native libraries installs and then crashes).

### Published assets

| Asset | What it is |
| :--- | :--- |
| `netgate-release-<version>-<abi>.apk` | arm64-v8a, armeabi-v7a, x86, x86_64 builds (smallest download) |
| `netgate-release-<version>-universal.apk` | every ABI in one APK |
| `netgate-release-<version>.apk` | byte-identical copy of the universal APK, kept so old links work |
| `*.apk.sha256` | checksum per APK |
| `netgate-release-<version>-sbom.cdx.json` | CycloneDX SBOM of the packaged APKs |

The job also uploads `netgate-release-<version>-mapping` (the R8 `mapping.txt`) as a workflow artifact, **not** as a release asset.

### Provenance and SBOM

Every release APK is attested by `actions/attest-build-provenance`, which records that this workflow, on this repository, at this commit produced exactly those bytes. Anyone can check a download:

```shell
gh attestation verify netgate-release-3.5-arm64-v8a.apk --repo RanaGzzz/NetGate
```

The SBOM (`netgate-release-<version>-sbom.cdx.json`) is generated from the built APKs with Anchore Syft in CycloneDX format, so it describes what actually ships — including the bundled WireGuard native libraries — rather than the requested dependency graph. It is attached to the release and also kept as the `netgate-release-<version>-sbom` workflow artifact.

Attestations need the `release` job's `id-token: write` and `attestations: write` permissions, and only work while the repository is public.

### Beta releases

Set `versionName` to a pre-release version (for example `3.6-beta.1`), bump `versionCode`, add the matching `# What's New — NetGate 3.6-beta.1` section, and tag `v3.6-beta.1`. The tag must still equal `versionName` exactly. Any version containing a `-` is published as a GitHub **prerelease**, so it never takes over the "latest release" pointer. Because the beta consumes its own `versionCode`, the next stable release must use a higher one.

## Checking a build locally

```shell
export KEYSTORE_PATH="$PWD/my-upload-key.jks" STORE_PASSWORD='...' ALIAS=upload KEY_PASSWORD='...'
./gradlew :app:assembleRelease

# signature + metadata (equivalent to the CI gate)
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-universal-release.apk
aapt dump badging app/build/outputs/apk/release/app-universal-release.apk | head -n1
```

## Retracing a crash from a release build

Release builds are shrunk and obfuscated, so stack traces need the mapping file from the same build:

1. Download the `netgate-release-<version>-mapping` artifact for that version.
2. Run `retrace -verbose mapping.txt crash.txt` (the tool ships in `cmdline-tools/bin`).

## Rotating the signing key

Rotate only if the key is compromised or lost — Android refuses to update an installed app whose signing certificate changed, so every existing user must uninstall and reinstall.

1. Create a new keystore (step 1) and store it offline.
2. Replace `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS` and `KEY_PASSWORD` in the `release` environment.
3. Set `RELEASE_CERT_SHA256` to the new fingerprint (step 3), otherwise the next release fails the verification gate.
4. Publish release notes telling users they must uninstall the previous version first.

## Withdrawing a release

1. Delete the GitHub release (this removes the public assets) and any assets still attached to it.
2. Delete the tag so it cannot be re-used: `git push --delete origin v3.5`.
3. Fix the problem, bump `versionCode`, and re-tag.

An APK that has already been downloaded cannot be recalled — if the build was bad for security reasons, treat it like a key rotation and say so in the next release notes.
