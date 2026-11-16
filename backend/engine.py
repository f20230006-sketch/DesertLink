"""Deterministic contact-based DTN simulator. No forwarding via global telemetry."""
from __future__ import annotations

import json
import math
import random
import sqlite3
import uuid
from pathlib import Path


class Simulation:
    def __init__(self, path: str = "data/desertlink.sqlite", seed: int = 7):
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.execute("CREATE TABLE IF NOT EXISTS checkpoint (id INTEGER PRIMARY KEY, payload TEXT)")
        self.db.execute("CREATE TABLE IF NOT EXISTS node_messages (node TEXT, id TEXT, payload TEXT, PRIMARY KEY(node,id))")
        self.db.execute("CREATE TABLE IF NOT EXISTS node_heads (node TEXT, incident TEXT, version INTEGER, PRIMARY KEY(node,incident))")
        saved = self.db.execute("SELECT payload FROM checkpoint WHERE id=1").fetchone()
        self.rng = random.Random(seed)
        if saved:
            self.world = json.loads(saved[0])
            self.world["running"] = False
            if "rng" in self.world:
                def tuples(value):
                    return tuple(tuples(x) for x in value) if isinstance(value, list) else value
                self.rng.setstate(tuples(self.world["rng"]))
        else:
            self.reset()

    def reset(self):
        self.rng.seed(7)
        self.world = {
            "time": 0, "running": False,
            "config": {"loss": 15, "speed": 40, "storm": False, "routing": "dtn"},
            "nodes": [
                {"id": "safari", "name": "Safari 01", "role": "stranded", "x": 1.8, "y": 6.4, "online": True, "battery": 86},
                {"id": "relay1", "name": "Relay 01", "role": "mobile", "x": 2.8, "y": 6.0, "online": True, "battery": 94, "direction": 1},
                {"id": "relay2", "name": "Relay 02", "role": "mobile", "x": 10.2, "y": 3.6, "online": True, "battery": 78, "direction": -1},
                {"id": "ridge", "name": "Ridge repeater", "role": "repeater", "x": 8, "y": 7.5, "online": True, "battery": 98},
                {"id": "base", "name": "Rescue base", "role": "base", "x": 14, "y": 2.6, "online": True, "battery": 100},
            ],
            "messages": [], "events": [], "attempts": 0, "transfers": 0, "failed": 0,
        }
        self.db.execute("DELETE FROM node_messages")
        self.db.execute("DELETE FROM node_heads")
        self.event("system", "Scenario ready. Safari 01 has no direct contact with rescue base.")
        self.save()

    def save(self):
        self.world["rng"] = self.rng.getstate()
        self.db.execute("INSERT OR REPLACE INTO checkpoint VALUES (1, ?)", (json.dumps(self.world),))
        self.db.commit()

    def event(self, kind, text, message=None):
        self.world["events"].insert(0, {"id": uuid.uuid4().hex[:10], "time": self.world["time"], "kind": kind, "text": text, "message": message})
        self.world["events"] = self.world["events"][:200]

    def inventory(self, node):
        return [json.loads(row[0]) for row in self.db.execute("SELECT payload FROM node_messages WHERE node=?", (node,))]

    def put(self, node, packet):
        if packet["kind"] == "message":
            incident = packet.get("incident", packet["id"])
            version = packet.get("version", 1)
            head = self.db.execute("SELECT version FROM node_heads WHERE node=? AND incident=?", (node, incident)).fetchone()
            if head and head[0] > version:
                return False
            self.db.execute("INSERT OR REPLACE INTO node_heads VALUES (?, ?, ?)", (node, incident, version))
            for old in self.inventory(node):
                if old["kind"] == "message" and old.get("incident", old["id"]) == incident and old.get("version", 1) < version:
                    self.db.execute("DELETE FROM node_messages WHERE node=? AND id=?", (node, old["id"]))
        self.db.execute("INSERT OR IGNORE INTO node_messages VALUES (?, ?, ?)", (node, packet["id"], json.dumps(packet)))
        return True

    def send(self, text="Vehicle immobilized. Two passengers need assistance.", priority="sos", ttl=3600, incident=None):
        previous = [m for m in self.world["messages"] if m.get("incident", m["id"]) == incident] if incident else []
        if incident and not previous:
            raise ValueError("Unknown incident")
        version = max((m.get("version", 1) for m in previous), default=0) + 1
        packet = {"id": uuid.uuid4().hex[:8], "source": "safari", "destination": "base", "text": text,
                  "priority": priority, "created": self.world["time"], "expires": self.world["time"] + ttl,
                  "kind": "message", "path": ["safari"], "location": {"lat": 24.8412, "lon": 55.7318}}
        packet.update(incident=incident or packet["id"], version=version)
        self.put("safari", packet)
        self.world["messages"].insert(0, {**packet, "status": "queued", "delivered": None, "confirmed": None, "acknowledged": None})
        self.event("queued", f"{priority.upper()} v{version} stored at Safari 01. Waiting for radio contact.", packet["id"])
        self.save()
        return packet["id"]

    def acknowledge(self, message_id):
        message = next((m for m in self.world["messages"] if m["id"] == message_id), None)
        if not message or message["delivered"] is None:
            raise ValueError("Message has not reached rescue base")
        if message["confirmed"] is not None:
            return
        if not next(n for n in self.world["nodes"] if n["id"] == "base")["online"]:
            raise ValueError("Rescue base is offline")
        ack = {"id": "ack-" + message_id, "source": "base", "destination": "safari", "kind": "ack",
               "message_id": message_id, "priority": "sos", "text": "Rescue operator confirmed your SOS. Assistance is being coordinated.",
               "created": self.world["time"], "expires": self.world["time"] + 3600, "path": ["base"]}
        self.put("base", ack)
        message["confirmed"] = self.world["time"]
        message["status"] = "ack_returning"
        self.event("ack", "Rescue operator confirmed receipt. ACK queued at base for return journey.", message_id)
        self.save()

    def links(self):
        active = [n for n in self.world["nodes"] if n["online"] and n["battery"] > 0]
        radius = 1.45 if self.world["config"]["storm"] else 2.6
        return [(a, b) for i, a in enumerate(active) for b in active[i+1:] if math.dist((a["x"], a["y"]), (b["x"], b["y"])) <= radius]

    def step(self, seconds=30):
        self.world["time"] += seconds
        now = self.world["time"]
        start, end = (2.2, 6.2), (13.5, 2.8)
        length = math.dist(start, end)
        for node in self.world["nodes"]:
            if not node["online"]:
                continue
            if node["role"] == "mobile" and node["battery"] > 0:
                fraction = ((node["x"]-start[0])*(end[0]-start[0]) + (node["y"]-start[1])*(end[1]-start[1])) / length**2
                fraction += node["direction"] * self.world["config"]["speed"] * seconds / 3600 / length
                while fraction > 1 or fraction < 0:
                    if fraction > 1:
                        fraction = 2-fraction
                        node["direction"] = -1
                    elif fraction < 0:
                        fraction = -fraction
                        node["direction"] = 1
                node["x"], node["y"] = start[0]+fraction*(end[0]-start[0]), start[1]+fraction*(end[1]-start[1])
            if node["role"] != "base":
                node["battery"] = max(0, node["battery"] - seconds / 18000)
        # Expiry is local to every queue. Delivered application records remain visible.
        for node in self.world["nodes"]:
            for packet in self.inventory(node["id"]):
                if packet["expires"] <= now:
                    self.db.execute("DELETE FROM node_messages WHERE node=? AND id=?", (node["id"], packet["id"]))
        for message in self.world["messages"]:
            if message["expires"] <= now and message["delivered"] is None and message["status"] != "expired":
                message["status"] = "expired"
                self.event("expired", "Message lifetime elapsed before destination delivery.", message["id"])
        # Freeze inventories for this contact round: no same-tick multi-hop shortcut.
        inventories = {n["id"]: self.inventory(n["id"]) for n in self.world["nodes"]}
        for a, b in self.links():
            for sender, receiver in ((a, b), (b, a)):
                known = {p["id"] for p in self.inventory(receiver["id"])}
                candidates = [p for p in inventories[sender["id"]] if p["id"] not in known and p["expires"] > now]
                if self.world["config"]["routing"] == "direct":
                    candidates = [p for p in candidates if p["source"] == sender["id"] and p["destination"] == receiver["id"]]
                candidates.sort(key=lambda p: (p["priority"] != "sos", p["expires"], p["created"]))
                # A small contact budget makes priority observable under competing traffic.
                for packet in candidates[:1]:
                    self.world["attempts"] += 1
                    loss = min(100, self.world["config"]["loss"] + (25 if self.world["config"]["storm"] else 0))
                    if self.rng.random()*100 < loss:
                        self.world["failed"] += 1
                        self.event("loss", f"Packet lost: {sender['name']} → {receiver['name']}. Copy retained for retry.", packet["id"])
                        continue
                    received = {**packet, "path": [*packet["path"], receiver["id"]]}
                    if not self.put(receiver["id"], received):
                        continue
                    self.world["transfers"] += 1
                    self.event("transfer", f"{sender['name']} → {receiver['name']} · {'ACK' if packet['kind']=='ack' else packet['priority'].upper()}", packet["id"])
                    mid = packet.get("message_id", packet["id"])
                    message = next((m for m in self.world["messages"] if m["id"] == mid), None)
                    if message is None:
                        continue
                    if receiver["id"] == packet["destination"]:
                        if packet["kind"] == "ack" and message["acknowledged"] is None:
                            message["acknowledged"] = now
                            message["status"] = "acknowledged"
                            message["ack_path"] = received["path"]
                            self.event("success", "ACK reached Safari 01. Sender now has operator confirmation.", mid)
                        elif packet["kind"] == "message" and message["delivered"] is None:
                            message["delivered"] = now
                            message["status"] = "delivered"
                            message["path"] = received["path"]
                            self.event("delivered", "SOS reached rescue base. Awaiting human acknowledgment." if packet["priority"] == "sos" else "Message reached rescue base.", mid)
                    elif packet["kind"] == "message" and message["status"] == "queued":
                        message["status"] = "relayed"
        self.save()

    def snapshot(self):
        world = {k: v for k, v in self.world.items() if k != "rng"}
        world["nodes"] = [{**n, "queue": len(self.inventory(n["id"])), "packets": [p["id"] for p in self.inventory(n["id"])]} for n in self.world["nodes"]]
        world["links"] = [[a["id"], b["id"]] for a, b in self.links()]
        delivered = [m for m in world["messages"] if m["delivered"] is not None]
        world["metrics"] = {"sent": len(world["messages"]), "delivered": len(delivered),
            "delivery_rate": round(100*len(delivered)/len(world["messages"])) if world["messages"] else 0,
            "mean_delay": round(sum(m["delivered"]-m["created"] for m in delivered)/len(delivered)) if delivered else None,
            "overhead": round(world["transfers"]/len(delivered), 1) if delivered else None}
        return world
