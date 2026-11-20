"""Groups stops into places: a station's platforms plus the stops around it,
or all corners of one intersection. Riders think in places, not poles."""
from __future__ import annotations

import math
from collections import Counter, defaultdict
from dataclasses import dataclass

from .gtfs import Stop


@dataclass(frozen=True)
class Place:
    id: int
    name: str
    lat: float
    lon: float
    stop_ids: list[str]


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6_371_000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def junction(name: str) -> frozenset[str]:
    """'Vermont / Wilshire' and 'Wilshire / Vermont Station - B & D Lines' name the same junction."""
    base = name.lower().split(" - ")[0].removesuffix(" station")
    return frozenset(part.strip() for part in base.split("/") if part.strip())


def build_places(stops: list[Stop], used: set[str], radius_m: float, max_span_m: float,
                 same_name_radius_m: float = 250, same_name_span_m: float = 400) -> list[Place]:
    """Stops closer than radius_m join one place; stops naming the same junction
    join up to same_name_radius_m apart. A merge never makes a place wider than
    the matching span limit, which stops chains forming along a street."""
    by_id = {s.id: s for s in stops}
    members = sorted((s for s in stops if s.id in used and s.location_type == 0), key=lambda s: s.id)

    group_of: dict[str, int] = {}
    groups: dict[int, list[Stop]] = {}
    by_parent: dict[str, int] = {}
    for s in members:
        gid = by_parent.get(s.parent) if s.parent else None
        if gid is None:
            gid = len(groups)
            groups[gid] = []
            if s.parent:
                by_parent[s.parent] = gid
        groups[gid].append(s)
        group_of[s.id] = gid

    # Candidate pairs via a grid, so this stays far from quadratic.
    reach = max(radius_m, same_name_radius_m)
    cell_lat = reach / 111_320
    grid: dict[tuple[int, int], list[Stop]] = defaultdict(list)

    def cell(s: Stop) -> tuple[int, int]:
        cell_lon = cell_lat / max(math.cos(math.radians(s.lat)), 0.01)
        return (math.floor(s.lat / cell_lat), math.floor(s.lon / cell_lon))

    for s in members:
        grid[cell(s)].append(s)
    pairs = []
    for s in members:
        cy, cx = cell(s)
        for dy in (-1, 0, 1):
            for dx in (-2, -1, 0, 1, 2):  # lon cells vary slightly with latitude
                for o in grid.get((cy + dy, cx + dx), ()):
                    if s.id < o.id:
                        d = haversine_m(s.lat, s.lon, o.lat, o.lon)
                        same = junction(s.name) == junction(o.name)
                        if d < radius_m or (same and d < same_name_radius_m):
                            pairs.append((d, s.id, o.id, same_name_span_m if same and d >= radius_m else max_span_m))
    pairs.sort()

    for _, a, b, limit in pairs:
        ga, gb = group_of[a], group_of[b]
        if ga == gb:
            continue
        span = max(haversine_m(x.lat, x.lon, y.lat, y.lon) for x in groups[ga] for y in groups[gb])
        if span > limit:
            continue
        for s in groups[gb]:
            group_of[s.id] = ga
        groups[ga].extend(groups.pop(gb))

    drafts = []
    for group in groups.values():
        parents = sorted({s.parent for s in group if s.parent and s.parent in by_id})
        if parents:
            name = by_id[parents[0]].name
        else:
            counts = Counter(s.name for s in group)
            name = min(counts, key=lambda n: (-counts[n], n))
        lat = round(sum(s.lat for s in group) / len(group), 6)
        lon = round(sum(s.lon for s in group) / len(group), 6)
        drafts.append((name, lat, lon, sorted(s.id for s in group)))
    drafts.sort()
    return [Place(i, name, lat, lon, ids) for i, (name, lat, lon, ids) in enumerate(drafts, start=1)]
