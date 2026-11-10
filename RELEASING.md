# Releasing

Every release is a git tag. GitHub Actions builds the APK, signs it with the project key and publishes it on the Releases page. The app checks that page daily (and from **Settings → Updates**), downloads the APK, checks its signature and installs it over the old version. Data stays.

## One-time setup

1. Create the repo and push (`gh repo create KIRAHERK/Forge --public --source . --push`).
2. Give CI the signing key. From the project root, with `forge-release.jks` and `keystore.properties` present:

   ```bash
   scripts/upload-signing-secrets.sh
   ```

   This sets four repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

3. Keep an offline copy of `forge-release.jks` and `keystore.properties` (USB stick, password manager). If the key is lost, no future build can update the installed app. Users would have to uninstall, which deletes their data.

## Every release

```bash
# optional: write what changed
echo "- Arabic translation" > release-notes.md && git add release-notes.md && git commit -m "Notes for 1.2.0"

scripts/release.sh 1.2.0
```

The version comes from the tag. `versionCode` is derived from it (`1.2.0` → `10200`), so it always increases. Versions must only go up, because Android refuses to install an older version over a newer one.

On the phone: **Settings → Updates → Check**, or wait for the daily notification. The first time, Android asks you to allow Forge to install apps. That happens once.

## Rules that keep data safe

- **Same `applicationId`** (`com.forge.app`) and **same signing key**, always.
- **Database changes need a migration.** Bump `version` in `ForgeDb`, add `autoMigrations = [AutoMigration(from = N, to = N + 1)]` (or a manual `Migration`) and commit the new file in `app/schemas/`. Never ship a schema change without one.
- **New settings fields need default values.** Settings are stored as JSON with defaults, so adding fields is safe; renaming or changing a field's type is not.
- The app makes a backup right before installing an update, and daily to `Downloads/Forge/` (Android 10+).
