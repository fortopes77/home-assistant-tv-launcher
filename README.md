# TV Home

A home screen for Google TV, styled like Projectivy's app rows, that sits on top of the stock Google TV home instead of replacing it.

- **Apps:** every app that shows in Google TV's app list, as wide banner cards. The card you're on grows and gets a white ring, its name shows big at the top, and the background takes on its colours.
- **Inputs & settings:** the TV's HDMI inputs (only on a TV; a Chromecast or streaming box has none) and a TV settings card.
- **Home button:** like Projectivy, an accessibility service opens TV Home when you press Home, or when you press Back out of an app and would land on the Google TV home. Turn it off in TV Home's settings (the gear, top right) and Home goes back to the stock Google TV screen. Nothing is replaced, so uninstalling TV Home leaves the TV exactly as it was.

Still to come: a Continue watching row (Jellyfin, Disney+, Binge) and a Home Assistant panel based on the dashboard's `tv` view.

## Get the APK

GitHub builds it on every push (**Actions → Build APK**, the `tv-home-apk` download at the bottom of a run). Every build of `main` also replaces `tv-home.apk` on the **Latest build** release, so that's one link to keep.

To build it yourself: `./gradlew assembleDebug` with the Android SDK installed, and the APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Install it on the TV with ADB

You need ADB on a computer on the same network as the TV ([Android platform tools](https://developer.android.com/tools/releases/platform-tools)).

1. **Turn on developer options on the TV.** Settings → System → About, then press OK on **Android TV OS build** seven times.
2. **Turn on debugging.** Settings → System → Developer options:
   - **Wireless debugging** (newer Google TVs): turn it on, open it, and choose **Pair device with pairing code**. On the computer run `adb pair <ip>:<pairing port>` and type the code, then `adb connect <ip>:<port>` using the port shown on the Wireless debugging screen (it's a different one).
   - **USB debugging / Network debugging** (older ones): turn it on, then run `adb connect <tv-ip>:5555` and accept the prompt on the TV.
   
   `adb devices` should now list the TV.
3. **Install:** `adb install -r tv-home.apk`. The `-r` installs over an older version, keeping your settings. Every build is signed with the same key from `keystore/`, so updates always install over the top.
4. **Turn on the Home button service.** First see what's already on:

   ```sh
   adb shell settings get secure enabled_accessibility_services
   ```

   If it prints `null` or nothing, run:

   ```sh
   adb shell settings put secure enabled_accessibility_services com.fortopes.tvlauncher/com.fortopes.tvlauncher.home.HomeButtonService
   adb shell settings put secure accessibility_enabled 1
   ```

   If it printed something (another app's service), keep it and add ours after a colon, for example:

   ```sh
   adb shell settings put secure enabled_accessibility_services <what it printed>:com.fortopes.tvlauncher/com.fortopes.tvlauncher.home.HomeButtonService
   adb shell settings put secure accessibility_enabled 1
   ```

   You can also do this on the TV in Settings → System → Accessibility → **TV Home: Home button**. ADB is the sure way: Android 13 and later can block that switch for apps installed from a file manager.
5. **Press Home.** TV Home opens. The gear at the top right has the on/off toggle, shows whether the service is on, and has **Open the Google TV home** to visit the stock home once without turning anything off.

To remove it: `adb uninstall com.fortopes.tvlauncher`.

## Good to know

- The service asks for window changes and key presses only. It can't read what's on the screen.
- While the toggle is on, a **long** press of Home is caught too, so Google TV's long-press panel doesn't open. Use the gear or the TV settings card for settings.
- If Home stops opening TV Home after a TV update, check the service is still on (the gear shows it) and run step 4 again.

## Code map

| Path | What it is |
|---|---|
| `app/src/main/java/.../MainActivity.kt` | The screen: loads apps and inputs, switches between home and settings |
| `.../home/HomeButtonService.kt` | The accessibility service that catches Home and the stock home appearing |
| `.../data/AppRepository.kt` | Lists Google TV apps with their banners and colours, and opens them |
| `.../data/InputRepository.kt` | Lists HDMI inputs and switches to them |
| `.../data/LauncherPrefs.kt` | The on/off toggle and the "open Google TV home once" flag |
| `.../ui/HomeScreen.kt` | The rows, spotlight title, clock and background; later rows slot in here |
| `.../ui/Cards.kt` | The Projectivy-style banner cards |
| `.../ui/SettingsScreen.kt` | TV Home's settings |
