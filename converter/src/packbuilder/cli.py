"""packbuilder build --config agencies/lametro.toml --out dist/"""
from __future__ import annotations

import argparse
import sys
from datetime import datetime, timezone
from pathlib import Path

from . import frequency, patterns, places, services, writer
from .config import load_config
from .gtfs import GtfsError, load_feed


def build(config: Path, out: Path, built_at: datetime) -> str:
    cfg = load_config(config)
    feeds = [load_feed(f.source, f.prefix) for f in cfg.feeds]
    zones = {f.timezone for f in feeds}
    if len(zones) != 1:
        raise GtfsError(f"feeds disagree on the time zone: {sorted(zones)}")

    sd = services.expand(feeds)
    lines = patterns.build(feeds, cfg)
    stops = {sid: s for f in feeds for sid, s in f.stops.items()}
    used = {s for p in lines.patterns for s in p.stop_ids}
    place_list = places.build_places(list(stops.values()), used, cfg.place_radius_m, cfg.place_max_span_m,
                                     cfg.place_same_name_radius_m, cfg.place_same_name_span_m)
    freq, spans = frequency.summarize(lines, sd)

    # Everything above can fail on bad data; nothing is written until it has all succeeded.
    out.mkdir(parents=True, exist_ok=True)
    # The published file's name carries the build time. The index is uploaded after it and
    # points at it by name, so a phone can never pair an old checksum with a new pack.
    stamp = built_at.astimezone(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    pack, gz = out / f"{cfg.id}.pack", out / f"{cfg.id}-{stamp}.pack.gz"
    for stale in out.glob(f"{cfg.id}-*.pack.gz"):
        stale.unlink()
    writer.write_pack(pack, cfg, zones.pop(), stops, lines, place_list, sd, freq, spans, built_at)
    writer.write_gz(pack, gz)
    writer.write_index(out / "index.json", [writer.index_entry(pack, gz, cfg, sd, built_at)])

    mb = lambda p: f"{p.stat().st_size / 1e6:.1f} MB"  # noqa: E731
    return (f"{cfg.name}: {len(lines.routes)} routes, {len(used)} stops, {len(place_list)} places, "
            f"{len(lines.patterns)} patterns, {len(lines.trips)} trips\n"
            f"valid {sd.valid_from}–{sd.valid_to} (days held to {sd.last_day})\n"
            f"{pack.name}: {mb(pack)}   {gz.name}: {mb(gz)}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="packbuilder")
    sub = parser.add_subparsers(dest="command", required=True)
    b = sub.add_parser("build", help="build a data pack from an agency config")
    b.add_argument("--config", required=True, type=Path)
    b.add_argument("--out", required=True, type=Path)
    b.add_argument("--built-at", help="ISO-8601 time to stamp the pack with (default: now)")
    args = parser.parse_args(argv)

    built_at = datetime.now(timezone.utc).replace(microsecond=0)
    if args.built_at:
        built_at = datetime.fromisoformat(args.built_at.replace("Z", "+00:00"))
        if built_at.tzinfo is None:
            built_at = built_at.replace(tzinfo=timezone.utc)
    try:
        print(build(args.config, args.out, built_at))
    except GtfsError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
