# DESERTLINK Android changes

This source tree derives from permissionlesstech/bitchat-android at commit 0594b60ff4adc4e8ed5dbc07ec163d0aedfc1607. Original source, license, and contributor notices are retained. The project license is GPL-3.0.

DESERTLINK additions live under `app/src/main/java/com/bitchat/android/desertlink/`:

- `RescueProtocol`: signed, bounded application records and a local revision-aware ledger.
- `RescueStore`: Android Keystore signing identity, persistent local records, explicit rescue trust, and cyclic BLE gossip.
- `DesertLinkScreen`: dedicated SOS and operator interface.
- `RescueDemo`: isolated simulated ledgers, explicit contacts, and revision-specific acknowledgments.

v0.2.0 uses a dedicated application, activity, and connected-device foreground service. The process-wide RescueRuntime owns persistence and BLE forwarding independently of a screen ViewModel. The launcher does not initialize upstream Nostr, Tor, or Wi-Fi transport runtimes. A separate AppUpdater is the only active internet integration. The underlying Bitchat packet protocol is unchanged. The new envelopes use public BLE messages; signatures do not encrypt their contents.

Source authenticity does not establish rescue authority. Users must verify a rescue fingerprint out of band. Selecting a role does not grant trust. The new relay loop is process-owned. Vendor battery management and real background BLE behavior still need field validation.

Run focused JVM tests with `./gradlew :app:testDebugUnitTest --tests com.bitchat.android.desertlink.*`. A physical UI test exercises the eight-step on-device simulation. A real BLE exchange needs at least two phones, and must be reported separately from simulated contacts.

The updater checks the public GitHub release feed. It verifies the APK package, strictly newer code, and current signing certificate before offering Android installation. Automatic Wi-Fi download is optional; installation always requires user approval.
