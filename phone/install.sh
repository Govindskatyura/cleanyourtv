#!/usr/bin/env bash
# Install Clean Home on a phone plugged in over USB, from the newest APK GitHub Actions built for this
# branch (or from an APK you name), then make it the Home app.
#
# Usage: phone/install.sh                       needs gh signed in, adb, USB debugging on
#        phone/install.sh path/to/app-release.apk
#        phone/install.sh --home                only make it Home (after installing on the phone itself)
#        REPO=owner/cleanyourtv phone/install.sh   when the branch lives in another fork
set -euo pipefail
cd "$(dirname "$0")"
PKG=io.github.govindskatyura.cleanhome

make_home() {
  adb shell cmd package set-home-activity "$PKG/.HomeActivity"
  # A copy opened from the installer's "Open" button runs as an ordinary app, not as Home: restart it as Home.
  adb shell am force-stop "$PKG"
  adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
  echo "Clean Home is the Home app. The stock launcher is still installed if you want it back."
}

if [ "${1:-}" = "--home" ]; then
  make_home
  exit 0
fi

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
  # MIUI refuses USB installs unless "Install via USB" is on, and that toggle won't stay on without a SIM
  # connection for the Mi account check. The phone's own installer still works: hand it the file.
  adb push "$APK" /sdcard/Download/CleanHome.apk
  echo "The phone refused a USB install. On the phone, open File Manager > Downloads > CleanHome.apk and" >&2
  echo "tap Install (allow File Manager to install apps if it asks). Then run: $0 --home" >&2
  exit 1
fi
make_home
