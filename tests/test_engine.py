import tempfile
import unittest
from pathlib import Path
from backend.engine import Simulation


class EngineTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = str(Path(self.tmp.name) / "state.sqlite")
        self.sim = Simulation(self.path)
        self.sim.world["config"]["loss"] = 0

    def tearDown(self):
        self.sim.db.close()
        self.tmp.cleanup()

    def advance_until(self, check, rounds=100):
        for _ in range(rounds):
            self.sim.step(60)
            if check():
                return
        self.fail("Expected condition was never reached")

    def test_partition_carry_deliver_and_return_ack(self):
        mid = self.sim.send()
        m = self.sim.world["messages"][0]
        self.assertFalse(self.sim.inventory("base"))
        self.sim.step(60)
        self.assertEqual(m["status"], "relayed")
        self.assertIsNone(m["delivered"])
        self.advance_until(lambda: m["delivered"] is not None)
        self.assertGreater(len(m["path"]), 2)
        self.sim.acknowledge(mid)
        self.assertIsNone(m["acknowledged"])
        self.advance_until(lambda: m["acknowledged"] is not None)
        self.assertEqual(m["status"], "acknowledged")

    def test_full_loss_then_recovery_retains_source(self):
        self.sim.world["config"].update(loss=100, speed=0)
        mid = self.sim.send()
        for _ in range(5):
            self.sim.step(30)
        self.assertEqual(self.sim.world["transfers"], 0)
        self.assertEqual(self.sim.inventory("safari")[0]["id"], mid)
        self.sim.world["config"].update(loss=0, speed=40)
        self.advance_until(lambda: self.sim.world["messages"][0]["delivered"] is not None)

    def test_restart_recovers_node_queue_and_clock(self):
        mid = self.sim.send()
        self.sim.step(60)
        other = Simulation(self.path)
        try:
            self.assertEqual(other.world["time"], 60)
            self.assertEqual(other.inventory("safari")[0]["id"], mid)
            self.assertEqual(other.inventory("relay1")[0]["id"], mid)
            self.assertFalse(other.world["running"])
        finally:
            other.db.close()

    def test_direct_baseline_cannot_cross_partition(self):
        self.sim.world["config"]["routing"] = "direct"
        self.sim.send(ttl=1800)
        for _ in range(31):
            self.sim.step(60)
        self.assertEqual(self.sim.world["transfers"], 0)
        self.assertEqual(self.sim.world["messages"][0]["status"], "expired")

    def test_priority_and_deduplication(self):
        self.sim.world["config"]["speed"] = 0
        self.sim.send("Routine", "routine")
        sos = self.sim.send("Emergency", "sos")
        self.sim.step(30)
        self.assertEqual([p["id"] for p in self.sim.inventory("relay1")], [sos])
        self.sim.step(30)
        self.sim.step(30)
        self.assertEqual(len(self.sim.inventory("relay1")), 2)
        self.assertEqual(self.sim.world["transfers"], 2)

    def test_offline_revision_does_not_teleport_and_old_ack_is_version_specific(self):
        old = self.sim.send("Need water")
        self.sim.step(60)
        carried = self.sim.inventory("relay1")[0]
        for n in self.sim.world["nodes"]:
            if n["role"] == "mobile":
                n["online"] = False
        new = self.sim.send("Medical emergency", incident=old)
        self.assertEqual(self.sim.inventory("safari")[0]["id"], new)
        self.assertEqual(self.sim.inventory("relay1")[0]["id"], old)
        self.assertFalse(self.sim.put("safari", carried))
        self.assertEqual(self.sim.inventory("safari")[0]["version"], 2)
        self.sim.put("base", carried)
        old_record = next(m for m in self.sim.world["messages"] if m["id"] == old)
        old_record["delivered"] = self.sim.world["time"]
        self.sim.acknowledge(old)
        self.assertIsNone(self.sim.world["messages"][0]["confirmed"])
        self.assertEqual(self.sim.world["messages"][0]["status"], "queued")
        base_messages = [p for p in self.sim.inventory("base") if p["kind"] == "message"]
        self.assertEqual(base_messages[0]["version"], 1)

    def test_new_revision_replaces_older_payload_at_contact(self):
        self.sim.world["config"]["speed"] = 0
        old = self.sim.send("Initial SOS")
        self.sim.step(30)
        new = self.sim.send("Updated SOS", incident=old)
        self.sim.step(30)
        self.assertEqual([p["id"] for p in self.sim.inventory("relay1")], [new])

    def test_offline_relays_cannot_receive(self):
        for n in self.sim.world["nodes"]:
            if n["role"] == "mobile":
                n["online"] = False
        self.sim.send(ttl=60)
        self.sim.step(60)
        self.assertEqual(self.sim.world["messages"][0]["status"], "expired")
        self.assertEqual(self.sim.world["transfers"], 0)


if __name__ == "__main__":
    unittest.main()
