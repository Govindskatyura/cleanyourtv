#!/usr/bin/env bash
# Install Clean Home on a phone plugged in over USB, from the newest APK GitHub Actions built for this
# branch (or from an APK you name), then offer it as the Home app.
#
# Usage: phone/install.sh                       needs gh signed in, adb, USB debugging on
#        phone/install.sh path/to/app-release.apk
#        REPO=owner/cleanyourtv phone/install.sh   when the branch lives in another fork
set -euo pipefail
cd "$(dirname "$0")"
APK="${1:-}"
if [ -z "$APK" ]; then
  BRANCH="$(git rev-parse --abbrev-ref HEAD)"
  REPO="${REPO:-$(gh api user -q .login)/cleanyourtv}"
  RUN="$(gh run list -R "$REPO" -b "$BRANCH" -w "Phone launcher" -s success -L 1 --json databaseId -q '.[0].databaseId')"
  [ -n "$RUN" ] || { echo "no successful 'Phone launcher' build of $BRANCH in $REPO yet" >&2; exit 1; }
  DIR="$(mktemp -d)"
  gh run download "$RUN" -R "$REPO" -n clean-home-apk -D "$DIR"
  APK="$DIR/app-release.apk"
fi
if ! adb install -r "$APK"; then
  echo "On Xiaomi, Redmi and POCO phones, turn on Developer options > Install via USB, then run this again." >&2
  exit 1
fi
# Make it Home. MIUI refuses this from the shell unless "USB debugging (Security settings)" is on;
# then the Home app setting opens and you pick Clean Home there. The stock launcher stays installed.
adb shell cmd package set-home-activity io.github.govindskatyura.cleanhome/.HomeActivity \
  || adb shell am start -a android.settings.HOME_SETTINGS
