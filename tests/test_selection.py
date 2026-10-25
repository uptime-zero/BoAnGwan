from datetime import UTC, datetime, timedelta

from boangwan.domain import chunks, eligible, select_events

AT = datetime(2026, 10, 25, 0, tzinfo=UTC)


def event(key, category="cloud", age=0, score=50, urgent=False):
    date = (AT - timedelta(days=age)).isoformat()
    return dict(
        id=key,
        category=category,
        published=date,
        discovered=date,
        importance=score,
        pending=True,
        urgent=urgent,
    )


def test_rotation_skips_empty_categories():
    category, chosen = select_events([event("a", "endpoint")], {"next_category": 0}, AT)
    assert category == "endpoint"
    assert [e["id"] for e in chosen] == ["a"]


def test_catchup_has_one_slot_beside_fresh_news():
    events = [event(f"fresh-{i}", score=10) for i in range(5)]
    events += [event(f"old-{i}", age=6, score=90) for i in range(5)]
    _, chosen = select_events(events, {"cutoffs": {"cloud": (AT - timedelta(days=5)).isoformat()}}, AT)
    assert sum(e["id"].startswith("old") for e in chosen) == 1
    assert sum(e["id"].startswith("fresh") for e in chosen) == 3


def test_all_old_edition_and_fourteen_day_limit():
    events = [event("one", age=7), event("two", age=13), event("expired", age=15)]
    _, chosen = select_events(events, {"cutoffs": {"cloud": AT.isoformat()}}, AT)
    assert {e["id"] for e in chosen} == {"one", "two"}
    assert eligible(event("boundary", age=14), AT)


def test_urgent_story_does_not_change_regular_category():
    category, chosen = select_events([event("regular"), event("urgent", "ai", urgent=True)], {}, AT)
    assert category == "cloud"
    assert [e["id"] for e in chosen] == ["urgent", "regular"]


def test_unicode_message_limit_preserves_text():
    text = "한글😀" * 2000
    result = chunks(text)
    assert "".join(result) == text
    assert all(len(part.encode("utf-16-le")) // 2 <= 1900 for part in result)
