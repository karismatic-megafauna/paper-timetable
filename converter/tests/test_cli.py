import shutil

from packbuilder.cli import main

from conftest import FIXTURES


def broken_config(tmp_path):
    shutil.copytree(FIXTURES / "mini", tmp_path / "mini")
    shutil.copy(FIXTURES / "mini.toml", tmp_path / "mini.toml")
    st = tmp_path / "mini" / "bus" / "stop_times.txt"
    st.write_text(st.read_text().replace(",6:00:00,6:00:00,", ",banana,banana,", 1))
    return tmp_path / "mini.toml"


def test_bad_feed_exits_1_and_writes_nothing(tmp_path, capsys):
    out = tmp_path / "out"
    assert main(["build", "--config", str(broken_config(tmp_path)), "--out", str(out)]) == 1
    assert "stop_times.txt row" in capsys.readouterr().err
    assert not out.exists() or not any(out.iterdir())


def test_prints_counts_and_sizes(tmp_path, capsys):
    assert main(["build", "--config", str(FIXTURES / "mini.toml"), "--out", str(tmp_path / "o")]) == 0
    text = capsys.readouterr().out
    assert "4 routes" in text and "7 stops" in text and "4 places" in text and "mini.pack" in text
