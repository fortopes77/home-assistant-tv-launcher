# TV Home

A home screen for Google TV, styled like Projectivy's app rows, that sits on top of the stock Google TV home instead of replacing it.

- **Apps:** every app that shows in Google TV's app list, as wide banner cards. The card you're on grows and gets a white ring, its name shows big at the top, and the background takes on its colours.
- **Inputs & settings:** the TV's HDMI inputs (only on a TV; a Chromecast or streaming box has none) and a TV settings card.
- **Home button:** like Projectivy, an accessibility service opens TV Home when you press Home, or when you press Back out of an app and would land on the Google TV home. Turn it off in TV Home's settings (the gear, top right) and Home goes back to the stock Google TV screen. Nothing is replaced, so uninstalling TV Home leaves the TV exactly as it was.

- **Continue watching:** what you're part-way through, newest first, above the apps. Jellyfin comes straight from your server, and OK opens Jellyfin and starts it where you left off. For Disney+, Binge and other apps, TV Home remembers the last show each one played (from Android's "now playing" info) and shows its name and picture. OK opens that app. These apps don't share their own continue-watching lists with other apps, so TV Home can't jump straight to the episode.

- **Home:** a row for Home Assistant. It has your front door camera (OK for full screen, left and right for the others), what's playing, the lights in the TV's room, your scenes and scripts (such as Movie mode and Good night), and **Full dashboard**, which opens the dashboard's TV screen (`?tv`) inside TV Home for cameras, calendar, solar and rooms. The weather sits next to the clock.

## Connect Home Assistant

Press OK on **Connect** in the Home row (or in the gear's Home Assistant section), and fill in the boxes on the TV. The Google Home app's TV remote keyboard can type or paste into them from your phone, which is easiest for the long token. Or scan the QR code and fill them in on your phone's browser (the phone has to be on the same network as the TV). You need:

- your Home Assistant address, such as `cunn-ha.local:8123` (`.local` names work)
- a long-lived access token (in Home Assistant: your profile → Security → Create token). A separate, non-administrator user is a good idea, as with the dashboard.
- optionally, the room the TV is in, so its lights show in the row

The TV checks they work before saving. **Full dashboard** expects the dashboard at `/local/dashboard/index.html`, where its README installs it.

## Set up Continue watching

Open TV Home's settings (the gear, top right). Nothing needs typing:

- **Jellyfin:** press OK on Jellyfin. TV Home finds your server on the network and shows a code. On your phone, open the Jellyfin app → Settings → **Quick Connect** and enter it. (If the TV says Quick Connect is off, turn it on in Jellyfin's Dashboard → General.) If the server isn't found, type its address in the box, like `192.168.1.20`.
- **Disney+, Binge and other apps:** TV Home needs notification access to see what's playing (it doesn't read or change your notifications). Android blocks that for sideloaded apps, so turn it on with ADB (see below):

  ```sh
  adb shell cmd notification allow_listener com.fortopes.tvlauncher/com.fortopes.tvlauncher.home.NowPlayingListener
  ```

  After that, anything you play in Disney+ or Binge shows up in Continue watching.

## Get the APK

GitHub builds it on every push (**Actions → Build APK**, the `tv-home-apk` download at the bottom of a run). Every build of `main` also replaces `tv-home.apk` on the **Latest build** release, so that's one link to keep.

To build it yourself: `./gradlew assembleDebug` with the Android SDK installed, and the APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Install it on the TV (no computer needed)

1. Install **Downloader** (by AFTVnews) from the Play Store on the TV.
2. In Downloader, type the APK's address: `https://github.com/fortopes77/home-assistant-tv-launcher/releases/download/latest/tv-home.apk`. The first time, the TV asks you to let Downloader install apps. Allow it, then install.
3. Turn on the Home button service: Settings → System → Accessibility → **TV Home: Home button** → On. It's the same place you'd switch on Projectivy's.
4. **Press Home.** TV Home opens. The gear at the top right has the on/off toggle, shows whether the service is on, and has **Open the Google TV home** to visit the stock home once without turning anything off.

To update, do step 2 again. Every build is signed with the same key from `keystore/`, so it installs over the old one and keeps your settings.

## ADB: unblock the Home button and Continue watching

Android blocks two things for apps that didn't come from the Play Store: the accessibility switch (the Home button) and notification access (remembering what Disney+ and Binge play). If the switch turns itself off or the gear says it never started, a few ADB commands get around that. You need ADB on a computer on the same network as the TV ([Android platform tools](https://developer.android.com/tools/releases/platform-tools)).

1. **Turn on developer options on the TV.** Settings → System → About, then press OK on **Android TV OS build** seven times.
2. **Turn on debugging.** Settings → System → Developer options:
   - **Wireless debugging** (newer Google TVs): turn it on, open it, and choose **Pair device with pairing code**. On the computer run `adb pair <ip>:<pairing port>` and type the code, then `adb connect <ip>:<port>` using the port shown on the Wireless debugging screen (it's a different one).
   - **USB debugging / Network debugging:** turn it on, then run `adb connect <tv-ip>:5555` and accept the prompt on the TV. This can work over ethernet, without wireless debugging.

   `adb devices` should now list the TV. You can also install from here with `adb install -r tv-home.apk`.
3. **Lift the block, then use the switch on the TV.** This is the simplest fix, and it leaves other apps' accessibility services (such as Projectivy's) alone:

   ```sh
   adb shell appops set com.fortopes.tvlauncher ACCESS_RESTRICTED_SETTINGS allow
   ```

   Then turn on Settings → System → Accessibility → **TV Home: Home button** on the TV.

   Or turn the service on straight from ADB. First see what's already on:

   ```sh
   adb shell settings get secure enabled_accessibility_services
   ```

   If it prints `null` or nothing, run:

   ```sh
   adb shell settings put secure enabled_accessibility_services com.fortopes.tvlauncher/com.fortopes.tvlauncher.home.HomeButtonService
   adb shell settings put secure accessibility_enabled 1
   ```

   If it printed something (another app's service, such as Projectivy's), keep it and add ours after a colon:

   ```sh
   adb shell settings put secure enabled_accessibility_services <what it printed>:com.fortopes.tvlauncher/com.fortopes.tvlauncher.home.HomeButtonService
   adb shell settings put secure accessibility_enabled 1
   ```

4. **Turn on "remember what's playing"** for Continue watching (Disney+, Binge):

   ```sh
   adb shell cmd notification allow_listener com.fortopes.tvlauncher/com.fortopes.tvlauncher.home.NowPlayingListener
   ```

To remove TV Home: Settings → Apps → TV Home → Uninstall, or `adb uninstall com.fortopes.tvlauncher`.

## Good to know

- The service asks for window changes and key presses only. It can't read what's on the screen.
- While the toggle is on, a **long** press of Home is caught too, so Google TV's long-press panel doesn't open. Use the gear or the TV settings card for settings.
- If Home stops opening TV Home after a TV update, check the service is still on (the gear shows it) and switch it on again.
- If Projectivy's Home button override is also on, turn one of them off so they don't fight over the Home button.

## Code map

| Path | What it is |
|---|---|
| `app/src/main/java/.../MainActivity.kt` | The screen: loads apps and inputs, switches between home and settings |
| `.../home/HomeButtonService.kt` | The accessibility service that catches Home and the stock home appearing |
| `.../data/AppRepository.kt` | Lists Google TV apps with their banners and colours, and opens them |
| `.../data/InputRepository.kt` | Lists HDMI inputs and switches to them |
| `.../data/JellyfinClient.kt` | Finds the Jellyfin server, Quick Connect sign-in, the resume list, and starting playback on the TV |
| `.../data/WatchNextRepository.kt` | Reads the TV's watch next list, for apps that share to it |
| `.../home/NowPlayingListener.kt` | Watches "now playing" and remembers the last show each app played |
| `.../data/NowPlayingStore.kt` | Stores those shows and turns them into Continue watching cards |
| `.../data/ContinueWatchingRepository.kt` | Merges the two into one row and opens items |
| `.../ui/JellyfinSetupScreen.kt` | The no-typing Jellyfin sign-in |
| `.../data/HomeAssistant.kt` | Home Assistant sign-in, REST client, and what the Home row shows |
| `.../home/PhoneSetupServer.kt` | The small web page on the TV that the QR code opens, for typing on your phone |
| `.../ui/HaSetupScreen.kt`, `CameraScreen.kt`, `DashboardScreen.kt` | Connecting, a full-screen camera, and the dashboard inside TV Home |
| `.../data/LauncherPrefs.kt` | The on/off toggle and the "open Google TV home once" flag |
| `.../ui/HomeScreen.kt` | The rows, spotlight title, clock and background; later rows slot in here |
| `.../ui/Cards.kt` | The Projectivy-style banner cards |
| `.../ui/SettingsScreen.kt` | TV Home's settings |
