import pytest

from packbuilder.frequency import BANDS, summarize, typical_days


@pytest.fixture(scope="module")
def result(lines, sd):
    return summarize(lines, sd)


@pytest.fixture(scope="module")
def f(result):
    return result[0]


@pytest.fixture(scope="module")
def spans(result):
    return result[1]


def row(f, key, d, day_type, band):
    r = next(x for x in f if (x.route_key, x.dir, x.day_type, x.band) == (key, d, day_type, band))
    return (r.trips, r.headway_min)


def span(spans, key, d, day_type):
    s = next(x for x in spans if (x.route_key, x.dir, x.day_type) == (key, d, day_type))
    return (s.first, s.last)


def test_bands_cover_4am_to_4am():
    assert BANDS[0][0] == 14400 and BANDS[-1][1] == 100800
    assert all(a[1] == b[0] for a, b in zip(BANDS, BANDS[1:]))


def test_typical_days(sd, lines):
    assert typical_days(sd, lines) == {0: 20261102, 1: 20261107, 2: 20261108}


def test_d_line_weekday_midday(f):
    assert row(f, "D", 0, 0, 2) == (36, 10)


def test_d_line_night_counts_after_midnight_trips(f):
    assert row(f, "D", 0, 0, 5) == (16, 10)  # 22:00–23:50, 23:55, 24:10, 24:40, 25:10


def test_saturday_is_every_twenty(f):
    assert row(f, "D", 1, 1, 2) == (18, 20)


def test_hourly_line(f):
    assert row(f, "617", 0, 0, 2) == (6, 60)


def test_few_trips(f):
    assert row(f, "99", 0, 0, 0) == (0, None) and row(f, "99", 0, 0, 1) == (1, None)


def test_no_saturday_service(f, spans):
    assert row(f, "720", 0, 1, 2) == (0, None)
    assert not any(s.route_key == "720" and s.day_type == 1 for s in spans)


def test_six_rows_per_direction_and_day_type(f, lines):
    assert len(f) == len(lines.directions) * 3 * 6


def test_span(spans):
    assert span(spans, "D", 0, 0) == (18000, 90600)  # 05:00 – 25:10


def test_typical_weekday_skips_a_holiday(rail, bus, cfg):
    # 20261126 runs Sunday bus service; it must never be picked as the typical weekday,
    # even when it is the first weekday in range.
    from packbuilder.patterns import build
    from packbuilder.services import ServiceDays, expand
    full = expand([bus])
    late = ServiceDays({d: s for d, s in full.days.items() if d >= 20261126}, 20261126, full.valid_to, 20261126, full.last_day)
    assert typical_days(late, build([bus], cfg))[0] == 20261127
