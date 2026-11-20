import gzip
import hashlib
import json
import sqlite3

import pytest

from packbuilder.cli import main

from conftest import MINI_TOML, REPO

BUILT = "2026-11-02T00:00:00Z"
GZ = "mini-20261102T000000Z.pack.gz"  # the build time is in the name: see test_published_pack_name


@pytest.fixture(scope="module")
def out(tmp_path_factory):
    d = tmp_path_factory.mktemp("out")
    assert main(["build", "--config", str(MINI_TOML), "--out", str(d), "--built-at", BUILT]) == 0
    return d


@pytest.fixture(scope="module")
def pack(out):
    con = sqlite3.connect(out / "mini.pack")
    yield con
    con.close()


def varint_count(blob):
    return sum(1 for b in blob if not b & 0x80)


def test_meta(pack):
    m = dict(pack.execute("select key, value from meta"))
    assert m["format_version"] == "1" and m["timezone"] == "America/Los_Angeles"
    assert m["agency_id"] == "mini" and m["agency_name"] == "Mini Transit"
    assert (m["valid_from"], m["valid_to"], m["first_day"], m["last_day"]) == ("20261102", "20261115", "20261102", "20261213")
    assert m["built_at"] == BUILT and m["realtime_url"] == ""


def test_counts(pack):
    count = lambda t: pack.execute(f"select count(*) from {t}").fetchone()[0]  # noqa: E731
    assert (count("route"), count("stop"), count("place")) == (4, 7, 4)


def test_alpha_place_has_three_stops(pack):
    keys = [k for (k,) in pack.execute(
        "select s.key from stop s join place p on p.id = s.place_id where p.name = 'Alpha Station' order by s.key")]
    assert keys == ["bus:101", "bus:102", "rail:ALPHA"]


def test_route_keys_and_order(pack):
    assert [k for (k,) in pack.execute("select key from route order by sort")] == ["D", "99", "617", "720"]


def test_direction_stops_are_zero_based_and_ordered(pack):
    rows = pack.execute("""select ds.seq, s.key from direction_stop ds join stop s on s.id = ds.stop_id
                           join route r on r.id = ds.route_id where r.key = 'D' and ds.dir = 0 order by ds.seq""").fetchall()
    assert rows == [(0, "rail:ALPHA"), (1, "rail:BETA"), (2, "rail:GAMMA")]


def test_trip_blob_matches_pattern_length(pack):
    rows = pack.execute("select t.deltas, p.stop_count from trip t join pattern p on p.id = t.pattern_id").fetchall()
    assert rows and all(varint_count(blob) == n - 1 for blob, n in rows)


def test_pattern_stop_rows_match_stop_count(pack):
    assert pack.execute("""select count(*) from pattern p where p.stop_count !=
                           (select count(*) from pattern_stop ps where ps.pattern_id = p.id)""").fetchone()[0] == 0


def test_service_days_cover_the_range(pack):
    assert pack.execute("select count(distinct day) from service_day").fetchone()[0] == 42


def test_frequency_and_span_rows(pack):
    assert pack.execute("""select f.trips, f.headway_min from frequency f join route r on r.id = f.route_id
                           where r.key = 'D' and f.dir = 0 and f.day_type = 0 and f.band = 2""").fetchone() == (36, 10)
    assert pack.execute("""select s.first, s.last from span s join route r on r.id = s.route_id
                           where r.key = 'D' and s.dir = 0 and s.day_type = 0""").fetchone() == (18000, 90600)


def test_no_wal_or_tmp_left(out):
    assert sorted(p.name for p in out.iterdir()) == sorted(["index.json", "mini.pack", GZ])


def test_index_matches_pack(out):
    index = json.loads((out / "index.json").read_text())
    assert index["format"] == 1 and len(index["packs"]) == 1
    e = index["packs"][0]
    assert e["agency_id"] == "mini" and e["name"] == "Mini Transit" and e["format_version"] == 1
    assert e["url"] == GZ and e["built_at"] == BUILT
    assert (e["valid_from"], e["valid_to"]) == (20261102, 20261115)
    assert e["size"] == (out / GZ).stat().st_size
    raw = (out / "mini.pack").read_bytes()
    assert e["sha256"] == hashlib.sha256(raw).hexdigest()
    assert gzip.decompress((out / GZ).read_bytes()) == raw


def test_build_is_reproducible(out, tmp_path):
    assert main(["build", "--config", str(MINI_TOML), "--out", str(tmp_path), "--built-at", BUILT]) == 0
    for name in ("mini.pack", GZ, "index.json"):
        assert (tmp_path / name).read_bytes() == (out / name).read_bytes(), name


def _contents(path):
    """Schema and every row. The file's bytes differ between SQLite versions; what it holds must not."""
    con = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        schema = con.execute("SELECT type, name, sql FROM sqlite_master ORDER BY name").fetchall()
        tables = [name for kind, name, _ in schema if kind == "table"]
        return schema, {t: sorted(con.execute(f"SELECT * FROM {t}").fetchall(), key=repr) for t in tables}
    finally:
        con.close()


def test_checked_in_fixture_is_current(out):
    checked_in = REPO / "app" / "src" / "test" / "resources" / "mini.pack"
    assert _contents(checked_in) == _contents(out / "mini.pack"), \
        "rebuild it: see docs/superpowers/plans, Task 6 step 5"


def test_published_pack_name_changes_with_every_build(tmp_path):
    # The index and the pack are uploaded separately. If the pack kept one name, a phone that
    # fetched between the two uploads would pair the old checksum with the new file.
    assert main(["build", "--config", str(MINI_TOML), "--out", str(tmp_path), "--built-at", "2026-11-09T11:00:05Z"]) == 0
    index = json.loads((tmp_path / "index.json").read_text())
    assert index["packs"][0]["url"] == "mini-20261109T110005Z.pack.gz"
    assert (tmp_path / "mini-20261109T110005Z.pack.gz").exists()
