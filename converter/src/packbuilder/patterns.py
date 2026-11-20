"""Turns raw trips into lines, directions, stop patterns and compact trips."""
from __future__ import annotations

import re
from collections import Counter, defaultdict
from dataclasses import dataclass

from .config import AgencyConfig
from .gtfs import Feed, GtfsError

RAIL_TYPES = {0, 1, 2}


@dataclass(frozen=True)
class LineRoute:
    key: str
    short_name: str
    long_name: str
    kind: int  # 0 rail, 1 bus
    sort: int


@dataclass(frozen=True)
class Pattern:
    id: int
    route_key: str
    dir: int
    stop_ids: tuple[str, ...]
    headsign: str


@dataclass(frozen=True)
class PackedTrip:
    pattern_id: int
    service_id: str
    start: int
    deltas: bytes


@dataclass(frozen=True)
class Direction:
    route_key: str
    dir: int
    headsign: str
    stop_ids: list[str]
    busiest_stop: str


@dataclass
class Lines:
    routes: list[LineRoute]
    patterns: list[Pattern]
    trips: list[PackedTrip]
    directions: list[Direction]


def encode_deltas(departures: list[int]) -> bytes:
    """Unsigned LEB128 varints: seconds from each stop's departure to the next."""
    out = bytearray()
    for prev, cur in zip(departures, departures[1:]):
        delta = cur - prev
        if delta < 0:
            raise GtfsError(f"departure times go backwards ({prev} then {cur})")
        while True:
            byte = delta & 0x7F
            delta >>= 7
            if delta:
                out.append(byte | 0x80)
            else:
                out.append(byte)
                break
    return bytes(out)


def _short_name(route, rail_regex: str) -> str:
    if route.short_name:
        return route.short_name
    if rail_regex:
        m = re.match(rail_regex, route.long_name)
        if m:
            return m.group(1)
    return route.long_name or route.id.split(":", 1)[1]


def _sort_key(kind: int, short: str):
    if kind == 0:
        return (0, 0, short)
    m = re.match(r"\d+", short)
    return (1, int(m.group()), short) if m else (2, 0, short)


def _direction_stops(patterns: list[tuple[tuple[str, ...], int]]) -> list[str]:
    """patterns: (stop sequence, trip count). See the plan, Task 3."""
    ordered = sorted(patterns, key=lambda p: (-p[1], -len(p[0]), p[0]))
    result: list[str] = []
    for stops, _ in ordered:
        anchor = -1  # index in result of the nearest earlier stop already listed
        for stop in stops:
            if stop in result:
                anchor = result.index(stop)
            else:
                anchor += 1
                result.insert(anchor, stop)
    return result


def build(feeds: list[Feed], cfg: AgencyConfig) -> Lines:
    strip = re.compile(cfg.headsign_strip_regex) if cfg.headsign_strip_regex else None

    route_key: dict[str, str] = {}
    by_key: dict[str, list] = defaultdict(list)
    for feed in feeds:
        for r in feed.routes:
            key = _short_name(r, cfg.rail_short_name_regex)
            route_key[r.id] = key
            by_key[key].append(r)

    pattern_trips: dict[tuple, list[tuple[str, int, bytes]]] = defaultdict(list)
    for feed in feeds:
        for trip in feed.trips.values():
            times = feed.stop_times.get(trip.id)
            if not times or len(times) < 2:
                continue  # nothing to board
            headsign = trip.headsign
            if not headsign and times[0].stop_headsign:
                headsign = strip.sub("", times[0].stop_headsign, count=1) if strip else times[0].stop_headsign
            if not headsign:
                headsign = feed.stops[times[-1].stop_id].name
            key = (route_key[trip.route_id], trip.direction_id, tuple(t.stop_id for t in times), headsign)
            try:
                deltas = encode_deltas([t.departure for t in times])
            except GtfsError as e:
                raise GtfsError(f"stop_times.txt, trip {trip.id.split(':', 1)[1]}: {e}") from None
            pattern_trips[key].append((trip.service_id, times[0].departure, deltas))

    used_keys = {k[0] for k in pattern_trips}
    routes = []
    kinds = {k: (0 if any(r.type in RAIL_TYPES for r in rs) else 1) for k, rs in by_key.items() if k in used_keys}
    for sort, key in enumerate(sorted(kinds, key=lambda k: _sort_key(kinds[k], k))):
        first = min(by_key[key], key=lambda r: r.id)
        routes.append(LineRoute(key, key, first.desc or first.long_name or key, kinds[key], sort))

    patterns, trips = [], []
    for pid, key in enumerate(sorted(pattern_trips), start=1):
        patterns.append(Pattern(pid, key[0], key[1], key[2], key[3]))
        for service_id, start, deltas in sorted(pattern_trips[key]):
            trips.append(PackedTrip(pid, service_id, start, deltas))

    trip_count = Counter(t.pattern_id for t in trips)
    by_direction: dict[tuple[str, int], list[Pattern]] = defaultdict(list)
    for p in patterns:
        by_direction[(p.route_key, p.dir)].append(p)

    directions = []
    for (key, d), pats in sorted(by_direction.items()):
        heads = Counter()
        departures = Counter()
        for p in pats:
            heads[p.headsign] += trip_count[p.id]
            for stop in p.stop_ids[:-1]:
                departures[stop] += trip_count[p.id]
        headsign = min(heads, key=lambda h: (-heads[h], h))
        stop_ids = _direction_stops([(p.stop_ids, trip_count[p.id]) for p in pats])
        busiest = min(stop_ids, key=lambda s: (-departures[s], stop_ids.index(s)))
        directions.append(Direction(key, d, headsign, stop_ids, busiest))

    return Lines(routes, patterns, trips, directions)
