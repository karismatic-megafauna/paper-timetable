"""Checks against the real LA Metro feeds. Needs the network; run with:

    uv run packbuilder build --config agencies/lametro.toml --out dist
    uv run pytest -m network
"""
import sqlite3
from pathlib import Path

import pytest

PACK = Path(__file__).resolve().parents[1] / "dist" / "lametro.pack"
pytestmark = pytest.mark.network


@pytest.fixture(scope="module")
def pack():
    if not PACK.exists():
        pytest.skip("build dist/lametro.pack first")
    con = sqlite3.connect(PACK)
    yield con
    con.close()


def test_lametro_shape(pack):
    keys = [k for (k,) in pack.execute("select key from route order by sort")]
    assert keys[:6] == ["A", "B", "C", "D", "E", "K"] and "720" in keys
    assert pack.execute("select count(*) from direction where headsign = ''").fetchone()[0] == 0


def test_pack_is_small_enough_to_bundle():
    assert PACK.stat().st_size < 30_000_000


def test_wilshire_vermont_is_one_place(pack):
    routes = {k for (k,) in pack.execute("""
        select distinct r.key from stop s join pattern_stop ps on ps.stop_id = s.id
        join pattern p on p.id = ps.pattern_id join route r on r.id = p.route_id
        where s.place_id = (select place_id from stop where name = 'Wilshire / Vermont Station' and key like 'rail:%')""")}
    assert {"B", "D", "720", "754"} <= routes


def test_every_stop_belongs_to_a_place_and_every_trip_to_a_service_day(pack):
    assert pack.execute("select count(*) from stop where place_id not in (select id from place)").fetchone()[0] == 0
    assert pack.execute("select count(*) from service_day").fetchone()[0] > 0
