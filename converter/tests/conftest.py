from pathlib import Path

import pytest

from packbuilder.gtfs import load_feed

FIXTURES = Path(__file__).parent / "fixtures"
MINI = FIXTURES / "mini"
MINI_TOML = FIXTURES / "mini.toml"
REPO = Path(__file__).resolve().parents[2]


@pytest.fixture(scope="session")
def rail():
    return load_feed(MINI / "rail", "rail")


@pytest.fixture(scope="session")
def bus():
    return load_feed(MINI / "bus", "bus")
