"""Reads a GTFS feed (zip, directory or http(s) address) into plain dataclasses.

Every id is prefixed with the feed's prefix so several feeds can be merged
without collisions.
"""
from __future__ import annotations

import csv
import io
import tempfile
import urllib.request
import zipfile
from dataclasses import dataclass, field
from pathlib import Path


class GtfsError(Exception):
    """Schedule data that cannot be interpreted. The message names file and row."""


@dataclass(frozen=True)
class Route:
    id: str
    short_name: str
    long_name: str
    desc: str
    type: int


@dataclass(frozen=True)
class Stop:
    id: str
    name: str
    lat: float
    lon: float
    location_type: int
    parent: str | None


@dataclass(frozen=True)
class Trip:
    id: str
    route_id: str
    service_id: str
    direction_id: int
    headsign: str


@dataclass(frozen=True)
class StopTime:
    stop_id: str
    departure: int
    stop_headsign: str


@dataclass(frozen=True)
class CalendarRow:
    service_id: str
    weekdays: tuple[bool, ...]  # Monday first
    start: int
    end: int


@dataclass(frozen=True)
class CalendarDate:
    service_id: str
    date: int
    added: bool


@dataclass
class Feed:
    prefix: str
    agency_name: str
    timezone: str
    routes: list[Route] = field(default_factory=list)
    stops: dict[str, Stop] = field(default_factory=dict)
    trips: dict[str, Trip] = field(default_factory=dict)
    stop_times: dict[str, list[StopTime]] = field(default_factory=dict)
    calendar: list[CalendarRow] = field(default_factory=list)
    calendar_dates: list[CalendarDate] = field(default_factory=list)


def parse_time(s: str) -> int:
    """'9:57:00' or '26:13:00' -> seconds after the service day's midnight."""
    parts = s.strip().split(":")
    try:
        if len(parts) != 3:
            raise ValueError
        h, m, sec = (int(p) for p in parts)
        if h < 0 or not 0 <= m < 60 or not 0 <= sec < 60:
            raise ValueError
    except ValueError:
        raise GtfsError(f"not a time: {s!r}") from None
    return h * 3600 + m * 60 + sec


class _Source:
    """Uniform access to the .txt files of a zip or a directory."""

    def __init__(self, source: str | Path):
        self._tmp = None
        src = str(source)
        if src.startswith(("http://", "https://")):
            self._tmp = tempfile.NamedTemporaryFile(suffix=".zip")
            try:
                with urllib.request.urlopen(src, timeout=120) as r:
                    while chunk := r.read(1 << 20):
                        self._tmp.write(chunk)
            except OSError as e:
                raise GtfsError(f"could not download {src}: {e}") from None
            self._tmp.flush()
            src = self._tmp.name
        self.path = Path(src)
        self.label = str(source)
        if self.path.is_dir():
            self._zip = None
        elif self.path.is_file():
            try:
                self._zip = zipfile.ZipFile(self.path)
            except zipfile.BadZipFile:
                raise GtfsError(f"{self.label} is not a zip file") from None
        else:
            raise GtfsError(f"feed not found: {self.label}")

    def has(self, name: str) -> bool:
        if self._zip is None:
            return (self.path / name).is_file()
        return name in self._zip.namelist()

    def rows(self, name: str, required: bool = True):
        """Yields (row_number, dict). Row 1 is the header, so data starts at 2."""
        if not self.has(name):
            if required:
                raise GtfsError(f"{self.label}: {name} is missing")
            return
        raw = open(self.path / name, "rb") if self._zip is None else self._zip.open(name)
        with raw, io.TextIOWrapper(raw, encoding="utf-8-sig", newline="") as text:
            reader = csv.reader(text)
            header = [h.strip() for h in next(reader, [])]
            for n, values in enumerate(reader, start=2):
                if not values or all(not v.strip() for v in values):
                    continue
                yield n, {h: v.strip() for h, v in zip(header, values)}


def _need(row: dict, col: str, name: str, n: int) -> str:
    v = row.get(col, "")
    if v == "":
        raise GtfsError(f"{name} row {n}: {col} is blank or missing")
    return v


def _int(row: dict, col: str, name: str, n: int, default: int | None = None) -> int:
    v = row.get(col, "")
    if v == "" and default is not None:
        return default
    try:
        return int(v)
    except ValueError:
        raise GtfsError(f"{name} row {n}: {col} is not a whole number: {v!r}") from None


def _float(row: dict, col: str, name: str, n: int) -> float:
    try:
        return float(_need(row, col, name, n))
    except ValueError:
        raise GtfsError(f"{name} row {n}: {col} is not a number: {row.get(col)!r}") from None


def load_feed(source: str | Path, prefix: str) -> Feed:
    src = _Source(source)
    p = lambda v: f"{prefix}:{v}"  # noqa: E731

    agency = next(iter(src.rows("agency.txt")), None)
    if agency is None:
        raise GtfsError("agency.txt has no rows")
    feed = Feed(prefix, _need(agency[1], "agency_name", "agency.txt", 2),
                _need(agency[1], "agency_timezone", "agency.txt", 2))

    for n, r in src.rows("routes.txt"):
        feed.routes.append(Route(p(_need(r, "route_id", "routes.txt", n)), r.get("route_short_name", ""),
                                 r.get("route_long_name", ""), r.get("route_desc", ""),
                                 _int(r, "route_type", "routes.txt", n)))

    for n, r in src.rows("stops.txt"):
        location_type = _int(r, "location_type", "stops.txt", n, default=0)
        parent = r.get("parent_station", "")
        sid = p(_need(r, "stop_id", "stops.txt", n))
        feed.stops[sid] = Stop(sid, _need(r, "stop_name", "stops.txt", n), _float(r, "stop_lat", "stops.txt", n),
                               _float(r, "stop_lon", "stops.txt", n), location_type, p(parent) if parent else None)

    route_ids = {r.id for r in feed.routes}
    for n, r in src.rows("trips.txt"):
        tid = p(_need(r, "trip_id", "trips.txt", n))
        route_id = p(_need(r, "route_id", "trips.txt", n))
        if route_id not in route_ids:
            raise GtfsError(f"trips.txt row {n}: unknown route_id {r['route_id']!r}")
        feed.trips[tid] = Trip(tid, route_id, p(_need(r, "service_id", "trips.txt", n)),
                               _int(r, "direction_id", "trips.txt", n, default=0), r.get("trip_headsign", ""))

    sequenced: dict[str, list[tuple[int, StopTime]]] = {}
    for n, r in src.rows("stop_times.txt"):
        tid = p(_need(r, "trip_id", "stop_times.txt", n))
        if tid not in feed.trips:
            raise GtfsError(f"stop_times.txt row {n}: unknown trip_id {r['trip_id']!r}")
        stop_id = p(_need(r, "stop_id", "stop_times.txt", n))
        if stop_id not in feed.stops:
            raise GtfsError(f"stop_times.txt row {n}: unknown stop_id {r['stop_id']!r}")
        raw = r.get("departure_time", "") or r.get("arrival_time", "")
        if raw == "":
            raise GtfsError(f"stop_times.txt row {n}: departure_time is blank")
        try:
            departure = parse_time(raw)
        except GtfsError as e:
            raise GtfsError(f"stop_times.txt row {n}: {e}") from None
        sequenced.setdefault(tid, []).append(
            (_int(r, "stop_sequence", "stop_times.txt", n), StopTime(stop_id, departure, r.get("stop_headsign", ""))))
    for tid, items in sequenced.items():
        items.sort(key=lambda it: it[0])
        feed.stop_times[tid] = [st for _, st in items]

    days = ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    for n, r in src.rows("calendar.txt", required=False):
        feed.calendar.append(CalendarRow(p(_need(r, "service_id", "calendar.txt", n)),
                                         tuple(_int(r, d, "calendar.txt", n) == 1 for d in days),
                                         _int(r, "start_date", "calendar.txt", n), _int(r, "end_date", "calendar.txt", n)))
    for n, r in src.rows("calendar_dates.txt", required=False):
        kind = _int(r, "exception_type", "calendar_dates.txt", n)
        if kind not in (1, 2):
            raise GtfsError(f"calendar_dates.txt row {n}: exception_type must be 1 or 2, got {kind}")
        feed.calendar_dates.append(CalendarDate(p(_need(r, "service_id", "calendar_dates.txt", n)),
                                                _int(r, "date", "calendar_dates.txt", n), kind == 1))
    if not feed.calendar and not feed.calendar_dates:
        raise GtfsError(f"{src.label}: neither calendar.txt nor calendar_dates.txt has any rows")
    return feed
