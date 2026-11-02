# TV Home privacy policy

TV Home is a home screen for Google TV. It has no account, no analytics and no ads, and it sends nothing to its developer.

**What stays on the TV.** These are saved only in TV Home's own storage on the TV, and are removed when you uninstall it or clear its data:
- your app order and hidden apps
- the services you connect, with their sign-ins: Jellyfin, Seerr and Home Assistant addresses, tokens and API keys
- the last show each app played

**What TV Home talks to.** It only contacts:
- the services you connect yourself, on your own network or at the addresses you give it: Jellyfin, Seerr and Home Assistant
- image.tmdb.org, for film and series pictures in the Recommended row
- YouTube, only when you choose to watch a trailer

**Accessibility service.** The "TV Home: Home button" service watches for the Home button and for the Google TV home screen opening, so TV Home can show instead. It does not read what's on the screen (canRetrieveWindowContent is off), it doesn't record your key presses, and it collects no data.

**Notification access.** When you turn it on, TV Home reads "now playing" media information, which is the title and picture of what an app is playing. It uses this to show Continue watching for apps like Disney+ and Binge. It doesn't read the content of your notifications, it doesn't change them, and it doesn't send them anywhere.

**Contact.** Open an issue at https://github.com/fortopes77/home-assistant-tv-launcher/issues.
