"""Writes the hand-made 'mini' GTFS feeds used by converter and app tests.

Run from anywhere: python make_mini.py   (output: mini/rail, mini/bus beside this file)
The values here are asserted on by tests on both sides; change them deliberately.
"""
import csv
from pathlib import Path

HERE = Path(__file__).parent


def write(folder, name, header, rows):
    folder.mkdir(parents=True, exist_ok=True)
    with open(folder / name, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(header)
        w.writerows(rows)


def hms(sec, pad=True):
    h, m, s = sec // 3600, sec % 3600 // 60, sec % 60
    return f"{h:02d}:{m:02d}:{s:02d}" if pad else f"{h}:{m:02d}:{s:02d}"


def every(start, end, step):
    return list(range(start, end + 1, step))


H = 3600
CAL = ["service_id", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "start_date", "end_date"]
ST = ["trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence", "stop_headsign"]
TRIPS = ["route_id", "service_id", "trip_id", "trip_headsign", "direction_id"]
STOPS = ["stop_id", "stop_code", "stop_name", "stop_lat", "stop_lon", "location_type", "parent_station"]
ROUTES = ["route_id", "route_short_name", "route_long_name", "route_desc", "route_type"]


def calendar(start, end):
    return [
        ["WK", 1, 1, 1, 1, 1, 0, 0, start, end],
        ["SA", 0, 0, 0, 0, 0, 1, 0, start, end],
        ["SU", 0, 0, 0, 0, 0, 0, 1, start, end],
    ]


def rail():
    d = HERE / "mini" / "rail"
    write(d, "agency.txt", ["agency_id", "agency_name", "agency_url", "agency_timezone"],
          [["MINI_Rail", "Mini Transit", "https://example.org", "America/Los_Angeles"]])
    write(d, "routes.txt", ROUTES, [["R1", "", "Metro D Line", "", 1]])
    stops = []
    for sid, name, lon in [("ALPHA", "Alpha Station", -118.30), ("BETA", "Beta Station", -118.29), ("GAMMA", "Gamma Station", -118.28)]:
        stops.append([sid, sid, name, "34.00000", f"{lon:.5f}", 0, sid + "_S"])
        stops.append([sid + "_S", sid + "_S", name, "34.00000", f"{lon:.5f}", 1, ""])
    stops.append(["ALPHA_E", "ALPHA_E", "Alpha Station - Main Entrance", "34.00010", "-118.30010", 2, "ALPHA_S"])
    write(d, "stops.txt", STOPS, stops)
    write(d, "calendar.txt", CAL, calendar(20261102, 20261115))
    write(d, "calendar_dates.txt", ["service_id", "date", "exception_type"], [])
    trips, sts = [], []

    def trip(service, direction, start, legs, headsign):
        tid = f"{service}-{direction}-{start}-{len(legs)}"
        trips.append(["R1", service, tid, "", direction])
        t = start
        for seq, (stop, delta) in enumerate(legs, 1):
            t += delta
            sts.append([tid, hms(t), hms(t), stop, seq, f"Metro D Line - {headsign}"])

    out = [("ALPHA", 0), ("BETA", 120), ("GAMMA", 180)]
    back = [("GAMMA", 0), ("BETA", 180), ("ALPHA", 120)]
    for direction, legs, head in [(0, out, "Gamma Station"), (1, back, "Alpha Station")]:
        for s in every(5 * H, 23 * H + 50 * 60, 600) + [24 * H + 600, 24 * H + 2400, 25 * H + 600]:
            trip("WK", direction, s, legs, head)
        for service in ("SA", "SU"):
            for s in every(6 * H, 23 * H + 40 * 60, 1200):
                trip(service, direction, s, legs, head)
    trip("WK", 0, 23 * H + 55 * 60, out[:2], "Beta Station")
    write(d, "trips.txt", TRIPS, trips)
    write(d, "stop_times.txt", ST, sts)


def bus():
    d = HERE / "mini" / "bus"
    write(d, "agency.txt", ["agency_id", "agency_name", "agency_url", "agency_timezone"],
          [["MINI", "Mini Transit", "https://example.org", "America/Los_Angeles"]])
    write(d, "routes.txt", ROUTES, [
        ["720-9", "720", "Mini Rapid Line", "ALPHA - FAR OUT VIA MAIN ST", 3],
        ["617-9", "617", "Mini Local Line", "FAR OUT - BETA", 3],
        ["99-9", "99", "Mini Local Line", "ALPHA LOOP", 3],
    ])
    # Leading spaces in stop_lat mirror LA Metro's bus feed.
    write(d, "stops.txt", STOPS, [
        ["101", "101", "Alpha / Main", "  34.00030", "-118.30000", "", ""],
        ["102", "102", "Alpha / Main", "  34.00035", "-118.30040", "", ""],
        ["201", "201", "Beta / Main", "  34.00030", "-118.29000", "", ""],
        ["301", "301", "Far / Out", "  34.02000", "-118.25000", "", ""],
    ])
    write(d, "calendar.txt", CAL, calendar(20261102, 20261213))
    write(d, "calendar_dates.txt", ["service_id", "date", "exception_type"],
          [["WK", 20261126, 2], ["SU", 20261126, 1]])
    trips, sts = [], []

    def trip(route, service, start, legs, headsign):
        tid = f"{route}-{service}-{start}"
        trips.append([route, service, tid, "", 0])
        t = start
        short = route.split("-")[0]
        for seq, (stop, delta) in enumerate(legs, 1):
            t += delta
            sts.append([tid, hms(t, pad=False), hms(t, pad=False), stop, seq, f"{short} - {headsign}"])

    r720 = [("101", 0), ("201", 300), ("301", 900)]
    for s in every(6 * H, 21 * H + 45 * 60, 900):
        trip("720-9", "WK", s, r720, "Far / Out")
    for s in every(7 * H, 20 * H, 1800):
        trip("720-9", "SU", s, r720, "Far / Out")
    for s in every(7 * H, 19 * H, H):
        trip("617-9", "WK", s, [("301", 0), ("201", 900)], "Beta / Main")
    for s in (6 * H + 1800, 9 * H + 1800, 15 * H + 1800, 18 * H + 1800):
        trip("99-9", "WK", s, [("102", 0), ("301", 1200), ("102", 1200)], "Alpha / Main")
    write(d, "trips.txt", TRIPS, trips)
    write(d, "stop_times.txt", ST, sts)


if __name__ == "__main__":
    rail()
    bus()
