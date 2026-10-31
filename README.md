# Destinyy

**Your destination. Right on time.**

Destinyy is a native Android arrival-reminder prototype. Plan a journey as an ordered sequence of destinations, choose a transport mode for every leg, and receive reminders as you approach each stop.

## Implemented in this milestone (0.3.0)
- Plan up to 8 ordered destinations, including interchanges and pit stops
- Configure Train / Metro, Cab / Car, Bus, or Walking independently for every leg
- Configure 10, 5, and 2 minute advance reminders independently for every stop
- Resolve every destination before tracking starts, so a journey never starts with a partially resolved plan
- Automatically advance to the next stop when the current stop is reached
- Send a distinct arrival notification at each intermediate stop, including the next mode and destination
- Complete and stop tracking only after the final destination is reached
- Use high-accuracy fused GPS/GNSS location updates while a journey is active
- Calculate ETA from the current GPS-to-destination distance and smoothed, measured ground speed
- Filter inaccurate location fixes, speed-accuracy outliers, and impossible speeds; pause ETA when the device is stationary or speed is not yet reliable
- Require precise location permission for GPS-based ETA
- Public lock-screen visibility for journey and arrival notifications, subject to the user's device lock-screen privacy settings
- Android 16 progress-centric notification using AndroidX NotificationCompat.ProgressStyle, destination milestones, an ongoing navigation category, and a request for promoted Live Update visibility
- Legacy Android devices receive a standard ongoing foreground-service notification with a progress indicator and stop action
- GitHub Actions builds the debug APK, runs unit tests, and uploads the APK artifact

## Android Live Updates and punch-hole displays
On Android 16 (API 36), Destinyy requests promotion as a Live Update. Eligible promoted notifications can appear prominently on the lock screen and notification shade, and as a compact status-bar chip. Android and device manufacturers control promotion and placement; users may disable promoted notifications or lock-screen details. An app cannot draw directly inside the physical camera cutout/punch-hole area. On earlier Android versions, Destinyy uses the standard ongoing foreground-service notification.

## Accuracy limitation
The current ETA is a **GPS movement-based estimate**: Destinyy uses high-accuracy fused location (GNSS/GPS where available), the current GPS-to-destination distance, and a smoothed speed measured from the device's movement. A mode-specific detour factor approximates non-straight paths, so this is **not yet a map-matched road-route, live-traffic, or transit timetable ETA**. The countdown pauses while GPS indicates the device is stopped or the signal is unreliable. Android's fused location provider combines available location sources; it does not guarantee that every fix comes from the GNSS chip alone. Geocoder place lookup also depends on the device's geocoding provider and may not return useful results offline. Treat this as an implementation prototype, not a dependable safety-critical arrival system.

## Build
Open this project in Android Studio with JDK 17 and Android SDK 36. Allow Gradle sync and run the app configuration. A Gradle wrapper is not included yet. GitHub Actions is configured to run assembleDebug and testDebugUnitTest on pushes and pull requests.

No Google Maps or Routes API key is included. When routing is added, keep keys out of source control and restrict them by API and application signature/package.

## Privacy principles
- Request precise location only when the user starts a journey.
- Track only while a journey is active.
- Stop location updates when the service stops or the final arrival radius is reached.
- Keep journey data on-device by default.
