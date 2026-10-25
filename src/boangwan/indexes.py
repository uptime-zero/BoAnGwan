"""Time-bucket indexes for active candidates and recent deduplication context."""

from datetime import datetime, timedelta

from .domain import parse_time


def candidate_event_ids(store, at: datetime) -> set[str]:
    start, end = (at - timedelta(days=14)).date(), (at + timedelta(days=1)).date()
    ids = set()
    day = start
    while day <= end:
        ids.update(row["event_id"] for row in store.query(f"CANDIDATES#{day.isoformat()}"))
        day += timedelta(days=1)
    return ids


def recent_events(store, at: datetime, limit: int = 40, weeks: int = 5) -> list[dict]:
    monday = at.date() - timedelta(days=at.weekday())
    latest = {}
    for offset in range(weeks):
        week = monday - timedelta(days=7 * offset)
        partition = f"RECENT_EVENTS#{week.isocalendar().year}-W{week.isocalendar().week:02}"
        for row in store.query(partition):
            event_id = row["event_id"]
            seen_at = row.get("last_seen") or row["sk"].split("#", 1)[0]
            if event_id not in latest or seen_at > latest[event_id]["last_seen"]:
                latest[event_id] = {"event_id": event_id, "last_seen": seen_at}
    return sorted(latest.values(), key=lambda row: row["last_seen"], reverse=True)[:limit]


def candidate_expiry(published: str) -> int:
    return int((parse_time(published) + timedelta(days=16)).timestamp())
