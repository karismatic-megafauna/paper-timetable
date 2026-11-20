# Paper Timetable

Transit schedules and headways for the Mudita Kompakt, offline. A paper
timetable, not a live tracker: it shows what the agency has published.

A small Android app that shows public-transit schedules on an E Ink phone,
offline. It starts with LA Metro (bus and rail) and is built so other
agencies can be added.

- **My Routes**: the lines you ride, each with its next three departures.
- **Lines**: every line with how often it runs ("every 10 min", "about
  hourly"), then direction, then stops.
- **Stops**: everything that leaves from a place, for deciding whether to get
  off and change.
- **When**: look at any day and time in the next four weeks.
- **Near me** and **Search** to find a stop.

It is pure black on white, nothing animates or scrolls, and it uses the
network only when you tap *Update schedule* and your location only when you
tap *Near me*. No accounts, no tracking, no Google services.

## How it fits together

```
converter/   Python. Reads an agency's GTFS feeds, writes one "pack"
             (a SQLite file) per agency plus index.json.
app/         Kotlin. Bundles a pack, reads it, and can download a newer one.
docs/        The design and the build plan.
```

The pack is the contract between the two halves. Its schema is in
`converter/src/packbuilder/writer.py`; the app reads it in
`app/src/main/java/io/github/karismatic_megafauna/papertimetable/pack/`. A small fixture pack built from
hand-made feeds (`converter/tests/fixtures/`) is checked in at
`app/src/test/resources/mini.pack` and both test suites use it. If you change
the fixture or the schema, rebuild it:

```sh
cd converter
uv run packbuilder build --config tests/fixtures/mini.toml --out /tmp/mini --built-at 2026-11-02T00:00:00Z
cp /tmp/mini/mini.pack ../app/src/test/resources/mini.pack
```

## Install on a Kompakt

1. Download `paper-timetable-<version>.apk` from the
   [latest release](https://github.com/karismatic-megafauna/paper-timetable/releases/latest).
2. On the phone, enable developer options and USB debugging.
3. Connect it by USB and accept the prompt on the phone. `adb devices` should list it.
4. `adb install paper-timetable-<version>.apk`

The app shows as **Timetable**. Later versions install over it the same way
and keep your saved routes.

## Build from source

You need [uv](https://docs.astral.sh/uv/), JDK 17 or newer, and the Android SDK
(platform 36, build-tools 36.0.0). On macOS:

```sh
brew install --cask android-commandlinetools android-platform-tools
sdkmanager "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
```

For fish, to keep `ANDROID_HOME` set:
`set -Ux ANDROID_HOME /opt/homebrew/share/android-commandlinetools`

Then:

```sh
# 1. Build the schedule pack (downloads LA Metro's feeds; about 15 seconds)
cd converter && uv run packbuilder build --config agencies/lametro.toml --out dist && cd ..

# 2. Build and install the app (it bundles converter/dist/lametro.pack)
./gradlew :app:installDebug

# Both test suites
./test.sh
```

## Releases

`./gradlew :app:assembleRelease` writes `app/build/outputs/apk/release/`.

Builds published by this project are signed with its release key. An APK that
claims to be Paper Timetable should show this certificate (check with
`apksigner verify --print-certs`):

```
CN=Paper Timetable, O=karismatic-megafauna
SHA-256 7b9a36b6e051ec46de64e69d3d5b496319f67c46568d694c9e2b6b067569d895
```

The key is not in this repository. The build signs a release only when these
are set in `~/.gradle/gradle.properties` on the machine doing the build:

```
paperTimetableStoreFile=/path/to/release.jks
paperTimetableStorePassword=…
paperTimetableKeyAlias=paper-timetable
```

Without them the release build still works and comes out unsigned; sign it
with a key of your own to install it. Android only lets an app be updated by
a build signed with the same key, so a phone moves between debug builds,
your own builds and the project's builds by uninstalling first.

## Schedule updates

`.github/workflows/packs.yml` rebuilds the pack every Monday and uploads it,
named with its build time (`lametro-20261123T110000Z.pack.gz`), and then
`index.json`, to a release called `packs`. The index is replaced last and is
what makes a new pack live.

GitHub disables scheduled workflows after 60 days without repository
activity. The job re-enables itself on every run to avoid that; this has not
been observed over a full 60 days yet, so check the Actions tab now and then.
If it has stopped, run it by hand from there. The app looks
for them at the address in `gradle.properties`:

```
packBaseUrl=https://github.com/<owner>/<repo>/releases/download/packs/
```

While that is empty, the About screen says updates are not set up and the app
simply uses the pack it shipped with.

LA Metro publishes rail schedules only about two weeks ahead. The app shows
the dates its schedule is valid for, and says so plainly when you look past
them.

## Adding an agency

Add a file in `converter/agencies/` like `lametro.toml`: an id, a name, and
one or more GTFS feeds, each with a short prefix. The regexes there handle
feeds that keep line names and destinations in unusual places. Build it with
`packbuilder build --config agencies/<id>.toml --out dist`. Choosing an agency
inside the app is not built yet; today the app uses the one pack it bundles.

## Licence

GPL-3.0. See [LICENSE](LICENSE). Schedule data belongs to the
agencies that publish it and is used under their terms.
