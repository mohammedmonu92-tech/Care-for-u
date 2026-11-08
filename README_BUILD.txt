CARE FOR U Android app project

This is an Android Studio project wrapper for the CARE FOR U storefront prototype.
It installs as an Android app with its own launcher icon/name after you build it.

Build an APK:
1. Install Android Studio on a computer.
2. Open this extracted folder in Android Studio (Open project).
3. Allow Gradle sync and install any requested Android SDK platform (API 35).
4. Use Build > Build Bundle(s) / APK(s) > Build APK(s).
5. Android Studio will show the generated APK location. Copy it to your phone and install it.
   On the phone, allow installation from that file manager/browser when Android asks.

Important:
- This is an installable app shell with the current demo storefront bundled offline.
- The catalogue/prices are sample data and must be replaced with actual inventory.
- Checkout is demo-only. No real UPI/card/COD order processing, admin panel, stock database,
  customer accounts, shipping calculation, courier tracking, or notifications are connected.
- Before public launch, add a real backend, payment provider, order management, delivery rules,
  privacy policy, support contact, and signed release build.
- Android Studio/Android SDK is required to compile the APK; this ZIP is the source project,
  not a precompiled APK.
