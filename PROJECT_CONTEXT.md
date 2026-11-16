# DESERTLINK — project handoff

Status snapshot: 16 November 2026, 19:31 Gulf Standard Time (UTC+4).

## Deadline and judging

Deadline shown by the supplied countdown: Tuesday, 17 November 2026 at 10:00 Gulf time. At this snapshot, approximately 14 hours 29 minutes remain. Confirm the actual submission page before the final upload.

Challenge T-001: Staying Connected in the Deep Desert. The core requires at least two people/devices exchanging useful information without mobile coverage, with a demonstration of weak or dropped links. A convincing simulation is accepted. Deployment must address power, heat/dust/distance, and cost. Judging: core 30%, chosen angle 25%, desert constraints 20%, execution 15%, demo/pitch 10%.

## Product and audience

DESERTLINK is a delay-tolerant emergency communication prototype for desert safari convoys and rescue operators. A stranded traveller creates an SOS; a passing vehicle carries it across a network partition; rescue receives it; an operator acknowledges the exact revision; the ACK must travel back.

No reachable peer means no immediate transmission. The SOS waits in local storage. No future encounter means no delivery guarantee.

## Our contribution

Mesh and store-and-forward are established techniques. Bitchat already supplies BLE discovery and mesh transport. Our application-specific contribution is version-aware rescue incidents with signed relay-copy receipts, separate rescue-node and operator receipts, explicit rescue identity trust, retained local history, and ACKs that cannot confirm an unseen newer revision. This is not a claim of worldwide research novelty.

The signature differentiates author identity from relay transport. A chosen role is not proof of rescue authority. SOS payloads are public: signatures do not encrypt the contents.

## Current platforms

- Android is the physical product focus. One physical Android phone is available for development.
- The Raspberry Pi is out of scope because no SD card is available.
- Virtual testing with Android Emulator/Netsim is planned. Multi-emulator Bluetooth behavior has not been verified.
- The browser simulator remains a working visualization and fault-injection demo.

## Versions and source

Repository: https://github.com/f20230006-sketch/DesertLink . It was initially private; the user explicitly approved public APK distribution after the repository became public.

v0.1.0 is a published prerelease with an ARM64 development-signed APK. v0.2.0 is the operational rework: dedicated Bluetooth-only launcher/service, process-owned storage/forwarding, three app sections, structured SOS creation, incident details/events, expired history, resolution revisions, relay-copy receipts, and online app update checking. See GitHub Releases for uploaded binaries and precise validation notes.

Android source is in `android/`; new rescue code is in `android/app/src/main/java/com/bitchat/android/desertlink/`. The app ID is `org.desertlink.rescue`.

Foundation: permissionlesstech/bitchat-android at 0594b60ff4adc4e8ed5dbc07ec163d0aedfc1607, GPL-3.0. Preserve upstream attribution and corresponding source. Upstream source includes extra features that the dedicated launcher does not initialize.

## Architecture

Android: Kotlin/Compose, Android Keystore ECDSA signing, persistent SharedPreferences ledger/checkpoint, bounded active records and event history, signed revision watermarks, a dedicated BLE foreground service, and a process-owned forwarding scheduler. The Bluetooth transport remains Bitchat GATT/mesh; DESERTLINK records are application envelopes.

States distinguish saved locally, a relay reporting a copy, a trusted rescue node reporting receipt, a trusted operator confirming the exact revision, and expiry. Resolution is a signed newer revision; isolated nodes may retain the earlier SOS until contact returns.

Desktop simulator: React/TypeScript/Vite, Python FastAPI/WebSockets, SQLite with separate logical node inventories, seeded contact/packet-loss simulation, moving relays, expiry, and returning ACKs. Its global observer never bypasses simulated contacts. Its kilometer ranges and battery drain are illustrative, not phone BLE measurements.

## Internet policy and OTA updates

Rescue communication uses Bluetooth only. The dedicated Android application does not start Nostr/Tor/Wi-Fi transports. Internet is used by a separate updater to query this public GitHub repository.

Auto checks are limited to once per six hours while the process is alive. Automatic downloads on Wi-Fi are optional. Installation is user-approved by Android, after package/version/signer checks and a release digest check when available. An update must use the same signing key. Production key management and CI signing are not configured. v0.1.0 needs one manual upgrade to obtain the updater.

## Evidence and limits

- v0.2.0 builds, installs, and opens on the physical phone.
- 14 focused protocol/operations/updater JVM tests and 5 backup tests pass.
- 8 Python simulator tests pass.
- A broad Android run reported 25 failures among 860 tests, with 4 skipped, before fixing the backup manifest contract. Remaining failures have not been baseline-classified.
- Android lint remains unclean.
- Automated operational phone UI tests could not reliably launch/attach to the activity. Do not report a passing full device workflow.
- Physical two-phone BLE transfer, background reliability, measured range, battery life, thermal behavior, and an actual downloaded-update installation remain unverified.
- Earlier app-only demo captures and a short walkthrough exist in `docs/assets/`. They show simulated contacts, not physical BLE delivery.

## Next work, in priority order

1. Verify the whole traveller → relay → rescue → ACK journey on a second phone, or configure two emulator instances and establish that their virtual BLE transport works.
2. Manually verify creation, update, expiry/history, resolution, role switching, trust matching, and app restart on the current phone; fix any resulting issues before demo recording.
3. Test the update verifier/download/install flow with a same-key newer APK. Never weaken signer verification to make an update install.
4. Record a fresh v0.2 walkthrough and keep phone screenshots compact in the README (about 240 pixels wide).
5. Prepare the pitch, attribution, and deployment explanation; distinguish measured results from assumptions.
6. Freeze scope, do a clean install/rehearsal, upload the submission, and keep a local demo fallback.

## Suggested remaining schedule

Use the first 2–3 hours for operational verification and emulator/second-device work. Spend the next 2 hours on the highest-impact fixes and regression checks. Allocate 1–2 hours to the demo recording and pitch, then deployment/constraint explanation. Preserve at least the final 2 hours for submission and rehearsal; do not spend the whole remaining window building features.

## Scope decisions

No Raspberry Pi requirement. No satellite/LoRa hardware integration for this submission. No arbitrary long-range BLE claims. No silent APK installation promise. No AI routing claim. No live GPS collection by the current rescue interface; a user can describe a landmark manually. Do not label an old ACK as confirmation of a newer incident revision.

## Hosted web demo

The Vercel build uses `npm run build:demo`. It runs a browser-local TypeScript contact engine with independent node queues and browser storage, rather than requiring the local Python/WebSocket server. Every visitor has a local scenario; this is not live Android telemetry. The original Python/SQLite mode remains available locally. Eight browser-engine tests pass.
