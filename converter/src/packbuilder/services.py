"""Expands GTFS calendars into the set of services running on each date."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from .gtfs import Feed, GtfsError


@dataclass
class ServiceDays:
    days: dict[int, set[str]]  # YYYYMMDD -> prefixed service ids
    valid_from: int  # latest feed start; also the first day held
    valid_to: int  # EARLIEST feed end: everything up to here is as published
    first_day: int
    last_day: int  # latest feed end; days after valid_to are partly projected


def to_date(d: int) -> date:
    return date(d // 10000, d // 100 % 100, d % 100)


def to_int(d: date) -> int:
    return d.year * 10000 + d.month * 100 + d.day


def _feed_days(feed: Feed) -> dict[date, set[str]]:
    days: dict[date, set[str]] = {}
    try:
        for row in feed.calendar:
            d, end = to_date(row.start), to_date(row.end)
            while d <= end:
                days.setdefault(d, set())
                if row.weekdays[d.weekday()]:
                    days[d].add(row.service_id)
                d += timedelta(days=1)
        # When calendar.txt exists it defines the feed's range. Exceptions outside
        # it are ignored: LA Metro lists stray holiday dates months past the end.
        bounded = bool(days)
        if not bounded and feed.calendar_dates:
            d, end = (f(to_date(ex.date) for ex in feed.calendar_dates) for f in (min, max))
            while d <= end:
                days[d] = set()
                d += timedelta(days=1)
        for ex in feed.calendar_dates:
            d = to_date(ex.date)
            if d not in days:
                continue
            if ex.added:
                days[d].add(ex.service_id)
            else:
                days[d].discard(ex.service_id)
    except ValueError as e:
        raise GtfsError(f"{feed.prefix}: calendar has an impossible date ({e})") from None
    if not days:
        raise GtfsError(f"{feed.prefix}: calendar covers no dates")
    return days


def expand(feeds: list[Feed]) -> ServiceDays:
    per_feed = [_feed_days(f) for f in feeds]
    first = max(min(d) for d in per_feed)
    valid_to = min(max(d) for d in per_feed)
    last = max(max(d) for d in per_feed)
    if valid_to < first:
        raise GtfsError("the feeds' calendars do not overlap")

    merged: dict[int, set[str]] = {}
    for days in per_feed:
        feed_end = max(days)
        d = first
        while d <= last:
            if d <= feed_end:
                services = days.get(d, set())
            else:
                # Past this feed's published range: carry the same weekday forward.
                src = feed_end - timedelta(days=(feed_end.weekday() - d.weekday()) % 7)
                services = days.get(src, set()) if src >= first else set()
            merged.setdefault(to_int(d), set()).update(services)
            d += timedelta(days=1)
    return ServiceDays(merged, to_int(first), to_int(valid_to), to_int(first), to_int(last))
