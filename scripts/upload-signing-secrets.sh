#!/usr/bin/env bash
# One-time setup: copies the release signing key into this repo's GitHub Actions secrets
# so CI can sign releases with the SAME key as the app on your phone.
# Values are piped straight to `gh`; nothing is printed.
#
#   scripts/upload-signing-secrets.sh            # uses the current repo
#   scripts/upload-signing-secrets.sh owner/name
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f forge-release.jks ] || { echo "forge-release.jks not found in the project root." >&2; exit 1; }
[ -f keystore.properties ] || { echo "keystore.properties not found in the project root." >&2; exit 1; }

repo="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
prop() { grep "^$1=" keystore.properties | cut -d= -f2- | tr -d '\r\n'; }

base64 -w0 forge-release.jks | gh secret set KEYSTORE_BASE64 -R "$repo"
printf %s "$(prop storePassword)" | gh secret set KEYSTORE_PASSWORD -R "$repo"
printf %s "$(prop keyAlias)" | gh secret set KEY_ALIAS -R "$repo"
printf %s "$(prop keyPassword)" | gh secret set KEY_PASSWORD -R "$repo"

echo "Signing secrets set on $repo."
