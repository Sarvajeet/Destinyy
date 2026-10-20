# Destinyy

**Your destination. Right on time.**

Destinyy is a native Android arrival-reminder prototype. Choose a destination, select a travel mode and advance alerts, then let a foreground service monitor your journey.

## Implemented in this milestone
- Kotlin + Jetpack Compose + Material 3 home screen with system light/dark theme
- Destination lookup using Android `Geocoder`
- Runtime location and notification permission requests
- Explicitly started foreground location service using Fused Location Provider
- User-selectable Train / Metro, Cab / Car, Bus, and Walking modes
- Configurable 10, 5, and 2 minute reminder thresholds
- Reminder notifications, ongoing tracking notification, arrival-radius notification, and stop-tracking action
- Location updates requested at a moderate cadence and removed when tracking stops

## Important accuracy limitation
The current ETA is a **rough distance-based estimate**, calculated from straight-line distance, a mode-specific average speed, and a simple route multiplier. It is **not** a road-route, live traffic, or transit timetable ETA. Geocoder place lookup also depends on the device's geocoding provider and may not return useful results offline. Treat this as an implementation prototype, not a dependable safety-critical arrival system.

## Next milestones
1. Integrate a route provider (Google Routes API or another provider) for actual walking/driving route duration and traffic-aware ETA.
2. Decide how Train / Metro and Bus should work: public-transit routing requires transit-capable route data, not a speed heuristic.
3. Add a proper place autocomplete/map picker, disambiguation when multiple results match, and a destination confirmation screen.
4. Improve ETA smoothing, stale-location handling, GPS-off/error states, and adaptive update intervals near arrival.
5. Add unit tests for threshold crossings and instrumentation tests for permission/service lifecycle.
6. Validate foreground-service and notification behavior across Android versions and OEM battery-management settings.

## Build
Open this project in Android Studio with JDK 17 and Android SDK 36. Allow Gradle sync and run the `app` configuration. A Gradle wrapper is not included yet.

No Google Maps or Routes API key is included. When routing is added, keep keys out of source control and restrict them by API and application signature/package.

## Privacy principles
- Request location only when the user starts a journey.
- Track only while a journey is active.
- Stop location updates when the service stops or the arrival radius is reached.
- Keep journey data on-device by default.
