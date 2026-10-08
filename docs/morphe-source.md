# Add Gunshootr to Morphe

In Morphe, open **Sources → + → Remote** and paste:

```text
https://github.com/Kratapand26/Gunshootr
```

You can also open [Add Gunshootr to Morphe](https://morphe.software/add-source?github=Kratapand26/Gunshootr&name=Gunshootr%20Patches)
on your Android device and accept Morphe's source prompt.

Morphe supports custom GitHub sources directly. Verification by the Morphe
developers is not required to add this repository manually. This does not imply
an endorsement or a security review by Morphe. See the official
[patch source guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md).

## Fetch updates

Use the **Update** button on the Gunshootr source card to check immediately.
Background checks follow Morphe's **Settings → Advanced → Updates** preferences.
Importing a local `.mpp` file does not enable updates; use the repository address
for a source that can update remotely.

Updating a patch source downloads the newer patches. Apply them to an original
APK and install the newly patched app to receive the changes in that app.
LinkedIn's declared compatibility remains **4.1.1255.1** and **4.1.1258**, with the
original experimental target retained. A new Gunshootr bundle version does not
change those app version declarations.

## Publish future updates

Pushing source code alone does not publish an updated patch bundle.

1. Commit and push the source changes to `main`.
2. Open GitHub **Actions → Release → Run workflow** on `main`.
3. Enter a new stable tag greater than the current bundle version, such as `v1.0.7`.
4. Wait for the build and tests to pass and the release to be published.

The workflow builds the Android patch bundle and LinkedIn extension, runs tests,
generates `patches-list.json`, and publishes these release files:

- `patches-<version>.mpp`
- `patches-bundle.json`
- `patches-list.json`

The bundle manifest, release tag, download URL, and metadata use the same version.
The workflow also updates the metadata on `main`. Failed builds do not publish a
release. A bundle check rejects accidental runtime libraries in the LinkedIn
extension, and test reports are retained as build artifacts. Keep the original
supported app versions unless an actual compatibility change is reviewed and tested.

See [GitHub releases](https://github.com/Kratapand26/Gunshootr/releases) for available
bundles and the [LinkedIn review](linkedin-port-review.md) for validation limits.
