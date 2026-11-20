"""How often each line runs, by part of the day, and its first and last trip."""
from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass

from .patterns import Lines
from .services import ServiceDays, to_date

H = 3600
# early morning, morning rush, midday, evening rush, evening, night (to 4 am)
BANDS = [(4 * H, 6 * H), (6 * H, 9 * H), (9 * H, 15 * H), (15 * H, 19 * H), (19 * H, 22 * H), (22 * H, 28 * H)]


@dataclass(frozen=True)
class Freq:
    route_key: str
    dir: int
    day_type: int  # 0 Mon–Fri, 1 Sat, 2 Sun
    band: int
    trips: int
    headway_min: int | None


@dataclass(frozen=True)
class Span:
    route_key: str
    dir: int
    day_type: int
    first: int
    last: int


def day_type(day: int) -> int:
    wd = to_date(day).weekday()
    return 0 if wd < 5 else wd - 4


def decode_offsets(deltas: bytes) -> list[int]:
    """Seconds from a trip's start to each stop (first is 0)."""
    out, total, value, shift = [0], 0, 0, 0
    for byte in deltas:
        value |= (byte & 0x7F) << shift
        if byte & 0x80:
            shift += 7
        else:
            total += value
            out.append(total)
            value, shift = 0, 0
    return out


def typical_days(sd: ServiceDays, lines: Lines) -> dict[int, int]:
    """For each day type, the earliest published date carrying the usual amount
    of service. Service ids cannot be compared directly: some feeds use a new
    id for every date. The number of trips running is stable on ordinary days
    and lower on holidays."""
    per_service = Counter(t.service_id for t in lines.trips)
    totals: dict[int, dict[int, int]] = defaultdict(dict)
    for day, services in sd.days.items():
        if sd.valid_from <= day <= sd.valid_to:
            totals[day_type(day)][day] = sum(per_service[s] for s in services)
    result = {}
    for kind, by_day in totals.items():
        counts = Counter(by_day.values())
        usual = min(counts, key=lambda n: (-counts[n], -n))
        result[kind] = min(d for d, n in by_day.items() if n == usual)
    return result


def _band_of(sec: int) -> int:
    for i, (_, end) in enumerate(BANDS):
        if sec < end:
            return i
    return len(BANDS) - 1


def _median_minutes(times: list[int]) -> int | None:
    if len(times) < 2:
        return None
    gaps = sorted(b - a for a, b in zip(times, times[1:]))
    mid = len(gaps) // 2
    median = gaps[mid] if len(gaps) % 2 else (gaps[mid - 1] + gaps[mid]) / 2
    return int(median / 60 + 0.5)


def summarize(lines: Lines, sd: ServiceDays) -> tuple[list[Freq], list[Span]]:
    typical = typical_days(sd, lines)
    trips_by_pattern = defaultdict(list)
    for t in lines.trips:
        trips_by_pattern[t.pattern_id].append(t)
    patterns_by_direction = defaultdict(list)
    for p in lines.patterns:
        patterns_by_direction[(p.route_key, p.dir)].append(p)

    freqs, spans = [], []
    for d in lines.directions:
        for kind in (0, 1, 2):
            services = sd.days.get(typical[kind], set()) if kind in typical else set()
            times = []
            for p in patterns_by_direction[(d.route_key, d.dir)]:
                if d.busiest_stop not in p.stop_ids[:-1]:
                    continue
                index = p.stop_ids.index(d.busiest_stop)
                for t in trips_by_pattern[p.id]:
                    if t.service_id in services:
                        times.append(t.start + decode_offsets(t.deltas)[index])
            times.sort()
            banded = [[] for _ in BANDS]
            for sec in times:
                banded[_band_of(sec)].append(sec)
            for band, in_band in enumerate(banded):
                freqs.append(Freq(d.route_key, d.dir, kind, band, len(in_band), _median_minutes(in_band)))
            if times:
                spans.append(Span(d.route_key, d.dir, kind, times[0], times[-1]))
    return freqs, spans
