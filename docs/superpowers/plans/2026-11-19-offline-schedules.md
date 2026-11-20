# Offline LA Metro Schedules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An E Ink-friendly Android app for the Mudita Kompakt that shows LA Metro bus and rail schedules offline, fed by a prebuilt data pack that a Python converter makes from Metro's GTFS files and a weekly job republishes.

**Architecture:** `converter/` (Python) reads one or more GTFS feeds and writes one SQLite "pack" per agency plus an `index.json`. The Gradle project at the repo root has one module, `app/` (Kotlin, Jetpack Compose with all motion off), which bundles the pack as an asset, copies it to app storage on first run, reads it with plain SQL, and can replace it from a static base address. The pack schema in Task 6 is the contract between the two halves; a small fixture pack built by the converter is checked in and used by the app's tests.

**Tech Stack:** Python 3.11+ (stdlib only: `csv`, `zipfile`, `sqlite3`, `tomllib`, `urllib`), `uv`, `pytest`. Kotlin, Android Gradle Plugin, Jetpack Compose (current stable versions, pinned in `gradle/libs.versions.toml` at Task 8), JUnit4 + Robolectric. JDK 21 (installed). GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-19-transit-app-design.md`

## Global Constraints

- Device that must work: Mudita Kompakt, 800×480 portrait-locked E Ink, Android 12 (API 31), no Google Play services. `minSdk = 26`.
- No Google libraries that need Play services. Location only through `android.location.LocationManager`.
- No network use except the user tapping "Update schedule". No analytics, no accounts.
- Pure black (`#000000`) on white (`#FFFFFF`) only. No greys, gradients, shadows, images.
- No motion: no transitions, ripples, overscroll effects, or scrolling lists. Lists page with Prev/Next.
- A screen redraws only on a tap, on resume, and at most once a minute when a shown departure has passed.
- Times are clock times in the **agency's** time zone, 12-hour with `a`/`p` (`4:12p`, `12:10a`), never countdowns.
- Nothing LA-specific in app code; all agency facts come from the pack. LA specifics live in `converter/agencies/lametro.toml`.
- Converter fails with a non-zero exit and a message naming the file and row on data it cannot interpret; it never writes a partial pack.
- Pack format version is `1`. The app refuses any other version and keeps the pack it has.
- Placeholder identifiers, each defined in exactly one place: `applicationId`/namespace `inktransit.app` (`app/build.gradle.kts`), pack base address (`PACK_BASE_URL` build config field).
- Commits: `git commit --no-gpg-sign`, message ending `Co-Authored-By: Claude Fable 5.2 <noreply@anthropic.com>`.

## Facts about Metro's files (checked 2026-11-19)

These shaped the tasks; the converter must handle all of them.

- Two feeds. Bus: 114 routes, 11,892 stops, 33,614 trips, 2.1 M stop times. Rail: 6 routes, 6,924 trips, 159 k stop times.
- `trip_headsign` is blank on every trip in both feeds. The destination is in `stop_times.stop_headsign` as `"<line> - <destination>"` (e.g. `206 - Vermont / Athens Station`, `Metro A Line - Pomona Station`).
- Rail `route_short_name` is blank; `route_long_name` is `Metro D Line`. Bus short names include `10/48` and `Dodger Stadium Express`.
- Times are not zero-padded in the bus feed (`9:57:00`) and run past midnight (`26:13:00`).
- Rail stops use `location_type` 0 (platform), 1 (station), 2 (entrance) with `parent_station`. Bus stops have neither.
- **The rail feed only covers about two weeks ahead** (today: 2026-11-19 to 2026-12-03) with one `service_id` per date, and is republished nightly. The bus feed covers the whole service period (to 2026-12-12) with heavy use of `calendar_dates.txt`.
- Stop and route ids could collide across the two feeds, and bus route ids carry a suffix (`720-13201`) that changes between service periods.

## Review Focus

Conditions the spec implies but does not spell out, most likely first. Each has a test in the task named.

1. **Phone clock in another time zone** (rider travelling, or zone set wrong): "now" and all times are computed in the agency's zone. → Task 9.
2. **Update interrupted, corrupt, wrong version, or disk full:** the installed pack is untouched and still opens. → Task 15.
3. **No location fix** (underground, indoors, permission denied): Near me shows a plain message within 30 s and never hangs. → Task 14.
4. **A saved route's line or stop is gone after an update:** the row shows "No longer in schedule" and can be removed; no crash. → Task 11.
5. **A day with no service on a line, a date outside the pack, and a trip that returns to a stop it already served:** the next real departure or "No service" is shown, and a trip's final stop is never offered as a departure. → Task 9.

---

## File Structure

```
converter/
  pyproject.toml
  agencies/lametro.toml          LA Metro feed addresses and quirks
  src/packbuilder/
    gtfs.py                      read a feed; time parsing; id prefixing
    config.py                    AgencyConfig, load_config
    services.py                  expand calendars to per-day service sets
    patterns.py                  headsigns, patterns, trip deltas, direction stop lists
    places.py                    group stops into places
    frequency.py                 bands, per-line frequency and first/last
    writer.py                    pack schema, write_pack, write_index
    cli.py                       `packbuilder build`
  tests/
    fixtures/mini/rail/*.txt     hand-made feeds (Task 1)
    fixtures/mini/bus/*.txt
    fixtures/mini.toml
    test_*.py
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
app/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/assets/                 bundled pack (git-ignored, see Task 8)
  src/main/java/inktransit/app/
    pack/Varint.kt               decode trip deltas
    pack/PackDb.kt               open, version check, meta, effectiveDay
    pack/Queries.kt              departures, boards, lines, places
    pack/PackStore.kt            install bundled pack, swap in a new one
    time/ServiceClock.kt         agency-zone clock, service-day candidates, formatting
    text/FrequencyWords.kt       numbers to "every 10 min"
    data/SavedRoutes.kt          saved routes file
    update/PackUpdater.kt        index check, download, verify
    location/OneShotLocation.kt  single fix with timeout
    ui/Theme.kt, ui/Pager.kt, ui/Nav.kt
    ui/screens/{MyRoutes,Lines,LineOverview,DirectionStops,StopView,DayTimes,WhenPicker,Search,NearMe,About}.kt
    MainActivity.kt
  src/test/resources/mini.pack   fixture pack (checked in; built by converter)
  src/test/java/inktransit/app/...
.github/workflows/packs.yml
README.md
```

---

### Task 1: Converter scaffold, fixture feeds, GTFS reading

**Files:**
- Create: `converter/pyproject.toml`, `converter/src/packbuilder/{__init__,gtfs,config}.py`, `converter/agencies/lametro.toml`, `converter/tests/fixtures/mini/**`, `converter/tests/fixtures/mini.toml`, `converter/tests/test_gtfs.py`, `.gitignore`

**Interfaces — Produces:**
- `gtfs.parse_time(s: str) -> int` (seconds after service-day midnight)
- `gtfs.GtfsError(Exception)` — message includes file name and row number
- `gtfs.load_feed(source: str | Path, prefix: str) -> Feed`; `source` is a `.zip` path, a directory, or an `http(s)` address. Every id (`stop_id`, `route_id`, `trip_id`, `service_id`, `parent_station`) is returned as `f"{prefix}:{id}"`.
- `Feed` dataclass: `prefix, agency_name, timezone, routes: list[Route], stops: dict[str, Stop], trips: dict[str, Trip], stop_times: dict[str, list[StopTime]]` (per trip, sorted by `stop_sequence`), `calendar: list[CalendarRow]`, `calendar_dates: list[CalendarDate]`
- `Route(id, short_name, long_name, desc, type)`, `Stop(id, name, lat, lon, location_type: int, parent: str | None)`, `Trip(id, route_id, service_id, direction_id: int, headsign)`, `StopTime(stop_id, departure: int, stop_headsign)`
- `config.AgencyConfig(id, name, feeds: list[FeedSource(prefix, source)], rail_short_name_regex: str, headsign_strip_regex: str, place_radius_m: float = 100, place_max_span_m: float = 250, realtime_url: str = "")`, `config.load_config(path) -> AgencyConfig` (relative `source` paths resolve against the config file's folder)

**The fixture** (`tests/fixtures/mini/`, time zone `America/Los_Angeles`). Later tasks assert on these exact values.

Rail feed (`prefix = "rail"`), `calendar.txt` services `WK` (Mon–Fri), `SA`, `SU`, all 20261102–20261115:

| Item | Value |
|---|---|
| Route | `R1`, short name blank, long name `Metro D Line`, type 1 |
| Stations | `ALPHA_S`, `BETA_S`, `GAMMA_S` (location_type 1): `Alpha Station`, `Beta Station`, `Gamma Station` |
| Platforms | `ALPHA` (34.00000, -118.30000), `BETA` (34.00000, -118.29000), `GAMMA` (34.00000, -118.28000), each with its station as parent |
| Entrance | `ALPHA_E` location_type 2, parent `ALPHA_S` |
| Dir 0 | ALPHA → BETA (+120 s) → GAMMA (+180 s more); `stop_headsign` `Metro D Line - Gamma Station` |
| Dir 1 | GAMMA → BETA (+180 s) → ALPHA (+120 s more); `Metro D Line - Alpha Station` |
| WK, both dirs | every 10 min from 05:00:00 to 23:50:00, then 24:10:00, 24:40:00, 25:10:00 |
| WK short turn | dir 0, 23:55:00, ALPHA → BETA only, `Metro D Line - Beta Station` |
| SA and SU, both dirs | every 20 min 06:00:00 to 23:40:00 |

Bus feed (`prefix = "bus"`), services `WK`, `SA`, `SU`, 20261102–20261213; `calendar_dates.txt`: on 20261126, `WK` exception_type 2 and `SU` exception_type 1. All `trip_headsign` blank; times written unpadded (`6:00:00`).

| Item | Value |
|---|---|
| Stops (no parents) | `101` `Alpha / Main` (34.00030, -118.30000); `102` `Alpha / Main` (34.00035, -118.30040); `201` `Beta / Main` (34.00030, -118.29000); `301` `Far / Out` (34.02000, -118.25000) |
| Route `720-9`, short `720` | dir 0: 101 → 201 (+300 s) → 301 (+900 s more), `720 - Far / Out`. WK every 15 min 6:00:00–21:45:00; SU every 30 min 7:00:00–20:00:00; no SA |
| Route `617-9`, short `617` | dir 0: 301 → 201 (+900 s), `617 - Beta / Main`. WK hourly 7:00:00–19:00:00 only |
| Route `99-9`, short `99` | dir 0: 102 → 301 (+1200 s) → 102 (+1200 s more; a loop that ends where it starts), `99 - Alpha / Main`. WK at 6:30:00, 9:30:00, 15:30:00, 18:30:00 |

`mini.toml`: `id = "mini"`, `name = "Mini Transit"`, the two feeds as directories, `rail_short_name_regex = "^Metro (\\w+) Line$"`, `headsign_strip_regex = "^.+? - "`.

`lametro.toml`: `id = "lametro"`, `name = "LA Metro"`, feeds `rail = "https://gitlab.com/LACMTA/gtfs_rail/-/raw/master/gtfs_rail.zip"`, `bus = "https://gitlab.com/LACMTA/gtfs_bus/-/raw/master/gtfs_bus.zip"`, same two regexes.

- [ ] **Step 1:** Create the `uv` project (`requires-python = ">=3.11"`, dev dependency `pytest`, script entry `packbuilder = "packbuilder.cli:main"`). Write a small generator script `converter/tests/fixtures/make_mini.py` that writes the fixture `.txt` files from the tables above, run it, and commit both the script and its output.
- [ ] **Step 2: Write failing tests** in `tests/test_gtfs.py`:

```python
def test_parse_time_unpadded_and_past_midnight():
    assert parse_time("9:57:00") == 35820
    assert parse_time("26:13:00") == 94380
    with pytest.raises(GtfsError): parse_time("9:57")

def test_ids_are_prefixed(rail):            # rail = load_feed(MINI/"rail", "rail")
    assert "rail:ALPHA" in rail.stops and rail.stops["rail:ALPHA"].parent == "rail:ALPHA_S"

def test_stop_times_sorted_per_trip(bus):
    t = next(t for t in bus.trips.values() if t.route_id == "bus:720-9")
    assert [s.stop_id for s in bus.stop_times[t.id]] == ["bus:101", "bus:201", "bus:301"]

def test_blank_departure_fails_with_file_and_row(tmp_path):   # copy bus feed, blank one departure_time
    with pytest.raises(GtfsError, match=r"stop_times\.txt.*row \d+"): load_feed(tmp_path, "bus")

def test_zip_and_directory_load_the_same(tmp_path, rail): ...  # zip the rail dir, compare trip counts
```

- [ ] **Step 3:** Run `cd converter && uv run pytest tests/test_gtfs.py -v`; expect failures on missing module.
- [ ] **Step 4:** Implement `gtfs.py` and `config.py`. Read with `csv.DictReader` over `utf-8-sig`; strip whitespace from every field (bus `stop_lat` has leading spaces). Missing optional files/columns default to empty.
- [ ] **Step 5:** Run the tests; expect PASS. Commit.

### Task 2: Service days

**Files:** Create `converter/src/packbuilder/services.py`, `converter/tests/test_services.py`

**Interfaces:**
- Consumes: `Feed` (Task 1).
- Produces: `services.expand(feeds: list[Feed]) -> ServiceDays` with `days: dict[int, set[str]]` (key `YYYYMMDD`, value prefixed service ids active that day), `valid_from: int` (latest feed start), `valid_to: int` (**earliest** feed end), `first_day: int`, `last_day: int` (latest feed end).

Rule for the short rail window: after each feed's own last day, up to `last_day`, that feed's services for a date are copied from the feed's latest in-range date with the same weekday. `valid_to` stays the earliest feed end, so the app can tell real from projected.

- [ ] **Step 1: Failing tests:**

```python
def test_weekday_and_weekend(sd):           # sd = expand([rail, bus]) on the fixture
    assert sd.days[20261104] == {"rail:WK", "bus:WK"}
    assert sd.days[20261107] == {"rail:SA", "bus:SA"}
def test_holiday_exception(sd):             # Thanksgiving: bus runs Sunday service
    assert "bus:SU" in sd.days[20261126] and "bus:WK" not in sd.days[20261126]
def test_short_feed_projected_by_weekday(sd):
    assert "rail:WK" in sd.days[20261126]   # copied from Thu 20261112
    assert "rail:SU" in sd.days[20261213]
def test_validity(sd):
    assert (sd.valid_from, sd.valid_to, sd.first_day, sd.last_day) == (20261102, 20261115, 20261102, 20261213)
```

- [ ] **Step 2:** Run; expect FAIL. **Step 3:** Implement `expand`. **Step 4:** Run; expect PASS. Commit.

### Task 3: Headsigns, patterns, trip encoding, direction stop lists

**Files:** Create `converter/src/packbuilder/patterns.py`, `converter/tests/test_patterns.py`

**Interfaces:**
- Consumes: `Feed`, `AgencyConfig`.
- Produces:
  - `encode_deltas(departures: list[int]) -> bytes` — unsigned LEB128 varints, one per stop after the first, each the seconds since the previous stop's departure. A negative delta raises `GtfsError`.
  - `build(feeds, cfg) -> Lines` with:
    - `routes: list[LineRoute(key, short_name, long_name, kind, sort)]` — `kind` 0 for rail (GTFS types 0, 1, 2), 1 for bus; `short_name` is `route_short_name`, or the regex capture from `route_long_name` when blank (`Metro D Line` → `D`); `key = short_name`; `long_name` is `route_desc` when present, else `route_long_name`.
    - `patterns: list[Pattern(id, route_key, dir, stop_ids: tuple[str, ...], headsign)]` — one per distinct (route, direction, stop sequence, headsign).
    - `trips: list[PackedTrip(pattern_id, service_id, start: int, deltas: bytes)]`
    - `directions: list[Direction(route_key, dir, headsign, stop_ids: list[str], busiest_stop: str)]`
  - Sort order: rail first by short name; then buses by leading integer of the short name, then by the whole short name; names with no leading integer last.

Rules the tests do not fully determine:
- **Trip headsign:** `trip_headsign`, else the first stop time's `stop_headsign` with `headsign_strip_regex` removed, else the last stop's name.
- **Direction headsign:** the headsign carried by the most trips in that direction.
- **Direction stop list:** start from the pattern with the most trips (ties: more stops). For each other pattern, most trips first, walk its stops; a stop not yet in the list is inserted immediately after the list position of the nearest earlier stop in that pattern that is in the list (or at the front if none).
- **Busiest stop:** the stop in the direction with the most departures across all trips, excluding each pattern's final stop; ties go to the earliest in the stop list.
- Routes with the same short name across feeds or service suffixes merge into one line.

- [ ] **Step 1: Failing tests:**

```python
def test_deltas_roundtrip_values():
    assert encode_deltas([18000, 18120, 18300]) == bytes([120, 0xB4, 0x01])   # 120, 180
    assert encode_deltas([100]) == b""
def test_rail_short_name_from_long_name(lines):
    d = next(r for r in lines.routes if r.key == "D"); assert d.kind == 0
def test_sort_rail_first_then_numeric(lines):
    assert [r.key for r in lines.routes] == ["D", "99", "617", "720"]
def test_headsign_prefix_stripped(lines):
    assert direction(lines, "D", 0).headsign == "Gamma Station"
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
def test_trip_count(lines):   # D weekday dir 0: 114 regular + 3 late + 1 short turn
    ...
```

- [ ] **Step 2:** Run; FAIL. **Step 3:** Implement. **Step 4:** Run; PASS. Commit.

### Task 4: Places

**Files:** Create `converter/src/packbuilder/places.py`, `converter/tests/test_places.py`

**Interfaces:**
- Produces: `build_places(stops: list[Stop], used: set[str], radius_m: float, max_span_m: float) -> list[Place(id: int, name, lat, lon, stop_ids: list[str])]`. Only stops in `used` (stops some trip serves) are included; stations and entrances are never members.

Algorithm (not determined by the tests):
1. Seed groups: platforms sharing a `parent_station` form one group; every other used stop is its own group.
2. List all stop pairs closer than `radius_m` (haversine; use a lat/lon grid of `radius_m` cells so this is not quadratic), sorted by distance ascending, ties by ids.
3. For each pair in different groups, merge the groups unless the merged group's largest stop-to-stop distance would exceed `max_span_m`.
4. Name: the parent station's name if any member has one (the group's first by id if several); otherwise the most common member stop name, ties alphabetical. Position: mean of member positions. Ids assigned in order of (name, lat, lon).

- [ ] **Step 1: Failing tests:**

```python
def test_station_and_corner_stops_are_one_place(places):
    alpha = by_name(places, "Alpha Station")
    assert set(alpha.stop_ids) == {"rail:ALPHA", "bus:101", "bus:102"}
def test_far_stop_is_alone(places):
    assert by_name(places, "Far / Out").stop_ids == ["bus:301"]
def test_entrances_and_stations_excluded(places):
    assert not any("rail:ALPHA_E" in p.stop_ids or "rail:ALPHA_S" in p.stop_ids for p in places)
def test_chain_is_capped():      # five stops in a line 80 m apart, radius 100, max span 250
    assert max(len(p.stop_ids) for p in build_places(chain, set_of_all, 100, 250)) <= 4
def test_deterministic_ids(): ...  # same input shuffled gives identical output
```

- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. Commit.

### Task 5: Frequency and first/last trip

**Files:** Create `converter/src/packbuilder/frequency.py`, `converter/tests/test_frequency.py`

**Interfaces:**
- Produces:
  - `BANDS = [(14400, 21600), (21600, 32400), (32400, 54000), (54000, 68400), (68400, 79200), (79200, 100800)]` — early morning 4–6, morning rush 6–9, midday 9–15, evening rush 15–19, evening 19–22, night 22–28 h. A departure before 14400 counts in band 0.
  - `typical_days(sd: ServiceDays) -> dict[int, int]` — for day type 0 (Mon–Fri), 1 (Sat), 2 (Sun): among dates of that type between `valid_from` and `valid_to`, the earliest date whose service set is the most common one.
  - `summarize(lines: Lines, sd: ServiceDays) -> tuple[list[Freq(route_key, dir, day_type, band, trips, headway_min)], list[Span(route_key, dir, day_type, first, last)]]` — measured at the direction's busiest stop on the typical day. `headway_min` is the median gap between consecutive departures in the band, in minutes rounded half up, or `None` when the band has fewer than two departures. A direction with no departures that day type gets six rows with `trips = 0` and no `Span` row.

- [ ] **Step 1: Failing tests:**

```python
def test_typical_days(sd): assert typical_days(sd) == {0: 20261102, 1: 20261107, 2: 20261108}
def test_d_line_weekday_midday(f): assert row(f, "D", 0, 0, 2) == (36, 10)       # trips, headway
def test_d_line_night_counts_after_midnight_trips(f): assert row(f, "D", 0, 0, 5) == (16, 10)   # 22:00–23:50, 23:55, 24:10, 24:40, 25:10
def test_hourly_line(f): assert row(f, "617", 0, 0, 2) == (6, 60)
def test_few_trips(f): assert row(f, "99", 0, 0, 0) == (0, None) and row(f, "99", 0, 0, 1) == (1, None)
def test_no_saturday_service(f, spans):
    assert row(f, "720", 0, 1, 2) == (0, None) and not any(s.route_key == "720" and s.day_type == 1 for s in spans)
def test_span(spans): assert span(spans, "D", 0, 0) == (18000, 90600)   # 05:00 – 25:10
```

- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. Commit.

### Task 6: Pack writer, index, CLI, fixture pack

**Files:** Create `converter/src/packbuilder/{writer,cli}.py`, `converter/tests/test_writer.py`, `converter/tests/test_cli.py`, `app/src/test/resources/mini.pack`

**Interfaces — Produces (the contract with the app):**

```sql
CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
-- keys: format_version='1', agency_id, agency_name, timezone,
--       valid_from, valid_to, first_day, last_day (YYYYMMDD), built_at (ISO-8601 UTC), realtime_url
CREATE TABLE route(id INTEGER PRIMARY KEY, key TEXT NOT NULL UNIQUE, short_name TEXT NOT NULL,
                   long_name TEXT NOT NULL, kind INTEGER NOT NULL, sort INTEGER NOT NULL);
CREATE TABLE direction(route_id INTEGER, dir INTEGER, headsign TEXT NOT NULL, busiest_stop_id INTEGER,
                       PRIMARY KEY(route_id, dir)) WITHOUT ROWID;
CREATE TABLE place(id INTEGER PRIMARY KEY, name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL);
CREATE TABLE stop(id INTEGER PRIMARY KEY, key TEXT NOT NULL UNIQUE, place_id INTEGER NOT NULL,
                  name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL);
CREATE INDEX stop_place ON stop(place_id);
CREATE TABLE direction_stop(route_id INTEGER, dir INTEGER, seq INTEGER, stop_id INTEGER,
                            PRIMARY KEY(route_id, dir, seq)) WITHOUT ROWID;
CREATE TABLE pattern(id INTEGER PRIMARY KEY, route_id INTEGER NOT NULL, dir INTEGER NOT NULL,
                     headsign TEXT NOT NULL, stop_count INTEGER NOT NULL);
CREATE TABLE pattern_stop(pattern_id INTEGER, seq INTEGER, stop_id INTEGER,
                          PRIMARY KEY(pattern_id, seq)) WITHOUT ROWID;
CREATE INDEX pattern_stop_stop ON pattern_stop(stop_id);
CREATE TABLE trip(id INTEGER PRIMARY KEY, pattern_id INTEGER NOT NULL, service_id INTEGER NOT NULL,
                  start INTEGER NOT NULL, deltas BLOB NOT NULL);
CREATE INDEX trip_lookup ON trip(pattern_id, service_id, start);
CREATE TABLE service_day(day INTEGER, service_id INTEGER, PRIMARY KEY(day, service_id)) WITHOUT ROWID;
CREATE TABLE frequency(route_id INTEGER, dir INTEGER, day_type INTEGER, band INTEGER,
                       trips INTEGER NOT NULL, headway_min INTEGER,
                       PRIMARY KEY(route_id, dir, day_type, band)) WITHOUT ROWID;
CREATE TABLE span(route_id INTEGER, dir INTEGER, day_type INTEGER, first INTEGER NOT NULL, last INTEGER NOT NULL,
                  PRIMARY KEY(route_id, dir, day_type)) WITHOUT ROWID;
```

- `seq` is 0-based. `stop.key` is the prefixed GTFS stop id; `route.key` is the short name. These keys are what the app saves, because integer ids change on every rebuild.
- `writer.write_pack(path: Path, cfg, lines, places, sd, freq, spans, built_at: datetime) -> None` writes to `path.with_suffix(".tmp")`, runs `VACUUM`, then renames. Journal mode `DELETE` (a single file, no `-wal`).
- `writer.index_entry(pack: Path, gz: Path, cfg, sd, built_at) -> dict` with keys `agency_id, name, format_version, built_at, valid_from, valid_to, url` (the `.gz` file name), `size` (bytes of the `.gz`), `sha256` (hex, of the **uncompressed** pack).
- `index.json`: `{"format": 1, "packs": [entry, ...]}`.
- CLI: `packbuilder build --config <toml> --out <dir> [--built-at <ISO>]` writes `<id>.pack`, `<id>.pack.gz`, `index.json`; prints route, stop, place, trip counts and both sizes; exit code 1 with the `GtfsError` message on bad data, leaving no output files.

- [ ] **Step 1: Failing tests:**

```python
def test_meta(pack):       # pack = sqlite3 connection to a build of mini.toml with --built-at 2026-11-02T00:00:00Z
    m = dict(pack.execute("select key, value from meta"))
    assert m["format_version"] == "1" and m["timezone"] == "America/Los_Angeles"
    assert (m["valid_from"], m["valid_to"], m["first_day"], m["last_day"]) == ("20261102", "20261115", "20261102", "20261213")
def test_alpha_place_has_three_stops(pack): ...
def test_trip_blob_matches_pattern_length(pack):   # every trip: varints in deltas == stop_count - 1
def test_no_wal_or_tmp_left(tmp_path): ...
def test_index_sha_matches_pack(tmp_path): ...
def test_bad_feed_exits_1_and_writes_nothing(tmp_path): ...
def test_build_is_reproducible(tmp_path):          # same input and --built-at → identical bytes
```

- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS.
- [ ] **Step 5:** Build the fixture pack into the app's test resources and commit it:

Run: `cd converter && uv run packbuilder build --config tests/fixtures/mini.toml --out /tmp/mini --built-at 2026-11-02T00:00:00Z && mkdir -p ../app/src/test/resources && cp /tmp/mini/mini.pack ../app/src/test/resources/mini.pack`
Expected: counts line showing 4 routes, 7 stops, 4 places.

Add a test `test_checked_in_fixture_is_current` that rebuilds and compares bytes with `app/src/test/resources/mini.pack`, so the two halves cannot drift. Commit.

### Task 7: Build the real LA Metro pack and tune it

**Files:** Modify `converter/agencies/lametro.toml` (only if tuning requires), create `converter/tests/test_lametro_smoke.py` (marked `@pytest.mark.network`, skipped by default; `pyproject.toml` registers the marker)

- [ ] **Step 1:** Run `cd converter && uv run packbuilder build --config agencies/lametro.toml --out dist`. Expected: completes in under 5 minutes; prints sizes. **Record the `.pack` and `.pack.gz` sizes in the commit message.** Spec target: under about 30 MB uncompressed. If over, stop and report before changing the schema.
- [ ] **Step 2:** Write the smoke test, run with `uv run pytest -m network`:

```python
def test_lametro_shape(pack):
    keys = [k for (k,) in pack.execute("select key from route order by sort")]
    assert keys[:6] == ["A", "B", "C", "D", "E", "K"] and "720" in keys
    assert pack.execute("select count(*) from direction where headsign = ''").fetchone()[0] == 0
def test_wilshire_vermont_is_one_place(pack):
    # the place containing a D Line platform named 'Wilshire / Vermont Station' also has bus stops of line 720
```

- [ ] **Step 3: Place tuning.** Print the member stops of the places containing the D Line platforms at Wilshire/Vermont, Wilshire/Western, 7th St/Metro Center and Union Station, and of ten random bus-only places. Each should read as one intersection or station: no stop from the next intersection, no missing corner. Adjust `place_radius_m` / `place_max_span_m` in `lametro.toml` if not, and say what was changed and why in the commit.
- [ ] **Step 4: Spot checks against Metro.** For the D Line and line 720, compare the pack's weekday departures at one stop in each direction with Metro's published timetable (metro.net) for three times of day. They must match to the minute. Record what was compared in the commit message. Commit.

### Task 8: Android project, toolchain, E Ink shell on the device

**Files:** Create `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/inktransit/app/{MainActivity.kt,ui/Theme.kt,ui/Pager.kt,ui/Nav.kt}`, `app/src/test/java/inktransit/app/ui/PagerTest.kt`; modify `.gitignore`, `README.md`

**Interfaces — Produces:**
- `@Composable fun EInkTheme(content: @Composable () -> Unit)` — white background, black content, `LocalIndication provides NoIndication` (no ripple), overscroll off, text styles `Title` (bold 26 sp), `Row` (bold 22 sp), `Small` (bold 18 sp) as starting sizes to tune on the device.
- `fun <T> pageOf(items: List<T>, page: Int, pageSize: Int): List<T>`; `fun pageCount(n: Int, pageSize: Int): Int` (at least 1); `@Composable fun PagedList(items, pageSize, row)` with full-width `Prev` / `page x of y` / `Next` bar, each button at least 64 dp tall; `Prev`/`Next` drawn but inert at the ends.
- `sealed interface Screen`; `class Nav` holding a back stack as Compose state: `push(Screen)`, `pop(): Boolean`, `top`. `MainActivity` renders `nav.top` with a plain `when` (no animation wrapper) and maps the system back button to `pop()`.
- Manifest: portrait locked, `android:configChanges` not used, permissions `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`; no `allowBackup`.
- `BuildConfig.PACK_BASE_URL: String` from Gradle property `packBaseUrl`, default `""` (update disabled when empty); `BuildConfig.BUNDLED_PACK = "lametro.pack"`.
- Gradle task `:app:copyPack` copies `converter/dist/lametro.pack` to `app/src/main/assets/lametro.pack` and fails with "run the converter first" when missing; `preBuild` depends on it. `app/src/main/assets/*.pack` and `converter/dist/` are git-ignored.

- [ ] **Step 1: Toolchain.** `brew install --cask android-commandlinetools android-platform-tools`; accept licences; `sdkmanager "platforms;android-35" "build-tools;35.0.0"` (use the current stable API level if newer; `compileSdk`/`targetSdk` follow it). Record the `ANDROID_HOME` export for fish in the README. Verify: `adb devices` lists the Kompakt (the owner enables USB debugging; if it does not appear, stop and ask them).
- [ ] **Step 2:** Create the Gradle project with current stable AGP, Kotlin, Compose BOM, Robolectric and JUnit4 pinned in `libs.versions.toml`. No navigation, Material ripple, or animation libraries beyond what Compose foundation/material3 pulls in.
- [ ] **Step 3: Failing test** `PagerTest`:

```kotlin
@Test fun pages() {
    assertEquals(listOf(1, 2, 3), pageOf((1..7).toList(), 0, 3))
    assertEquals(listOf(7), pageOf((1..7).toList(), 2, 3))
    assertEquals(3, pageCount(7, 3)); assertEquals(1, pageCount(0, 3))
    assertEquals(emptyList<Int>(), pageOf(emptyList<Int>(), 0, 3))
}
```

- [ ] **Step 4:** Run `./gradlew :app:testDebugUnitTest`; FAIL. Implement `Pager.kt`, `Theme.kt`, `Nav.kt`, and a `MainActivity` showing a placeholder "MY ROUTES" screen with the bottom bar (`Lines`, `Near me`, `Search`) and a 25-item dummy `PagedList`. Run; PASS.
- [ ] **Step 5:** `./gradlew :app:installDebug`, then `adb exec-out screencap -p > /tmp/shot.png` and read the image. Expected: black on white, nothing clipped at 800×480, bars inside the screen.
- [ ] **Step 6: Owner checkpoint 1.** Ask the owner to look at the phone and page the dummy list: is the text comfortable, do taps land, is there ghosting after paging? Also open two of Mudita's own apps beside it and note their conventions (title placement, button style, back behaviour) to follow where practical. Apply their size/spacing feedback to `Theme.kt` before going on. Commit.

### Task 9: Reading the pack — store, clock, departures

**Files:** Create `app/src/main/java/inktransit/app/pack/{Varint,PackDb,Queries,PackStore}.kt`, `time/ServiceClock.kt`; tests `app/src/test/java/inktransit/app/pack/{VarintTest,PackDbTest,DeparturesTest}.kt`, `time/ServiceClockTest.kt`, shared `TestPacks.kt` (copies `mini.pack` from resources to a temp file and opens it)

**Interfaces — Produces:**
- `object Varint { fun offsetAt(deltas: ByteArray, index: Int): Int }` — seconds from the trip's start to stop `index` (0 → 0). Throws `IllegalArgumentException` if the blob is shorter than `index` varints.
- `class UnsupportedPackException(val found: String?) : Exception()`
- `class PackDb private constructor(...) : Closeable { companion object { fun open(file: File): PackDb }; val meta: PackMeta; fun effectiveDay(day: Int): Int? }` — opens read-only; throws `UnsupportedPackException` unless `meta.format_version == "1"`.
- `data class PackMeta(agencyId: String, agencyName: String, zone: ZoneId, validFrom: Int, validTo: Int, firstDay: Int, lastDay: Int, builtAt: Instant)`
- `effectiveDay(day)`: `day` itself when `firstDay ≤ day ≤ lastDay`; the latest in-range date with the same weekday when `day > lastDay`; the earliest such when `day < firstDay`; `null` only if the pack has no day of that weekday.
- `class ServiceClock(val zone: ZoneId, private val now: () -> Instant)`:
  - `fun now(): LocalDateTime` (in `zone`)
  - `fun candidates(at: LocalDateTime): List<ServiceTime>` → `[ServiceTime(previous calendar day, sec + 86400), ServiceTime(day, sec)]`
  - `fun toLocal(day: Int, sec: Int): LocalDateTime` — the service day's **noon minus 12 h** plus `sec` (the GTFS rule; correct across daylight-saving changes)
  - `fun format(t: LocalDateTime): String` → `4:12p`, `12:10a`
  - `fun dayLabel(t: LocalDateTime, relativeTo: LocalDateTime): String?` → `null` same day, `Tomorrow`, else `Sat Nov 21`
- `data class RouteRow(id: Int, key: String, shortName: String, longName: String, kind: Int)`
- `data class Departure(routeId: Int, dir: Int, stopId: Int, time: LocalDateTime, serviceDay: Int, otherHeadsign: String?)` — `otherHeadsign` is the pattern's headsign when it differs from the direction's, else `null`.
- `Queries` (extension functions on `PackDb`, taking a `ServiceClock`):
  - `fun nextDepartures(stopId: Int, routeId: Int, dir: Int, from: LocalDateTime, limit: Int): List<Departure>` — looks at the previous service day's after-midnight trips, the current day, then following days until `limit` is met or 7 days are searched. Never returns a departure from a pattern's final stop. Sorted by time.
  - `fun dayDepartures(stopId: Int, routeId: Int, dir: Int, day: Int): List<Departure>` — the whole service day `day`.
  - `fun placeBoard(placeId: Int, from: LocalDateTime, perRow: Int): List<BoardRow>`; `data class BoardRow(route: RouteRow, dir: Int, headsign: String, stopId: Int, stopName: String, departures: List<Departure>)` — one row per (route, direction, stop) at the place that has any service in the pack; ordered by route `sort`, then `dir`.
- `class PackStore(private val dir: File, private val bundled: () -> InputStream)`: `fun current(): PackDb` (packs live at `dir/<agency_id>.pack`, so more than one can be held later; `dir/current` names the one in use. Copies the bundled pack in on first use, or when the bundled pack's `built_at` is newer than the installed one); `fun replaceWith(candidate: File): PackDb` (opens `candidate` first — failures throw and change nothing — then closes the current one and renames atomically).

- [ ] **Step 1: Failing tests** (fixture facts from Task 1; `at("2026-11-04T16:03")` is a `LocalDateTime`; `la` = `ServiceClock(ZoneId.of("America/Los_Angeles")) { fixedInstant }`):

```kotlin
// VarintTest
@Test fun offsets() { val b = byteArrayOf(120, 0xB4.toByte(), 0x01)
    assertEquals(0, Varint.offsetAt(b, 0)); assertEquals(120, Varint.offsetAt(b, 1)); assertEquals(300, Varint.offsetAt(b, 2)) }

// PackDbTest
@Test fun rejectsOtherVersion()       // copy mini.pack, set format_version='2' → UnsupportedPackException
@Test fun effectiveDay() { assertEquals(20261104, db.effectiveDay(20261104)); assertEquals(20261213, db.effectiveDay(20261220))
    assertEquals(20261102, db.effectiveDay(20261026)) }

// ServiceClockTest
@Test fun formats() { assertEquals("4:12p", la.format(at("2026-11-04T16:12"))); assertEquals("12:10a", la.format(at("2026-11-05T00:10"))) }
@Test fun nowUsesAgencyZoneNotDeviceZone()   // Review Focus 1: TimeZone.setDefault(Tokyo); instant 2026-11-05T00:03Z → la.now() == 2026-11-04T16:03
@Test fun fallBackDayIsCorrect()             // clocks go back on 2026-11-01: toLocal(20261101, 7200) == 2026-11-01T02:00 (not 01:00) and toLocal(20261101, 43200) == 2026-11-01T12:00

// DeparturesTest — D = route 'D', ALPHA = stop key 'rail:ALPHA'
@Test fun nextThree() = assertTimes("4:10p 4:20p 4:30p", next(ALPHA, D, 0, at("2026-11-04T16:03"), 3))
@Test fun afterMidnightBelongsToPreviousDay() = assertTimes("12:10a 12:40a 1:10a", next(ALPHA, D, 0, at("2026-11-05T00:05"), 3))
@Test fun endOfServiceRollsToNextDay() { val d = next(ALPHA, D, 0, at("2026-11-05T01:15"), 1).single()
    assertEquals(at("2026-11-05T05:00"), d.time) }
@Test fun shortTurnIsMarked() { val d = next(ALPHA, D, 0, at("2026-11-04T23:52"), 1).single()
    assertEquals("11:55p", la.format(d.time)); assertEquals("Beta Station", d.otherHeadsign) }
@Test fun terminusHasNoDepartures() = assertTrue(next(GAMMA, D, 0, at("2026-11-04T16:03"), 3).isEmpty())
@Test fun holidayUsesSundayBusService() = assertTimes("7:00a 7:30a", next(S101, R720, 0, at("2026-11-26T06:00"), 2))
@Test fun railOnProjectedDay() = assertTimes("4:10p", next(ALPHA, D, 0, at("2026-11-26T16:03"), 1))
@Test fun dayBeyondPackUsesSameWeekday() = assertEquals(54, db.dayDepartures(ALPHA, D, 0, 20261220).size)   // Sunday
// Review Focus 5
@Test fun noSaturdayServiceSkipsToSunday() { val d = next(S101, R720, 0, at("2026-11-07T10:00"), 1).single(); assertEquals(at("2026-11-08T07:00"), d.time) }
@Test fun loopStopListsFirstVisitOnly() = assertTimes("6:30a 9:30a", next(S102, R99, 0, at("2026-11-04T06:00"), 2))   // final-stop visit is not a departure
@Test fun placeBoardListsEveryLineAtPlace() { val rows = db.placeBoard(alphaPlace, at("2026-11-04T16:03"), 2)
    assertEquals(listOf("D→Gamma Station", "99→Alpha / Main", "720→Far / Out"), rows.map { "${it.route.shortName}→${it.headsign}" }) }
```

`D → Alpha Station` is absent from that list on purpose: ALPHA is that direction's final stop, and a row whose stop is only ever a final stop is dropped.

- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest`; FAIL. **Step 3:** Implement. Query shape for departures: `pattern_stop` rows for the stop (with `seq < stop_count - 1`) joined to `pattern` filtered by route and direction; trips of those patterns whose `service_id` is in `service_day` for `effectiveDay(day)`; departure = `start + Varint.offsetAt(deltas, seq)`; filter and sort in Kotlin. **Step 4:** PASS. Commit.

### Task 10: Lines, frequency wording, search

**Files:** Create `app/src/main/java/inktransit/app/text/FrequencyWords.kt`; extend `pack/Queries.kt`; tests `text/FrequencyWordsTest.kt`, `pack/LinesTest.kt`

**Interfaces — Produces:**
- Consumes `RouteRow` (Task 9). `data class DirectionRow(routeId: Int, dir: Int, headsign: String)`; `data class StopRow(id: Int, key: String, placeId: Int, name: String)`; `data class PlaceRow(id: Int, name: String, lat: Double, lon: Double)`; `data class FreqRow(dir: Int, band: Int, trips: Int, headwayMin: Int?)`; `data class SpanRow(dir: Int, first: Int, last: Int)`
- `fun PackDb.routes(): List<RouteRow>` (by `sort`); `fun route(key: String): RouteRow?`; `fun stop(key: String): StopRow?`; `fun directions(routeId): List<DirectionRow>`; `fun directionStops(routeId, dir): List<StopRow>`; `fun frequency(routeId, dayType): List<FreqRow>`; `fun spans(routeId, dayType): List<SpanRow>`; `fun allFrequencyTags(dayType): Map<Int, String>` (route id → tag, one query); `fun searchPlaces(q: String, limit: Int): List<PlaceRow>`; `fun place(id): PlaceRow`
- `fun dayTypeOf(day: Int): Int` — 0 Mon–Fri, 1 Sat, 2 Sun.
- `object FrequencyWords`:
  - `val BAND_NAMES = listOf("Early morning", "Morning rush", "Midday", "Evening rush", "Evening", "Night")`
  - `fun snap(min: Int): Int` — nearest of 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 45, 60 (ties go down)
  - `fun band(trips: Int, headwayMin: Int?): String` — `no service` (0 trips); `1 trip`; with a headway: `about hourly` when 53–75, `"$trips trips"` when over 75, else `every ${snap} min`
  - `fun tag(rows: List<FreqRow>): String` — `no service` if no trips at all; `"$n trips a day"` when the busier direction has 8 or fewer trips in total; otherwise from bands 1–3 with a headway, across both directions: one snapped value → `every 10 min` / `about hourly`; a range → `every 5–10 min` (an hourly top end prints as 60).
- Search: case-insensitive; every whitespace-separated word of `q` must appear in the place name; `/` and punctuation ignored on both sides; names starting with the first word rank first, then alphabetical.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun snaps() { assertEquals(6, snap(7)); assertEquals(8, snap(8)); assertEquals(10, snap(11)); assertEquals(60, snap(58)) }
@Test fun bandWords() { assertEquals("every 10 min", band(36, 10)); assertEquals("about hourly", band(6, 60))
    assertEquals("no service", band(0, null)); assertEquals("1 trip", band(1, null)); assertEquals("3 trips", band(3, 120)) }
@Test fun tags() { assertEquals("every 10 min", tag(db.frequency(D, 0))); assertEquals("about hourly", tag(db.frequency(R617, 0)))
    assertEquals("4 trips a day", tag(db.frequency(R99, 0))); assertEquals("no service", tag(db.frequency(R720, 1)))
    assertEquals("every 15 min", tag(db.frequency(R720, 0))) }
@Test fun rangeTag() = assertEquals("every 5–10 min", tag(listOf(FreqRow(0, 1, 36, 5), FreqRow(0, 2, 36, 10), FreqRow(0, 3, 48, 5))))
@Test fun routesInOrder() = assertEquals(listOf("D", "99", "617", "720"), db.routes().map { it.shortName })
@Test fun directionStopsInTravelOrder() = assertEquals(listOf("Alpha Station", "Beta Station", "Gamma Station"), db.directionStops(D, 0).map { it.name })
@Test fun searchIgnoresSlashAndCase() { assertEquals("Far / Out", db.searchPlaces("far out", 10).single().name)
    assertEquals(listOf("Alpha Station"), db.searchPlaces("alph", 10).map { it.name }) }
@Test fun searchEmptyQueryReturnsNothing() = assertTrue(db.searchPlaces("  ", 10).isEmpty())
```

- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. Commit.

### Task 11: Saved routes and My Routes screen

**Files:** Create `app/src/main/java/inktransit/app/data/SavedRoutes.kt`, `ui/screens/MyRoutes.kt`; modify `MainActivity.kt`; test `data/SavedRoutesTest.kt`, `ui/MyRoutesModelTest.kt`

**Interfaces — Produces:**
- `data class SavedRoute(agencyId: String, routeKey: String, dir: Int, stopKey: String, routeName: String, headsign: String, stopName: String)` — names are kept so a route that no longer resolves can still be shown.
- `class SavedRoutes(private val file: File)`: `fun all(): List<SavedRoute>`; `fun add(r: SavedRoute)` (no duplicates by agency, route, dir, stop); `fun remove(r)`; `fun move(r, up: Boolean)`; `fun contains(agencyId, routeKey, dir, stopKey): Boolean`. Stored as JSON (`org.json`), written to a temp file then renamed. An unreadable file is treated as empty and renamed `saved.bad`, not deleted.
- `data class RouteCard(saved: SavedRoute, resolved: Boolean, times: List<String>, dayLabel: String?, placeId: Int?)`
- `fun buildCards(db: PackDb, clock: ServiceClock, saved: List<SavedRoute>, now: LocalDateTime): List<RouteCard>` — three next departures each; `resolved = false` when `db.route(routeKey)` or `db.stop(stopKey)` is null or the agency differs.
- `fun nextRedraw(cards: List<RouteCard>, ...)`: the screen recomposes when the earliest shown departure's minute has passed, checked by a once-a-minute tick that runs only while the screen is resumed.

Screen (spec sketch "MY ROUTES"): 4 cards per page; each card shows `routeName → headsign`, stop name, three times; an unresolved card shows `No longer in schedule`; a card whose next departure is not today shows the day label before the times; empty state text: `No routes yet. Tap + Add, pick a line, a direction, then your stop.` `Edit` toggles per-card `Up`, `Down`, `Remove` buttons. Tapping a resolved card opens the stop view for its place. Bottom bar: `Lines`, `Near me`, `Search`, and `About` reachable from the title row.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun addRemoveMovePersist()            // add a, b; move b up; reopen file → [b, a]; remove a → [b]
@Test fun noDuplicates()
@Test fun corruptFileBecomesEmptyAndIsKept()  // write "{{{" → all() empty, saved.bad exists
@Test fun cardsShowNextThree() { val c = buildCards(db, la, listOf(dAlpha), at("2026-11-04T16:03")).single()
    assertEquals(listOf("4:10p", "4:20p", "4:30p"), c.times); assertNull(c.dayLabel) }
@Test fun cardAfterServiceShowsTomorrow()   // at 2026-11-05T01:15 → times start "5:00a", dayLabel == null (same calendar day); at 2026-11-04T23:59 for 720 → "Tomorrow"
@Test fun missingStopIsUnresolvedNotACrash()   // Review Focus 4: stopKey "bus:999" → resolved == false, times empty
@Test fun otherAgencyIsUnresolved()
```

- [ ] **Step 2:** FAIL. **Step 3:** Implement store, model and screen. **Step 4:** PASS.
- [ ] **Step 5:** Install; seed one saved route through a debug-only intent extra or by using the Lines flow once Task 12 lands (if seeding now, remove the seed before commit). Screenshot and check against the sketch. Commit.

### Task 12: Lines, line overview, direction stops

**Files:** Create `ui/screens/{Lines,LineOverview,DirectionStops}.kt`; modify `ui/Nav.kt` (screens `Lines`, `LineOverview(routeId)`, `DirectionStops(routeId, dir)`, `StopView(placeId, highlightStopId?)`); test `ui/LinesModelTest.kt`

**Interfaces — Produces:**
- `fun filterLines(routes: List<RouteRow>, typed: String): List<RouteRow>` — empty `typed` returns all; otherwise lines whose short name starts with `typed` (case-insensitive), then lines containing it.
- `data class OverviewDay(dayType: Int, firstLast: String?, bands: List<Pair<String, String>>)`; `fun overview(db, clock, routeId): List<OverviewDay>` — for each day type: `firstLast` like `5:00a – 1:10a` from the earliest `first` and latest `last` across directions (`null` when no service); band wording uses, per band, the direction with more trips; if the two directions' wording differs in any band, the day shows both directions separately, labelled by headsign.

Screens:
- **Lines:** title `LINES` with the current day type on the right (`Weekday`/`Saturday`/`Sunday`, from `clock.now()`); a text field for jumping (numeric keyboard with a switch to letters); rows `shortName` + frequency tag, 7 per page; tap → overview.
- **Line overview:** title `<shortName> LINE` for rail, `LINE <shortName>` for bus, long name beneath in `Small`; the current day type expanded with first/last and six band rows; the other two day types as buttons that expand in place (one expanded at a time); bottom: one button per direction, `→ headsign`.
- **Direction stops:** title `<shortName> → headsign`; stops in travel order, 7 per page; tap → `StopView(place of stop, highlightStopId = stop)`.

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun filter() { assertEquals(listOf("617"), filterLines(routes, "6").map { it.shortName }); assertEquals(4, filterLines(routes, "").size)
    assertEquals(listOf("D"), filterLines(routes, "d").map { it.shortName }) }
@Test fun overviewWeekday() { val wk = overview(db, la, D).first { it.dayType == 0 }
    assertEquals("5:00a – 1:10a", wk.firstLast); assertEquals("Midday" to "every 10 min", wk.bands[2]) }
@Test fun overviewNoService() { val sa = overview(db, la, R720).first { it.dayType == 1 }; assertNull(sa.firstLast) }
```

- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5:** Install, screenshot each screen, check against the spec sketches. Commit.

### Task 13: Stop view, full-day times, "When" picker

**Files:** Create `ui/screens/{StopView,DayTimes,WhenPicker}.kt`; test `ui/WhenTest.kt`, `ui/StopViewModelTest.kt`

**Interfaces — Produces:**
- `sealed interface WhenChoice { object Now; data class At(val time: LocalDateTime) }`, held in `Nav` (in memory only, so it resets when the app process restarts) and reset to `Now` in `MainActivity.onStart` after the app has been in the background.
- `fun dayOptions(today: LocalDate, count: Int = 28): List<Pair<LocalDate, String>>` — labels `Today`, `Tomorrow`, then `Sat Nov 21`.
- `fun isBeyondPublished(meta: PackMeta, viewedDay: Int, today: Int): Boolean` — `today > validTo || viewedDay > validTo`.
- `fun boardRows(db, clock, placeId, whenChoice): List<BoardRow>` using `placeBoard(..., perRow = 2)`.

Screens:
- **Stop view:** title = place name. A `When: Now` button under the title; with a chosen time it reads `When: Sat Nov 21, 9:00a` and is drawn inverted (white on black) so it cannot be missed. Rows: `shortName → headsign`, two times, a star (☆/★) that saves or unsaves that row's (route, direction, stop). Where a place has the same route and direction at more than one stop, each row also shows its stop name in `Small`. A departure with `otherHeadsign` shows `→ <otherHeadsign>` after its time. The row matching `highlightStopId` is listed first. 5 rows per page. Tapping a row (not the star) opens Day times. A row with no departures within 7 days shows `No service`.
- **Day times:** title `shortName → headsign`, stop name, the day label; all departures of that service day in a 4-column grid, 6 rows per page, opening on the page containing the first departure at or after the chosen time; `When` button as above.
- **When picker:** step 1 day list from `dayOptions`, 7 per page, plus `Now` at the top; step 2 hour grid `4a … 3a` (24 buttons, 4 columns); step 3 `:00 :15 :30 :45`. Choosing sets `WhenChoice.At` and returns.
- **Out-of-date line:** when `isBeyondPublished` is true, Stop view, Day times and My Routes show one line under the title: `Schedule may be out of date` (today past `validTo`) or `Beyond published schedule — times may change` (a future day past `validTo`).

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun dayLabels() { val o = dayOptions(LocalDate.of(2026, 11, 19)); assertEquals(listOf("Today", "Tomorrow", "Sat Nov 21"), o.take(3).map { it.second }); assertEquals(28, o.size) }
@Test fun beyondPublished() { assertFalse(isBeyondPublished(meta, 20261110, 20261104)); assertTrue(isBeyondPublished(meta, 20261126, 20261104)); assertTrue(isBeyondPublished(meta, 20261116, 20261116)) }
@Test fun boardAtChosenTime() { val rows = boardRows(db, la, alphaPlace, WhenChoice.At(at("2026-11-07T09:05")))   // Saturday
    assertEquals(listOf("9:20a", "9:40a"), rows.first { it.route.shortName == "D" }.departures.map { la.format(it.time) }) }
@Test fun noServiceRowIsKept()   // 617 at Far / Out on a Saturday morning still appears; its next departure is Monday
```

- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5:** Install; walk Lines → D → direction → stop → star → back to My Routes; screenshot each. Commit.

### Task 14: Search and Near me

**Files:** Create `ui/screens/{Search,NearMe}.kt`, `location/OneShotLocation.kt`; extend `pack/Queries.kt`; test `pack/NearestTest.kt`, `location/OneShotLocationTest.kt`

**Interfaces — Produces:**
- `fun PackDb.nearestPlaces(lat: Double, lon: Double, limit: Int): List<Pair<PlaceRow, Int>>` — distance in metres (haversine), nearest first; SQL bounding box first, widening from 0.01° until `limit` places are found or the box exceeds 0.5°.
- `fun formatDistance(m: Int, locale: Locale): String` — feet and miles for locales whose country is `US`, metres and kilometres otherwise. Under 1,000 ft: feet rounded to the nearest 50 (`350 ft`); otherwise miles to one decimal (`0.4 mi`). Under 1,000 m: metres rounded to the nearest 10 (`120 m`); otherwise kilometres to one decimal (`1.2 km`).
- `sealed interface Fix { data class Fresh(lat, lon); data class Stale(lat, lon, ageMinutes: Long); object Denied; object Off; object None }`
- `class OneShotLocation(private val source: LocationSource, private val timeoutMs: Long = 30_000)`: `suspend fun get(): Fix`. `LocationSource` is a small interface over `LocationManager` (`hasPermission()`, `enabledProviders()`, `suspend current(provider): Location?`, `lastKnown(): Location?`) so it can be faked. Asks GPS and network providers together, takes the first result; on timeout returns `Stale` from the last known location if it is under 30 minutes old, else `None`.

Screens:
- **Search:** text field (focused, keyboard open), results 6 per page, tap → Stop view. Results update on each keystroke only after 2 or more characters.
- **Near me:** requests permission on first entry; shows `Finding location…` (static text); then 6 nearest places with distance, 6 per page. Messages: `Location permission is off. Turn it on in Settings to use Near me.` (Denied), `Location is switched off on this phone.` (Off), `Couldn't find your location. Try again outside.` with a `Try again` button (None), and above the list `Using your location from N min ago` (Stale).

- [ ] **Step 1: Failing tests:**

```kotlin
@Test fun nearestOrder() { val r = db.nearestPlaces(34.0001, -118.2999, 2); assertEquals("Alpha Station", r[0].first.name); assertTrue(r[0].second < 100) }
@Test fun nearestFarAwayStillReturnsWithinLimitBox() = assertTrue(db.nearestPlaces(40.0, -100.0, 3).isEmpty())
@Test fun distanceWords() { assertEquals("350 ft", formatDistance(107, Locale.US)); assertEquals("0.4 mi", formatDistance(640, Locale.US)); assertEquals("120 m", formatDistance(120, Locale.GERMANY)) }
// Review Focus 3
@Test fun deniedReturnsImmediately() = runTest { assertEquals(Fix.Denied, OneShotLocation(fake(permission = false)).get()) }
@Test fun timeoutFallsBackToRecentLastKnown() = runTest { /* current() never returns; lastKnown 5 min old → Stale(…, 5) after 30 s virtual time */ }
@Test fun timeoutWithOldLastKnownIsNone() = runTest { /* lastKnown 3 h old → Fix.None */ }
@Test fun providersOffIsOff() = runTest { assertEquals(Fix.Off, OneShotLocation(fake(providers = emptyList())).get()) }
```

- [ ] **Step 2:** FAIL. **Step 3:** Implement (`kotlinx-coroutines-test` for virtual time; `LocationManager.getCurrentLocation` on API 30+, `requestSingleUpdate` below). **Step 4:** PASS. **Step 5:** Install; try Near me on the device near a window and note the time to a fix in the commit message. Commit.

### Task 15: About, schedule update

**Files:** Create `update/PackUpdater.kt`, `ui/screens/About.kt`; test `update/PackUpdaterTest.kt`

**Interfaces — Produces:**
- `data class UpdateInfo(url: URL, size: Long, sha256: String, builtAt: Instant, validTo: Int)`
- `sealed interface UpdateResult { object UpToDate; data class Updated(meta: PackMeta); data class Failed(reason: String) }`
- `class PackUpdater(private val baseUrl: String, private val store: PackStore, private val workDir: File, private val open: (URL) -> InputStream = { it.openStream() })`:
  - `fun check(): UpdateInfo?` — fetches `index.json`; returns the entry for the installed pack's `agency_id` only if its `format_version` is 1 and its `built_at` is later than the installed pack's.
  - `fun update(progress: (Int) -> Unit): UpdateResult` — downloads the `.gz` to `workDir`, gunzips to a temp file, checks size and SHA-256, calls `store.replaceWith`, deletes temp files in a `finally`. `progress` is called at most once per 10 %.
  - Failure reasons, as shown to the user: `No connection.`, `Download didn't complete. Try again.`, `The new schedule needs a newer version of this app.`, `Not enough space on the phone.`, `The downloaded schedule was damaged. Try again.`
- About screen: agency name; `Schedule valid <Nov 2> – <Nov 15>`; `Built <Nov 2>`; `Update schedule` button; progress as text (`Downloading 40%`), no bar animation; result line. When `BuildConfig.PACK_BASE_URL` is empty the button is replaced by `Updates not set up in this build`. App version at the bottom.

- [ ] **Step 1: Failing tests** (index and pack served from a temp folder through the injected `open`; a second fixture `mini-newer.pack` is made in the test by copying `mini.pack` and bumping `built_at`):

```kotlin
@Test fun upToDateWhenIndexIsNotNewer()
@Test fun updatesAndNewPackIsCurrent()                   // store.current().meta.builtAt == newer
// Review Focus 2 — after each of these, store.current() still opens and has the ORIGINAL builtAt, and workDir is empty
@Test fun interruptedDownloadKeepsOldPack()              // stream throws IOException halfway
@Test fun wrongChecksumKeepsOldPack()
@Test fun truncatedGzipKeepsOldPack()
@Test fun newerFormatVersionIsRefusedBeforeDownload()    // index entry format_version 2 → check() == null; a pack with version 2 behind a v1 index entry → Failed("…newer version of this app.")
@Test fun diskFullKeepsOldPack()                         // workDir is a file, not a folder → Failed, old pack intact
@Test fun noConnection()                                 // open throws UnknownHostException → Failed("No connection.")
```

- [ ] **Step 2:** FAIL. **Step 3:** Implement; run `update` off the main thread and post results back. **Step 4:** PASS.
- [ ] **Step 5:** End-to-end check against a local address: `cd converter/dist && python3 -m http.server 8000`, `adb reverse tcp:8000 tcp:8000`, build with `-PpackBaseUrl=http://localhost:8000/` (add a debug-only network security config allowing cleartext to `localhost`), install an APK bundling an older pack, tap `Update schedule`, confirm the dates change. Commit.

### Task 16: Weekly pack job and README

**Files:** Create `.github/workflows/packs.yml`, `README.md`

- Workflow `packs`: triggers `schedule: cron "0 11 * * 1"` (Mondays 3 am Pacific) and `workflow_dispatch`; `permissions: contents: write`; steps: checkout, install `uv`, `uv run pytest` in `converter/`, `uv run packbuilder build --config agencies/lametro.toml --out dist`, then `gh release upload packs dist/lametro.pack.gz dist/index.json --clobber` (creating the `packs` release first if absent). A failed build uploads nothing, so the last good pack stays published.
- App default once the repository exists: `packBaseUrl = https://github.com/<owner>/<repo>/releases/download/packs/` in `gradle.properties`.
- README: what the app is; install on a Kompakt over ADB; build from source (toolchain from Task 8, run the converter, `./gradlew installDebug`); how to add an agency config; the pack format section pointing at `writer.py`.

- [ ] **Step 1:** Write the workflow and README. Validate the workflow file with `gh workflow view` after push, or `actionlint` if available.
- [ ] **Step 2: Blocked on the owner:** the repository needs a name and a licence. When they give both: create the public repository, push, set `packBaseUrl`, run the workflow once by hand, and confirm `index.json` downloads from the release address. Until then, commit the files and leave `packBaseUrl` empty.

### Task 17: Device pass with the owner

- [ ] **Step 1:** Build the current LA Metro pack, install a fresh build, run the whole unit suite (`cd converter && uv run pytest`; `./gradlew :app:testDebugUnitTest`). Both must pass.
- [ ] **Step 2: Owner checkpoint 2,** on the Kompakt, with real data: add the D Line and a bus route they ride; check My Routes against a known departure; open a transfer stop and judge whether the place grouping is right; look at the Lines list tags and one line overview; pick a future Saturday; try Near me outdoors and Search. Ask specifically about legibility, ghosting after paging, mis-taps, and anything that redraws when it should not.
- [ ] **Step 3:** Fix what they report (type sizes and page sizes in `Theme.kt` and the screens; place distances in `lametro.toml`), re-run the suites, commit.
