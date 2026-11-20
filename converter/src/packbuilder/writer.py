"""Writes the data pack (one SQLite file per agency) and the index that lists packs.

The schema below is the contract with the app. Bump FORMAT_VERSION on any
change the app could misread; the app refuses versions it does not know.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

from .config import AgencyConfig
from .frequency import Freq, Span
from .patterns import Lines
from .places import Place
from .services import ServiceDays

FORMAT_VERSION = 1

SCHEMA = """
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE route(id INTEGER PRIMARY KEY, key TEXT NOT NULL UNIQUE, short_name TEXT NOT NULL,
                   long_name TEXT NOT NULL, kind INTEGER NOT NULL, sort INTEGER NOT NULL);
CREATE TABLE direction(route_id INTEGER, dir INTEGER, headsign TEXT NOT NULL, busiest_stop_id INTEGER,
                       PRIMARY KEY(route_id, dir)) WITHOUT ROWID;
CREATE TABLE place(id INTEGER PRIMARY KEY, name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL);
CREATE TABLE stop(id INTEGER PRIMARY KEY, key TEXT NOT NULL UNIQUE, place_id INTEGER NOT NULL,
                  name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL);
CREATE INDEX stop_place ON stop(place_id);
CREATE TABLE direction_stop(route_id INTEGER, dir INTEGER, seq INTEGER, stop_id INTEGER,
                            PRIMARY KEY(route_id, dir, seq)) WITHOUT ROWID;
CREATE TABLE pattern(id INTEGER PRIMARY KEY, route_id INTEGER NOT NULL, dir INTEGER NOT NULL,
                     headsign TEXT NOT NULL, stop_count INTEGER NOT NULL);
CREATE TABLE pattern_stop(pattern_id INTEGER, seq INTEGER, stop_id INTEGER,
                          PRIMARY KEY(pattern_id, seq)) WITHOUT ROWID;
CREATE INDEX pattern_stop_stop ON pattern_stop(stop_id);
CREATE TABLE trip(id INTEGER PRIMARY KEY, pattern_id INTEGER NOT NULL, service_id INTEGER NOT NULL,
                  start INTEGER NOT NULL, deltas BLOB NOT NULL);
CREATE INDEX trip_lookup ON trip(pattern_id, service_id, start);
CREATE TABLE service_day(day INTEGER, service_id INTEGER, PRIMARY KEY(day, service_id)) WITHOUT ROWID;
CREATE TABLE frequency(route_id INTEGER, dir INTEGER, day_type INTEGER, band INTEGER,
                       trips INTEGER NOT NULL, headway_min INTEGER,
                       PRIMARY KEY(route_id, dir, day_type, band)) WITHOUT ROWID;
CREATE TABLE span(route_id INTEGER, dir INTEGER, day_type INTEGER, first INTEGER NOT NULL, last INTEGER NOT NULL,
                  PRIMARY KEY(route_id, dir, day_type)) WITHOUT ROWID;
"""


def iso(t: datetime) -> str:
    return t.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def write_pack(path: Path, cfg: AgencyConfig, tz: str, stops: dict, lines: Lines, places: list[Place],
               sd: ServiceDays, freq: list[Freq], spans: list[Span], built_at: datetime) -> None:
    """`stops` maps prefixed stop id -> gtfs.Stop for every stop that is in a place."""
    path = Path(path)
    tmp = path.with_suffix(".tmp")
    tmp.unlink(missing_ok=True)
    con = sqlite3.connect(tmp)
    try:
        con.execute("PRAGMA journal_mode = DELETE")
        con.executescript(SCHEMA)

        con.executemany("INSERT INTO meta VALUES (?, ?)", sorted({
            "format_version": str(FORMAT_VERSION), "agency_id": cfg.id, "agency_name": cfg.name, "timezone": tz,
            "valid_from": str(sd.valid_from), "valid_to": str(sd.valid_to),
            "first_day": str(sd.first_day), "last_day": str(sd.last_day),
            "built_at": iso(built_at), "realtime_url": cfg.realtime_url,
        }.items()))

        route_id = {r.key: r.sort + 1 for r in lines.routes}
        con.executemany("INSERT INTO route VALUES (?, ?, ?, ?, ?, ?)",
                        [(route_id[r.key], r.key, r.short_name, r.long_name, r.kind, r.sort) for r in lines.routes])

        con.executemany("INSERT INTO place VALUES (?, ?, ?, ?)", [(p.id, p.name, p.lat, p.lon) for p in places])
        place_of = {sid: p.id for p in places for sid in p.stop_ids}
        stop_id = {key: i for i, key in enumerate(sorted(place_of), start=1)}
        con.executemany("INSERT INTO stop VALUES (?, ?, ?, ?, ?, ?)",
                        [(stop_id[k], k, place_of[k], stops[k].name, stops[k].lat, stops[k].lon) for k in sorted(place_of)])

        for d in lines.directions:
            con.execute("INSERT INTO direction VALUES (?, ?, ?, ?)",
                        (route_id[d.route_key], d.dir, d.headsign, stop_id[d.busiest_stop]))
            con.executemany("INSERT INTO direction_stop VALUES (?, ?, ?, ?)",
                            [(route_id[d.route_key], d.dir, seq, stop_id[s]) for seq, s in enumerate(d.stop_ids)])

        for p in lines.patterns:
            con.execute("INSERT INTO pattern VALUES (?, ?, ?, ?, ?)",
                        (p.id, route_id[p.route_key], p.dir, p.headsign, len(p.stop_ids)))
            con.executemany("INSERT INTO pattern_stop VALUES (?, ?, ?)",
                            [(p.id, seq, stop_id[s]) for seq, s in enumerate(p.stop_ids)])

        service_id = {s: i for i, s in enumerate(sorted({t.service_id for t in lines.trips}), start=1)}
        con.executemany("INSERT INTO trip(pattern_id, service_id, start, deltas) VALUES (?, ?, ?, ?)",
                        [(t.pattern_id, service_id[t.service_id], t.start, t.deltas) for t in lines.trips])
        con.executemany("INSERT INTO service_day VALUES (?, ?)", [
            (day, service_id[s]) for day in sorted(sd.days) for s in sorted(sd.days[day]) if s in service_id])

        con.executemany("INSERT INTO frequency VALUES (?, ?, ?, ?, ?, ?)",
                        [(route_id[f.route_key], f.dir, f.day_type, f.band, f.trips, f.headway_min) for f in freq])
        con.executemany("INSERT INTO span VALUES (?, ?, ?, ?, ?)",
                        [(route_id[s.route_key], s.dir, s.day_type, s.first, s.last) for s in spans])
        con.commit()
        con.execute("VACUUM")
    except BaseException:
        con.close()
        tmp.unlink(missing_ok=True)
        raise
    con.close()
    tmp.replace(path)


def write_gz(pack: Path, gz: Path) -> None:
    with open(gz, "wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0, compresslevel=9) as z:
        z.write(Path(pack).read_bytes())


def index_entry(pack: Path, gz: Path, cfg: AgencyConfig, sd: ServiceDays, built_at: datetime) -> dict:
    return {
        "agency_id": cfg.id, "name": cfg.name, "format_version": FORMAT_VERSION, "built_at": iso(built_at),
        "valid_from": sd.valid_from, "valid_to": sd.valid_to, "url": Path(gz).name,
        "size": Path(gz).stat().st_size, "sha256": hashlib.sha256(Path(pack).read_bytes()).hexdigest(),
    }


def write_index(path: Path, entries: list[dict]) -> None:
    Path(path).write_text(json.dumps({"format": 1, "packs": entries}, indent=2, sort_keys=True) + "\n", encoding="utf-8")
