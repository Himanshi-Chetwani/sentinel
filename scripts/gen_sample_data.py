import json
from datetime import datetime, timedelta, timezone
from pathlib import Path


START = datetime(2026, 11, 23, 10, 0, tzinfo=timezone.utc)
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "sample-data"


def timestamp_for(index, interval_seconds):
    timestamp = START + timedelta(seconds=index * interval_seconds)
    return timestamp.isoformat().replace("+00:00", "Z")


def brute_force(failures):
    events = [
        {
            "type": "LOGIN",
            "user": "alice",
            "sourceIp": "203.0.113.7",
            "success": False,
            "timestamp": timestamp_for(index, 10),
        }
        for index in range(failures)
    ]
    events.append(
        {
            "type": "LOGIN",
            "user": "alice",
            "sourceIp": "203.0.113.7",
            "success": True,
            "timestamp": timestamp_for(failures, 10),
        }
    )
    return events


def port_scan(ports):
    return [
        {
            "type": "FIREWALL",
            "sourceIp": "198.51.100.9",
            "destinationIp": "10.0.0.5",
            "destinationPort": port,
            "action": "DENY",
            "timestamp": timestamp_for(port - 1, 1),
        }
        for port in range(1, ports + 1)
    ]


def write_batch(filename, events):
    with (OUTPUT_DIR / filename).open("w", encoding="utf-8") as output:
        json.dump({"events": events}, output, indent=2)
        output.write("\n")


def main():
    OUTPUT_DIR.mkdir(exist_ok=True)
    write_batch("brute_force.json", brute_force(12))
    write_batch("brute_force_near_miss.json", brute_force(9))
    write_batch("port_scan.json", port_scan(60))
    write_batch("port_scan_near_miss.json", port_scan(49))


if __name__ == "__main__":
    main()
