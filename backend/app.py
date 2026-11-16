import asyncio
import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Literal

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from .engine import Simulation

sim = Simulation(os.environ.get("DESERTLINK_DB", "data/desertlink.sqlite"))
lock = asyncio.Lock()


@asynccontextmanager
async def lifespan(app):
    async def ticker():
        while True:
            await asyncio.sleep(0.5)
            async with lock:
                if sim.world["running"]:
                    sim.step(60)
    task = asyncio.create_task(ticker())
    yield
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass
    sim.save()


app = FastAPI(title="DESERTLINK Simulator", lifespan=lifespan)


class Command(BaseModel):
    action: Literal["play", "pause", "step", "reset", "send", "ack", "configure", "toggle_node"]
    text: str = Field(default="Vehicle immobilized. Two passengers need assistance.", min_length=1, max_length=160)
    priority: Literal["sos", "routine"] = "sos"
    ttl: int = Field(default=3600, ge=60, le=86400)
    message_id: str | None = None
    node_id: str | None = None
    incident: str | None = None
    loss: int | None = Field(default=None, ge=0, le=100)
    speed: int | None = Field(default=None, ge=0, le=80)
    storm: bool | None = None
    routing: Literal["dtn", "direct"] | None = None


@app.get("/api/state")
async def state():
    return sim.snapshot()


@app.post("/api/command")
async def command(cmd: Command):
    async with lock:
        if cmd.action in ("play", "pause"):
            sim.world["running"] = cmd.action == "play"
        elif cmd.action == "step":
            sim.step(60)
        elif cmd.action == "reset":
            sim.reset()
        elif cmd.action == "send":
            if len(sim.world["messages"]) >= 200:
                raise HTTPException(409, "Scenario limit reached. Reset before sending more messages.")
            try:
                sim.send(cmd.text, cmd.priority, cmd.ttl, cmd.incident)
            except ValueError as exc:
                raise HTTPException(404, str(exc)) from exc
        elif cmd.action == "ack":
            try:
                sim.acknowledge(cmd.message_id)
            except ValueError as exc:
                raise HTTPException(409, str(exc)) from exc
        elif cmd.action == "configure":
            for key in ("loss", "speed", "storm", "routing"):
                value = getattr(cmd, key)
                if value is not None:
                    sim.world["config"][key] = value
        elif cmd.action == "toggle_node":
            node = next((n for n in sim.world["nodes"] if n["id"] == cmd.node_id), None)
            if not node:
                raise HTTPException(404, "Unknown node")
            node["online"] = not node["online"]
            sim.event("system", f"{node['name']} {'reconnected' if node['online'] else 'disconnected'}. Stored packets retained.")
        sim.save()
        return sim.snapshot()


@app.websocket("/ws")
async def websocket(ws: WebSocket):
    await ws.accept()
    try:
        while True:
            await ws.send_json(sim.snapshot())
            await asyncio.sleep(0.5)
    except (WebSocketDisconnect, RuntimeError):
        pass


dist = Path(__file__).resolve().parents[1] / "dist"
if dist.exists():
    app.mount("/assets", StaticFiles(directory=dist / "assets"), name="assets")

    @app.get("/")
    async def index():
        return FileResponse(dist / "index.html")
