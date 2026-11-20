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
