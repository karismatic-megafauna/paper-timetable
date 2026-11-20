import shutil
import zipfile

import pytest

from packbuilder.config import load_config
from packbuilder.gtfs import GtfsError, load_feed, parse_time

from conftest import MINI, MINI_TOML


def test_parse_time_unpadded_and_past_midnight():
    assert parse_time("9:57:00") == 35820
    assert parse_time("26:13:00") == 94380
    with pytest.raises(GtfsError):
        parse_time("9:57")


def test_ids_are_prefixed(rail):
    assert "rail:ALPHA" in rail.stops
    assert rail.stops["rail:ALPHA"].parent == "rail:ALPHA_S"


def test_stop_without_parent_has_none(bus):
    assert bus.stops["bus:101"].parent is None
    assert bus.stops["bus:101"].location_type == 0


def test_coordinates_with_leading_spaces_parse(bus):
    assert bus.stops["bus:101"].lat == pytest.approx(34.0003)


def test_stop_times_sorted_per_trip(bus):
    t = next(t for t in bus.trips.values() if t.route_id == "bus:720-9")
    assert [s.stop_id for s in bus.stop_times[t.id]] == ["bus:101", "bus:201", "bus:301"]


def test_agency_timezone(rail):
    assert rail.timezone == "America/Los_Angeles"


def test_blank_departure_fails_with_file_and_row(tmp_path):
    shutil.copytree(MINI / "bus", tmp_path / "bus")
    st = tmp_path / "bus" / "stop_times.txt"
    lines = st.read_text().splitlines()
    cols = lines[5].split(",")
    cols[1] = cols[2] = ""
    lines[5] = ",".join(cols)
    st.write_text("\n".join(lines) + "\n")
    with pytest.raises(GtfsError, match=r"stop_times\.txt.*row 6"):
        load_feed(tmp_path / "bus", "bus")


def test_missing_required_file_fails(tmp_path):
    shutil.copytree(MINI / "bus", tmp_path / "bus")
    (tmp_path / "bus" / "trips.txt").unlink()
    with pytest.raises(GtfsError, match=r"trips\.txt"):
        load_feed(tmp_path / "bus", "bus")


def test_zip_and_directory_load_the_same(tmp_path, rail):
    z = tmp_path / "rail.zip"
    with zipfile.ZipFile(z, "w") as zf:
        for f in (MINI / "rail").iterdir():
            zf.write(f, f.name)
    zipped = load_feed(z, "rail")
    assert len(zipped.trips) == len(rail.trips)
    assert zipped.stop_times.keys() == rail.stop_times.keys()


def test_config_resolves_relative_sources():
    cfg = load_config(MINI_TOML)
    assert cfg.id == "mini" and cfg.name == "Mini Transit"
    assert [f.prefix for f in cfg.feeds] == ["rail", "bus"]
    assert cfg.feeds[0].source == str(MINI / "rail")
    assert cfg.place_radius_m == 100 and cfg.place_max_span_m == 250 and cfg.realtime_url == ""
