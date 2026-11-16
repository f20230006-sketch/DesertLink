# DESERTLINK

A working delay-tolerant emergency network simulator for desert convoys.

## Run

Requires Python 3.10+ and Node.js 20.19+.

```powershell
python -m pip install -r requirements.txt
npm install
npm run build
python -m uvicorn backend.app:app --host 127.0.0.1 --port 8000
```

Open http://127.0.0.1:8000. For development, run the Python server and `npm run dev` in separate terminals; Vite proxies the API and WebSocket.

## Demo

1. Send an SOS from Safari 01, then play the simulation.
2. Watch a vehicle collect a packet and carry it across a network partition.
3. At rescue base, click Acknowledge. The acknowledgment must travel back through radio contacts.
4. Set packet loss to 100%, disconnect nodes, or stop the vehicles. Messages remain stored and expire honestly.
5. Update an incident while isolated. Each revision is a separate packet with a monotonically increasing version. Nodes discard older incident payloads when a newer revision arrives. ACKs refer to an exact packet, never an unseen newer revision.
6. Switch to direct-only routing and reset the scenario to compare against store-and-forward. Export the JSON report for delivery, delay, and transfer counts.

## Architecture and limits

React + TypeScript dashboard; FastAPI + WebSockets; seeded contact simulator; SQLite node inventories and transactional world checkpoint. Each node has a separate logical inventory keyed by node ID. Only a successful simulated radio contact copies a packet between inventories. Global telemetry never forwards packets. One packet per direction per link per contact round, SOS first, then expiry. The original sender retains its copy; expiry removes local payloads. Incident version watermarks prevent obsolete payloads from returning.

This is a **software simulation**, not an APK or a physical BLE/LoRa implementation. The synthetic map uses kilometer units and illustrative coordinates. Radio range, packet loss, storm effects, capacity, and power drain are assumptions, not measured desert RF performance. It uses controlled replication, not a claim of a new routing standard or RFC 9171 conformance. No future contact means no guaranteed delivery. A rescue operator must still arrange actual assistance.

## Validation

```powershell
python -m unittest discover -s tests -v
npm run build
```

## Upstream research

Bitchat Android is being evaluated as the physical BLE transport foundation: https://github.com/permissionlesstech/bitchat-android

Research checkout: `0594b60ff4adc4e8ed5dbc07ec163d0aedfc1607`. Its `LICENSE.md` is GPL-3.0. The downloaded checkout is excluded from this repository. No Bitchat transport code is incorporated into this simulator. Any derivative APK must preserve applicable upstream notices and satisfy its source distribution terms. Credit BLE mesh, discovery, encryption, and existing store-and-forward work to upstream; the proposed DESERTLINK extension concerns version-aware emergency incidents and revision-specific operator confirmation.
