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


@pytest.fixture(scope="session")
def cfg():
    from packbuilder.config import load_config
    return load_config(MINI_TOML)


@pytest.fixture(scope="session")
def sd(rail, bus):
    from packbuilder.services import expand
    return expand([rail, bus])


@pytest.fixture(scope="session")
def lines(rail, bus, cfg):
    from packbuilder.patterns import build
    return build([rail, bus], cfg)
