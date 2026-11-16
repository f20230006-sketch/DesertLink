# Deployment proposal and demo boundary

## Initial users

Safari convoy operators, with a radio node shared by each vehicle and a staffed rescue base. Tourists use a phone interface; the operator maintains the relay equipment. Start with one known route and scheduled vehicle passes rather than claiming arbitrary desert coverage.

## Android prototype

The current hardware target is Android phones only. A Raspberry Pi is not required. The app includes a clearly labeled three-node on-device simulation, while physical BLE tests need at least two phones.


The Android APK integrates Bitchat’s BLE transport with DESERTLINK’s persistent rescue layer. One physical phone is available. The Raspberry Pi is excluded because no SD card is available. A real two-device encounter needs another Android phone or a verified emulator/Netsim setup; neither has yet proved the physical exchange.

A multi-hop rescue demonstration requires additional real devices or explicitly simulated nodes. The Android offline demo and desktop simulator supply clearly labeled simulated nodes. Do not present simulator kilometer ranges as measured BLE performance. Internet is used only for optional APK updates; rescue incidents use BLE.

## Contribution to demonstrate

An incident has a stable ID and increasing revisions. A relay may deliver v1 while the source is isolated with v2. Acknowledgment of v1 confirms only v1. Nodes replace obsolete payloads after learning a newer revision, retaining a revision watermark. No node can know an unseen update exists. This is an application-specific contribution, not a claim to have invented BLE mesh or delay-tolerant networking.

## Desert constraints

- Power: vehicle-powered relay with protected backup battery; stationary equipment may use a separately sized solar and battery system. Measure idle, scan, transmit, and reconnect consumption before estimating runtime. Assess production hardware and runtime only after measuring the Android prototype.
- Heat and dust: shaded, dust-sealed enclosure, suitable connectors, and manufacturer-rated thermal and battery charging protection. Validate under expected environmental conditions; an indoor prototype does not establish desert survivability.
- Distance: BLE is a local encounter transport. Scheduled vehicle movement bridges disconnected areas over time. A future long-range radio adapter requires a separate link-budget study and local radio compliance review.
- Cost: share equipment per vehicle, reuse existing phones, and price the enclosure, radio/controller, power protection, maintenance, and staffing. Obtain local component quotes before stating a per-user deployment cost.
- Operations: staff the rescue endpoint and define escalation procedures. No relay encounter means no delivery guarantee. A confirmed message does not itself mean that rescue has been dispatched.

## Submission checklist

Show the source queue, relay inventory, destination receipt, human confirmation, and returned ACK. Show packet loss and node removal. Show the isolated v2 update. Export observed metrics and state the simulator assumptions. Attribute any reused Bitchat components and follow the hackathon's own rules for preexisting code.
