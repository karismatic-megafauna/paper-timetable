import pytest

from packbuilder.gtfs import GtfsError
from packbuilder.patterns import encode_deltas


def direction(lines, key, d):
    return next(x for x in lines.directions if x.route_key == key and x.dir == d)


def test_deltas_roundtrip_values():
    assert encode_deltas([18000, 18120, 18300]) == bytes([120, 0xB4, 0x01])  # 120, 180
    assert encode_deltas([100]) == b""


def test_negative_delta_is_an_error():
    with pytest.raises(GtfsError):
        encode_deltas([100, 90])


def test_rail_short_name_from_long_name(lines):
    d = next(r for r in lines.routes if r.key == "D")
    assert d.kind == 0 and d.short_name == "D"


def test_bus_long_name_prefers_description(lines):
    r = next(r for r in lines.routes if r.key == "720")
    assert r.kind == 1 and r.long_name == "ALPHA - FAR OUT VIA MAIN ST"


def test_sort_rail_first_then_numeric(lines):
    assert [r.key for r in sorted(lines.routes, key=lambda r: r.sort)] == ["D", "99", "617", "720"]


def test_headsign_prefix_stripped(lines):
    assert direction(lines, "D", 0).headsign == "Gamma Station"
    assert direction(lines, "D", 1).headsign == "Alpha Station"
    assert direction(lines, "720", 0).headsign == "Far / Out"


def test_short_turn_is_its_own_pattern(lines):
    heads = {p.headsign for p in lines.patterns if p.route_key == "D" and p.dir == 0}
    assert heads == {"Gamma Station", "Beta Station"}


def test_direction_stop_list_and_busiest(lines):
    d = direction(lines, "D", 0)
    assert d.stop_ids == ["rail:ALPHA", "rail:BETA", "rail:GAMMA"] and d.busiest_stop == "rail:ALPHA"


def test_loop_pattern_keeps_both_visits(lines):
    p = next(p for p in lines.patterns if p.route_key == "99")
    assert p.stop_ids == ("bus:102", "bus:301", "bus:102")


def test_loop_direction_lists_each_stop_once_and_busiest_is_first(lines):
    d = direction(lines, "99", 0)
    assert d.stop_ids == ["bus:102", "bus:301"] and d.busiest_stop == "bus:102"


def test_trip_count(lines):
    # D weekday dir 0: 114 regular + 3 after midnight + 1 short turn
    ids = {p.id for p in lines.patterns if p.route_key == "D" and p.dir == 0}
    assert sum(1 for t in lines.trips if t.pattern_id in ids and t.service_id == "rail:WK") == 118


def test_trip_start_and_deltas(lines):
    p = next(p for p in lines.patterns if p.route_key == "D" and p.dir == 0 and p.headsign == "Gamma Station")
    first = min((t for t in lines.trips if t.pattern_id == p.id and t.service_id == "rail:WK"), key=lambda t: t.start)
    assert first.start == 18000 and first.deltas == bytes([120, 0xB4, 0x01])
