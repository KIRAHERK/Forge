#!/usr/bin/env bash
# Tags and pushes a release. GitHub Actions builds, signs and publishes it;
# phones running Forge pick it up from Settings → Updates.
#
#   scripts/release.sh 1.2.0
#
# Optional: put notes for this version in release-notes.md before running.
set -euo pipefail
cd "$(dirname "$0")/.."

v="${1:?usage: scripts/release.sh 1.2.0}"
[[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must look like 1.2.0" >&2; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "Commit or stash your changes first." >&2; exit 1; }
git rev-parse "v$v" >/dev/null 2>&1 && { echo "Tag v$v already exists." >&2; exit 1; }

latest="$(git tag --list 'v*' --sort=-v:refname | head -1)"
if [ -n "$latest" ]; then
  newest="$(printf '%s\n%s\n' "${latest#v}" "$v" | sort -V | tail -1)"
  [ "$newest" = "$v" ] && [ "${latest#v}" != "$v" ] || { echo "v$v must be newer than $latest, or phones won't update." >&2; exit 1; }
fi

git tag -a "v$v" -m "Forge $v"
git push origin HEAD
git push origin "v$v"
echo "Pushed v$v. Watch the build: gh run watch"
