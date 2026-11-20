# Minimal transit schedules for the Mudita Kompakt — design

Date: 2026-11-19
Status: approved and built. Revised 2026-11-20 after the first pass on the
device; the screens below describe the app as it now is.
Name: Paper Timetable (first built under the working name `kompakt-transit`)

## Intent

A rider who uses a Mudita Kompakt (E Ink, Android-based, no Google services)
wants transit schedules on the phone without a smartphone-style transit app.
It serves two moments:

1. **Before leaving home:** "when is my next one?"
2. **Riding toward a stop:** "do I get off here or stay on?" — which is really
   "what leaves from the upcoming stop, and when?"

The owner rides LA Metro's D Line and buses, and thinks of transit as
**line, then direction**. The app starts with LA and is meant to grow into
something any Kompakt owner can use with their own city's agency.

## Scope and order

This is three projects, built in this order. **This document designs the first
in detail.** The other two get their own design when their turn comes; they
are described here only so the first does not block them.

1. **Offline schedules for LA Metro** (bus and rail). This document.
2. **On-demand lateness check.** Tap "check" on one stop/route, make one
   request, show "on time" / "6 min late" / "no live info". Never a live,
   self-updating view.
3. **Other agencies.** A setup step that downloads a data pack per agency.

Later, unscheduled: search by place or address. Finding a route from A to B is
not planned; the owner wants riders to understand the system well enough to
choose for themselves (see "Understanding a line" below).

## Goals (project 1)

- Show the next departures for saved routes with zero taps after opening.
- Let the rider find any LA Metro stop by line → direction → stop, by
  "near me", or by stop name.
- Show everything that leaves from a stop, for transfer decisions.
- Work with no connection. A connection is needed only to update schedules.
- Help a rider plan ahead without a route-finder: show how often each line
  runs, and show schedules for another day or time.
- Read comfortably on a 4.3" 800×480 E Ink screen.
- Be built so adding agencies later is a setup screen, not a rewrite.

## Non-goals (project 1)

Live data, trip planning (A to B), maps, place/address search, fares, service
alerts, accounts, choosing an agency in the app.

## Anti-goals

These would make it a failure even if it works:

- Anything that animates, scrolls smoothly, counts down, or redraws on its own
  more than once a minute.
- Needing a connection to read a schedule.
- Needing Google Play services.
- Accounts, analytics, or any tracking.
- Showing a stale schedule without saying it is stale.

## Constraints

- **Device:** Mudita Kompakt. 4.3" E Ink, 800×480, MuditaOS K (Android 12),
  quad-core MediaTek MT6761, 3 GB RAM, 32 GB storage. No Google services.
- **Screen measure:** the Kompakt reports a density of 213, so the screen is
  about 360 × 601 dp, not the 320 × 533 dp first assumed. Layouts are sized
  from the space they are given, never from a fixed row count.
- **Install:** sideloaded APK (ADB today). Builds meant for other people are
  signed with the project's release key (see "Releases").
- **Data source:** LA Metro publishes bus and rail schedules as two separate
  GTFS zip files (gitlab.com/LACMTA/gtfs_bus and gtfs_rail). Roughly 110+ bus
  lines; rail is refreshed often, bus at service changes a few times a year.
- **Location:** Android's built-in location service only (no Google fused
  location).
- **Rail is only published two weeks ahead** (found when planning, 2026-11-19).
  Metro's rail file covers about a fortnight and is refreshed nightly; the bus
  file covers the whole service period. So the pack's "valid through" date is
  the rail one, the weekly rebuild matters, and a rail time for a date further
  out is the same weekday's schedule carried forward, shown with a plain
  "Beyond published schedule — times may change" line.

## Approaches

Where the schedule data gets prepared:

- **A. Prebuilt data packs (chosen).** A converter runs on a computer and turns
  an agency's GTFS files into one compact database file. The app ships with
  the LA Metro pack and can download a newer one. Phone-side lookups are
  instant and offline. Cost: the converter has to be re-run when schedules
  change, so the app needs an update action and a visible validity date.
- **B. Import raw GTFS on the phone.** No hosting, any agency from day one.
  Rejected: LA's bus schedule is millions of rows; importing on this hardware
  would be slow and fragile, and every agency's quirks would be handled on the
  device.
- **C. Web page saved to the home screen.** Rejected: unreliable offline
  storage and location, and it would not feel like a Kompakt app.

Chosen: **A**, picked by the owner. It puts heavy work where there is power to
spare and keeps the phone side simple.

Languages, agreed with the owner: **Kotlin** for the app (native Android),
**Python** for the converter. Rust was considered and declined: the screen, not
the CPU, is the slow part, and Android's interface toolkit is Kotlin.

## The app

### Screens

**My Routes (home).** The saved routes, each a line + direction + boarding
stop, with the next three departures as clock times.

```
 MY ROUTES              │ Edit │ About
 ─────────────────────────────────────
 D  → Union Station
    Wilshire/Western    4:12 4:24 4:36
 ─────────────────────────────────────
 720 → Santa Monica
    Wilshire/Western    4:09 4:17 4:25
 ─────────────────────────────────────
 Lines      │   Near me   │    Search
```

- Tapping a route opens its stop view.
- Edit switches the cards to remove and reorder; it is offered only when
  there is a route to edit.
- There is no "Add" button. A route is added by finding its stop and tapping
  the star; the empty state says so.

**Finding your way around.** Added after the device pass: with only the
phone's own back gesture, it was easy to lose track of where a screen sat.

- Every screen but home has a row at the top that goes back and names where
  back leads: "‹ Home", "‹ Lines", "‹ D Line", "‹ D → Union Station",
  "‹ Wilshire / Vermont". A breadcrumb trail was tried first and was too
  small to read.
- The phone's back gesture does the same thing.

**Lines → Direction → Stops.** The way to add a route, matching the owner's
mental model. The Lines list is split into **Rail** and **Bus**, chosen with
two buttons under a field for typing a line; typing searches both at once.
The list comes back on the half that was open. Picking a line opens its overview, which
offers its two directions by destination. Picking a direction shows that
direction's stops in travel order. Picking a stop opens the stop view.

**Understanding a line.** The owner's point: knowing "the D Line comes every
few minutes" versus "the 617 comes once an hour" lets someone with a limited
phone use the system intentionally — which lines you can just walk up to, and
which you must time.

- The Lines list carries a short frequency tag per line, for the current kind
  of day (weekday, Saturday or Sunday).
- A few event shuttles have names far too long for a row ("LAX / Metro
  Transit Center - SoFi Stadium Express"). Those take the full width with the
  frequency beneath, and are cut from the middle so both ends can be read.
- The line overview gives, for each kind of day, the first and last trip and
  how often it runs in each part of the day.

```
 ‹ Home
 ─────────────────────────────────────
 LINES                    Weekday
 [ Type a line: 720, D …             ]
 ─────────────────────────────────────
 █████ Rail █████  │       Bus
 ─────────────────────────────────────
 B     every 10–15 min
 D     every 5–10 min
 ...
 617   about hourly
```

```
 D LINE
 ─────────────────────────────────────
 WEEKDAYS          4:30a – 12:40a
   Morning rush    every 5 min
   Midday          every 10 min
   Evening rush    every 5 min
   Night           every 20 min
 SATURDAY  ·  SUNDAY
 ─────────────────────────────────────
 → Union Station    → Wilshire/Western
```

(Figures in these sketches are illustrative, not Metro's.)

- *Author's calls:* frequencies are worked out by the converter from the real
  schedule and stored in the pack, measured at each direction's busiest stop.
  Parts of the day are fixed bands (early morning, morning rush, midday,
  evening rush, evening, night). Wording is rounded and plain: "every 5 min",
  "every 10–15 min", "about hourly", "6 trips a day"; a band with no service
  says so. If the two directions differ noticeably, each is shown.

**Another day or time.** Stop view and the full-day departures screen have a
"When" control: now (the default), or a chosen day and time. Days are offered
as Today, Tomorrow, the next weekday/Saturday/Sunday, or a specific date, so
holiday schedules come out right. Chosen with large buttons, not spinning
wheels. A chosen time is clearly labelled on screen and resets to "now" when
the app is reopened. My Routes always shows now. The control is a full-width
row under the screen title that reads "When: Now" or the chosen day and time.

**Stop view.** Every line and direction serving the stop, with the next
departures for each. Each row can be saved to My Routes with its star.

```
 WILSHIRE / VERMONT
 ─────────────────────────────────────
 B  → North Hollywood    4:15 4:27   ☆
 D  → Union Station      4:14 4:26   ★
 720 → Downtown LA       4:11 4:19   ☆
 720 → Santa Monica      4:13 4:21   ☆
 754 → Hollywood         4:13 4:28   ☆
```

- Tapping a row opens the full day's departures for that line, direction and
  stop, as a paged grid of times opening on the page that holds the next one.
  That screen has the star too, in its title bar, so a route can be saved
  from wherever the rider ends up.
- *Author's call — places, not poles:* schedule data lists each platform and
  each street corner as its own stop. The app works in **places**: a station's
  platforms plus every stop within about 100 m of it or of each other (so all
  corners of one intersection, and the rail station under it, are one place).
  The stop view lists every line and direction at the place in one list; there
  is no separate "nearby" section. Near me and Search list places, one entry
  each. The converter computes places and stores them in the pack; the
  distance is a per-agency config value, tuned against real LA intersections.
- A saved route still records the exact stop it boards at, so its times are
  for that pole or platform.

**Near me.** On tap, gets one location fix and lists the closest places with
distance and, under each name, the lines that can be boarded there — what
serves a stop matters more than what it is called. Location permission is requested the first time this is tapped, not
at install. Shows a plain "Finding location…" while waiting, uses the last
known location if a fix takes too long, and says so.

**Search.** Stop-name search with the on-screen keyboard. The keyboard is
left in whatever case the rider sets (matching ignores case), and each result
lists its lines as Near me does.

**About / schedule.** Agency name, the dates the schedule is valid for, when it
was built, and an "Update schedule" action.

### Time and service-day behaviour

- Times are clock times in the agency's time zone, never countdowns.
- "Next" means from now. When today's service is finished, show the first
  departures of the next service day, labelled with the day.
- Service after midnight belongs to the previous service day (schedule data
  writes these as 25:10); the app shows them correctly as 1:10.
- Weekday, Saturday, Sunday and holiday schedules, and one-off exception
  dates, come from the schedule data.
- *Author's call:* a departure that ends short of the direction's usual
  terminus, or serves a branch, is marked with its own destination.

### E Ink rules

- Pure black on white. No greys, gradients, shadows, images or thin fonts.
  Heavy weights, large sizes.
- No animation of any kind: no transitions, tap ripples, overscroll glow or
  smooth scrolling. Lists move a page at a time with Prev/Next.
- The screen redraws only on a tap, on returning to the app, and when a shown
  departure passes (at most once a minute, and only while visible).
- Large tap targets with clear separation; touch feedback lags on this screen.
- Buttons are not boxed. Set side by side or stacked, boxes double every line
  and looked untidy on the device. A bar of buttons is ruled off with one
  horizontal line and divided by vertical lines that run the bar's full
  height; the chosen one of a pair is shown white on black. Only a button
  standing alone has an outline.
- A full page of a list ends flush against Prev/Next. Rows stretch to fill
  the height, so a gap at the bottom only ever means "this is the last page".
- The text cursor does not blink (each blink would be a refresh); a still bar
  is drawn against the last letter typed.
- A change of system settings (text size, language) redraws in place and
  keeps the rider on the page they were reading.
- Follows Mudita's own app conventions where they can be observed on the
  device.

### Schedule freshness

- The pack records the dates it is valid for. The About screen always shows
  them.
- When today is past the valid range, every screen with times carries a plain
  "Schedule may be out of date" line.
- "Update schedule" fetches a small index file, compares it to the installed
  pack, and downloads a newer pack if there is one. It never runs on its own.
- A download is checked against the index: it is stopped if it runs longer
  than the index says or unpacks far beyond the size of any real pack, and is
  only installed once its checksum matches and it opens. Whatever goes wrong,
  the installed schedule is left as it was.
- *Author's call:* packs and the index are plain static files at a base
  address set in one place in the app, so hosting can move.
- **In project 1:** the app-side fetch, compare and download, and the
  converter writing the pack plus its index entry. Both are tested end to end
  against a local address.
- **Also in project 1:** a scheduled job that rebuilds the pack weekly and
  publishes it. Packs and the index are published as the release files of
  the public repository, `github.com/karismatic-megafauna/paper-timetable`,
  under the release named `packs`.

### Releases

Builds handed to other people are signed with one release key, created
2026-11-20. Android accepts an update only from the key that signed the
installed app, so the key is kept for the life of the project: it lives on
the owner's machine outside the repository, its password in the owner's
Gradle settings, and its certificate fingerprint is printed in the README so
anyone can check a build. Someone building from source gets an unsigned
release build and signs it themselves.

## The data pack

One SQLite file per agency, built by the converter. LA Metro's bus and rail
feeds are merged into one pack, since the rider sees one system.

It holds: agency name and time zone; validity dates and build date; a format
version; lines (short name, long name, rail or bus, display order); each
line's directions and destinations; stops (name, position, station grouping);
each direction's ordered stop list; departures; the service calendar with
exceptions; and each line's frequency summary per kind of day. It also has a reserved place for the agency's live-data address,
empty for now, for project 2.

- The app refuses a pack whose format version it does not understand, and
  keeps the one it has.
- *Author's call:* departures are stored compactly (shared stop patterns plus
  start times, not one row per stop per trip). Target: the LA Metro pack is
  small enough to bundle in the APK and download over mobile data — aim for
  under about 30 MB; the builder measures and reports.

## The converter

A Python command-line tool: given one or more GTFS zip files or addresses and
an agency name, it writes a pack and the index entry for it. It fails loudly
on schedule data it cannot interpret instead of writing a partial pack. Agency
specifics for LA Metro live in a small config, not in the code, so the next
agency is a new config.

## Preparing for projects 2 and 3

- Everything the app knows about the agency comes from the pack; nothing about
  LA is written into the app.
- The app's storage can hold more than one pack, though project 1 uses one.
- Saved routes record which agency they belong to.
- For project 2: the owner has an earlier experiment (`~/Code/lametro-eink`)
  that reads LA Metro rail predictions from the Swiftly service with a
  personal key. An app others install cannot ship a personal key; how live
  data is reached is a question for that project's design.

## Left to the builder

- Interface toolkit (Compose or classic views), provided the E Ink rules hold.
- Lowest supported Android version; lower than 12 is welcome so other Android
  E Ink devices can run it, but the Kompakt is the only device that must work.
- Exact pack schema and compaction.
- Project layout, build setup, test tooling. Expected shape: `app/`,
  `converter/`, `docs/`.
- Page sizes, type sizes and spacing, tuned on the real device.

## Testing

- Converter: automated tests against a small hand-made schedule covering
  after-midnight trips, holiday exceptions, short-turn trips and merged
  stations and intersections (places); plus a run against the real LA Metro files with spot checks
  against Metro's published timetables for the D Line and line 720.
- App: automated tests for "next departures" across the service-day boundary,
  the end of service, a chosen future day including a holiday, and an expired
  pack. Converter tests cover the frequency summary, including an hourly line
  and a line with a handful of trips a day.
- On the device: the owner has the only Kompakt, connected by USB to the
  machine the build runs on. Builds are installed over ADB and layout is
  checked from device screenshots; legibility, ghosting, tap accuracy and
  location-fix time need the owner's eyes, so the plan has owner checkpoints
  on the device early (first screen) and not only at the end. An 800×480
  emulator set to the Kompakt's density of 213 is used in between.

## Open, for the owner

- **Name: settled.** Paper Timetable ("Timetable" under the icon), repository
  `paper-timetable`, app identifier
  `io.github.karismatic_megafauna.papertimetable`.
- **Licence: settled.** GPL-3.0.
- **Becoming "official".** How apps get into Mudita's InkApps collection has
  not been looked into.
