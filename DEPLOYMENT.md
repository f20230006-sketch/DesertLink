# Deployment proposal and demo boundary

## Initial users

Safari convoy operators, with a radio node shared by each vehicle and a staffed rescue base. Tourists use a phone interface; the operator maintains the relay equipment. Start with one known route and scheduled vehicle passes rather than claiming arbitrary desert coverage.

## Physical prototype

The simulator works now. The next physical milestone is an Android phone exchanging persistent, versioned incident records with a Raspberry Pi 3 over BLE. Bitchat Android is the candidate existing transport implementation; it is cloned separately for evaluation and is not yet integrated. Pi interoperability must be implemented and tested; onboard Bluetooth does not automatically make the Pi a Bitchat node.

One phone plus one Pi proves only a two-device encounter and later reconnection. A multi-hop rescue demonstration needs additional real devices or explicitly simulated nodes. Disable internet transports during the offline proof. Do not present simulator kilometer range as BLE performance.

## Contribution to demonstrate

An incident has a stable ID and increasing revisions. A relay may deliver v1 while the source is isolated with v2. Acknowledgment of v1 confirms only v1. Nodes replace obsolete payloads after learning a newer revision, retaining a revision watermark. No node can know an unseen update exists. This is an application-specific contribution, not a claim to have invented BLE mesh or delay-tolerant networking.

## Desert constraints

- Power: vehicle-powered relay with protected backup battery; stationary equipment may use a separately sized solar and battery system. Measure idle, scan, transmit, and reconnect consumption before estimating runtime. The Pi is a development device; assess a lower-power production controller after protocol validation.
- Heat and dust: shaded, dust-sealed enclosure, suitable connectors, and manufacturer-rated thermal and battery charging protection. Validate under expected environmental conditions; an indoor prototype does not establish desert survivability.
- Distance: BLE is a local encounter transport. Scheduled vehicle movement bridges disconnected areas over time. A future long-range radio adapter requires a separate link-budget study and local radio compliance review.
- Cost: share equipment per vehicle, reuse existing phones, and price the enclosure, radio/controller, power protection, maintenance, and staffing. Obtain local component quotes before stating a per-user deployment cost.
- Operations: staff the rescue endpoint and define escalation procedures. No relay encounter means no delivery guarantee. A confirmed message does not itself mean that rescue has been dispatched.

## Submission checklist

Show the source queue, relay inventory, destination receipt, human confirmation, and returned ACK. Show packet loss and node removal. Show the isolated v2 update. Export observed metrics and state the simulator assumptions. Attribute any reused Bitchat components and follow the hackathon's own rules for preexisting code.
