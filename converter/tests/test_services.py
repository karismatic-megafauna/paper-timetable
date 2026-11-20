import pytest

from packbuilder.services import expand


@pytest.fixture(scope="module")
def sd(rail, bus):
    return expand([rail, bus])


def test_weekday_and_weekend(sd):
    assert sd.days[20261104] == {"rail:WK", "bus:WK"}
    assert sd.days[20261107] == {"rail:SA", "bus:SA"}


def test_holiday_exception(sd):
    assert "bus:SU" in sd.days[20261126] and "bus:WK" not in sd.days[20261126]


def test_short_feed_projected_by_weekday(sd):
    assert "rail:WK" in sd.days[20261126]  # copied from Thu 20261112
    assert "rail:SU" in sd.days[20261213]


def test_validity(sd):
    assert (sd.valid_from, sd.valid_to, sd.first_day, sd.last_day) == (20261102, 20261115, 20261102, 20261213)


def test_every_day_in_range_is_present(sd):
    assert len(sd.days) == 42  # 2 Nov – 13 Dec inclusive


def test_stray_exception_dates_do_not_stretch_the_range():
    # LA Metro's bus feed lists holiday exceptions months past the end of its calendar.
    from packbuilder.gtfs import CalendarDate, CalendarRow, Feed
    feed = Feed("x", "X", "America/Los_Angeles")
    feed.calendar = [CalendarRow("x:WK", (True,) * 5 + (False, False), 20261102, 20261115)]
    feed.calendar_dates = [CalendarDate("x:WK", 20270101, True), CalendarDate("x:WK", 20261001, True)]
    out = expand([feed])
    assert (out.first_day, out.last_day) == (20261102, 20261115)
    assert 20270101 not in out.days and 20261001 not in out.days


def test_feed_with_only_calendar_dates_uses_their_range():
    from packbuilder.gtfs import CalendarDate, Feed
    feed = Feed("x", "X", "America/Los_Angeles")
    feed.calendar_dates = [CalendarDate("x:A", 20261102, True), CalendarDate("x:A", 20261104, True)]
    out = expand([feed])
    assert (out.first_day, out.last_day) == (20261102, 20261104) and out.days[20261103] == set()
