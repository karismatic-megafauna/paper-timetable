"""Per-agency settings: where the feeds are and how to read their quirks."""
from __future__ import annotations

import tomllib
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class FeedSource:
    prefix: str
    source: str


@dataclass(frozen=True)
class AgencyConfig:
    id: str
    name: str
    feeds: list[FeedSource]
    rail_short_name_regex: str = ""
    headsign_strip_regex: str = ""
    place_radius_m: float = 100
    place_max_span_m: float = 250
    realtime_url: str = ""


def load_config(path: str | Path) -> AgencyConfig:
    path = Path(path)
    data = tomllib.loads(path.read_text(encoding="utf-8"))
    feeds = []
    for f in data.pop("feeds"):
        source = f["source"]
        if not source.startswith(("http://", "https://")) and not Path(source).is_absolute():
            source = str((path.parent / source).resolve())
        feeds.append(FeedSource(f["prefix"], source))
    return AgencyConfig(feeds=feeds, **data)
