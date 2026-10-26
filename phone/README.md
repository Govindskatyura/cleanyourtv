# Clean Home: an iOS-style home screen for Android phones

A small launcher that lays out an Android phone's home screen the way an iPhone does. It's plain Java
with no libraries, it asks for no internet, storage or location permissions, and the release APK is a
few tens of KB. It was made for a POCO M2 Pro (MIUI 12, Android 10) after debloating it with this repo's
approach, and it should work on any phone running Android 8.0 or newer.

It copies the layout and behaviour of the iOS home screen, not Apple's artwork. App icons are your
own apps' icons cut to the iOS shape, and labels use the phone's system font.

## What it does

- **Pages of apps**: four columns and six rows (fewer on short screens), with your wallpaper behind.
  Swipe sideways to change page. Each page snaps into place and the ends rubber-band.
- **Icons in the iOS shape**: every icon becomes a squircle tile. Square icons (MIUI's) fill it, and
  round icons and loose glyphs sit on a white tile.
- **The dock**: a rounded glass band with four apps and no names, lined up under the grid's columns
  and tinted from your wallpaper. On first run it holds your phone, messages, browser and camera apps.
- **Search pill**: reads "Search" above the dock and turns into page dots while you swipe. Tap it, or
  pull down anywhere on the page, to search your apps. Suggestions show while the field is empty, and
  the last row sends the words to your browser.
- **Memory widget**: an iOS medium widget across the top two rows of page one, showing RAM in use and storage
  in use as rings with the amounts beside them. It reads two system numbers every 3 seconds, and only while
  the home screen is in front. Remove it with its "−" in jiggle mode; the "+" at the top left puts it back.
- **Long-press an app**: the page dims, the tile lifts, and a menu shows the app's own shortcuts, then
  Edit Home Screen, App Info and Remove App.
- **Remove App** (or an app's "−" in jiggle mode) asks as iOS does: **Delete App** (apps you installed),
  **Remove from Home Screen** (any app; it stays in the App Library), or Cancel.
- **App Library**: swipe past the last page. Every app is sorted into groups (Suggestions, Recently Added,
  then Social, Entertainment, Finance and so on), with three apps to open straight away and a small grid
  that opens the whole group. Its search field lists every app A to Z and narrows as you type. Hold an app
  there for **Add to Home Screen** or **Delete App**. Groups come from the app's Play Store category, else
  from words in its name, else Utilities (system apps) or Other.
- **Jiggle mode**: long-press an empty spot, choose Edit Home Screen, or hold an app and move it. Apps
  wiggle and get a "−". You can drag apps around the grid, onto another page by holding at the screen
  edge (a new page appears after the last), and into or out of the dock. Tap Done or an empty spot to finish.
- **Home button**: closes whatever is open, and takes you back to page one when you're already home.
- New apps go at the end of the last page and uninstalled ones disappear. The layout is saved on the phone.

Not included, to keep it light: other widgets, folders on the Home Screen, notification badges, and a
real blur behind the dock (Android 10 has no cheap blur, so the dock is tinted glass instead).

## Build

GitHub Actions builds it on every push that touches `phone/` (`.github/workflows/phone.yml`). The APK is
under the run's **Artifacts** as `clean-home-apk`.

To build locally you need JDK 17 and Android SDK platform 34 with build-tools 34.0.0:

```bash
cd phone && ./gradlew --no-daemon lintRelease assembleRelease
```

The APK is then `phone/app/build/outputs/apk/release/app-release.apk`.

## Install

Plug the phone in over USB with USB debugging on, then:

```bash
phone/install.sh
```

This downloads the newest APK that GitHub built for this branch, installs it, and makes it the Home app.

On a Xiaomi, Redmi or POCO phone, a USB install needs **Developer options > Install via USB**. MIUI won't
keep that on without a SIM connection for its Mi account check, so the script falls back when the install
is refused. It copies the APK to the phone's Downloads folder. You then open it in File Manager, tap
Install, and run `phone/install.sh --home`.

The stock launcher stays installed. To go back, pick it again under Settings > Apps > Manage apps >
Default apps > Home screen (the exact path differs between phones), or uninstall Clean Home.
