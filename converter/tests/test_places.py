import random

from packbuilder.gtfs import Stop
from packbuilder.places import build_places, haversine_m


def by_name(places, name):
    return next(p for p in places if p.name == name)


def test_station_and_corner_stops_are_one_place(places):
    alpha = by_name(places, "Alpha Station")
    assert set(alpha.stop_ids) == {"rail:ALPHA", "bus:101", "bus:102"}


def test_far_stop_is_alone(places):
    assert by_name(places, "Far / Out").stop_ids == ["bus:301"]


def test_four_places_in_fixture(places):
    assert sorted(p.name for p in places) == ["Alpha Station", "Beta Station", "Far / Out", "Gamma Station"]


def test_entrances_and_stations_excluded(places):
    assert not any("rail:ALPHA_E" in p.stop_ids or "rail:ALPHA_S" in p.stop_ids for p in places)


def test_unused_stop_is_left_out():
    stops = [Stop("a", "A", 34.0, -118.0, 0, None), Stop("b", "B", 34.0, -118.0, 0, None)]
    assert [p.stop_ids for p in build_places(stops, {"a"}, 100, 250)] == [["a"]]


def chain(n=5, gap_m=80):
    # stops in a north-south line, gap_m apart
    step = gap_m / 111_320
    return [Stop(f"s{i}", f"Stop {i}", 34.0 + i * step, -118.0, 0, None) for i in range(n)]


def test_chain_is_capped():
    stops = chain()
    places = build_places(stops, {s.id for s in stops}, 100, 250)
    assert max(len(p.stop_ids) for p in places) <= 4
    for p in places:
        members = [s for s in stops if s.id in p.stop_ids]
        assert max(haversine_m(a.lat, a.lon, b.lat, b.lon) for a in members for b in members) <= 250


def test_platforms_of_one_station_stay_together_even_when_far_apart():
    stops = [Stop("p1", "Big Station", 34.0, -118.0, 0, "st"), Stop("p2", "Big Station", 34.004, -118.0, 0, "st"),
             Stop("st", "Big Station", 34.002, -118.0, 1, None)]
    places = build_places(stops, {"p1", "p2"}, 100, 250)
    assert len(places) == 1 and places[0].name == "Big Station"


def test_bus_only_place_takes_most_common_name():
    stops = [Stop("a", "Main / 1st", 34.0, -118.0, 0, None), Stop("b", "Main / 1st", 34.0002, -118.0, 0, None),
             Stop("c", "1st / Main", 34.0001, -118.0001, 0, None)]
    assert build_places(stops, {"a", "b", "c"}, 100, 250)[0].name == "Main / 1st"


def test_deterministic_ids():
    stops = chain(12, 60)
    used = {s.id for s in stops}
    a = build_places(stops, used, 100, 250)
    shuffled = stops[:]
    random.Random(7).shuffle(shuffled)
    assert build_places(shuffled, used, 100, 250) == a
    assert [p.id for p in a] == list(range(1, len(a) + 1))
