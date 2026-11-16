# DESERTLINK Android changes

This source tree derives from permissionlesstech/bitchat-android at commit 0594b60ff4adc4e8ed5dbc07ec163d0aedfc1607. Original source, license, and contributor notices are retained. The project license is GPL-3.0.

DESERTLINK additions live under `app/src/main/java/com/bitchat/android/desertlink/`:

- `RescueProtocol`: signed, bounded application records and a local revision-aware ledger.
- `RescueStore`: Android Keystore signing identity, persistent local records, explicit rescue trust, and cyclic BLE gossip.
- `DesertLinkScreen`: dedicated SOS and operator interface.
- `RescueDemo`: isolated simulated ledgers, explicit contacts, and revision-specific acknowledgments.

Integration changes: dedicated application ID, launcher label/icon, MainActivity screen selection, ChatViewModel BLE send/receive bridge, and application-scoped shutdown permission names. The underlying Bitchat packet protocol is unchanged. The new envelopes use public BLE messages; signatures do not encrypt their contents.

Source authenticity does not establish rescue authority. Users must verify a rescue fingerprint out of band. Selecting a role does not grant trust. The relay loop currently follows the ViewModel lifecycle, so keep the app visible for first tests.

Run focused JVM tests with `./gradlew :app:testDebugUnitTest --tests com.bitchat.android.desertlink.*`. A physical UI test exercises the eight-step on-device simulation. A real BLE exchange needs at least two phones, and must be reported separately from simulated contacts.
