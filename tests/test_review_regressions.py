from datetime import UTC, datetime, timedelta

import pytest
from test_ingestion import extracted

from boangwan.domain import Match, chunks, digest
from boangwan.indexes import candidate_event_ids, candidate_expiry, recent_events
from boangwan.matching import match_event
from boangwan.pipeline import RetryablePreparation, ingest, mark_facts, pending_facts, prepare
from boangwan.recovery import close_edition, resolve_part

AT = datetime(2026, 10, 25, tzinfo=UTC)


def seed_event(store, revision=1):
    event = dict(
        id="e1",
        title="사건",
        category="cloud",
        pending=True,
        importance=50,
        published=AT.isoformat(),
        discovered=AT.isoformat(),
        revision=revision,
    )
    store.put("EVENT", "e1", **event)
    store.put(
        f"CANDIDATES#{AT.date().isoformat()}",
        "e1",
        event_id="e1",
        expires=candidate_expiry(AT.isoformat()),
    )
    store.put("FACT#e1", "f1", id="f1", text="첫 번째 사실", revision=1, published=AT.isoformat())
    store.put("FACT#e1", "f2", id="f2", text="요약에 쓰이지 않은 사실", revision=1, published=AT.isoformat())
    return event


def test_ingestion_failure_leaves_no_partial_event(store, monkeypatch):
    monkeypatch.setattr("boangwan.pipeline.fetch", lambda *a: "html")
    monkeypatch.setattr(
        "boangwan.pipeline.extract_article",
        lambda *a: dict(body=extracted().facts[0].quote, published=AT.isoformat(), url=a[1]),
    )

    class AI:
        def ask(self, schema, *args):
            return (
                Match(event_id=None, novel_indices=[0], material_update=True)
                if schema is Match
                else extracted()
            )

    actual = store.transact
    monkeypatch.setattr(
        store, "transact", lambda *a, **k: (_ for _ in ()).throw(RuntimeError("write failed"))
    )
    with pytest.raises(RuntimeError):
        ingest(store, AI(), {"url": "https://github.blog/test", "source": "github"}, AT)
    key = digest(extracted().event_key)[:32]
    assert store.query(f"FACT#{key}") == []
    assert store.get("EVENT", key) is None
    monkeypatch.setattr(store, "transact", actual)
    ingest(store, AI(), {"url": "https://github.blog/test", "source": "github"}, AT)
    assert len(store.query(f"FACT#{key}")) == 1
    assert store.get("EVENT", key)["revision"] == 1


def test_transaction_guard_rolls_back_every_write(store):
    store.put("CONTENT#existing", marker="original")
    assert not store.transact(
        [
            dict(pk="EVENT", sk="e1", title="must not persist"),
            dict(pk="CONTENT#existing", sk="META", marker="replacement"),
        ],
        guard=("CONTENT#existing", "META"),
    )
    assert store.get("EVENT", "e1") is None
    assert store.get("CONTENT#existing")["marker"] == "original"


def test_delivery_closes_unused_facts_but_preserves_new_revision(store):
    event = seed_event(store, revision=2)
    store.put(
        "FACT#e1", "f3", id="f3", text="나중에 공개된 중요 업데이트", revision=2, published=AT.isoformat()
    )
    mark_facts(store, "2026-10-25", dict(event_id="e1", fact_ids=["f1"], revision=1))
    assert {f["id"] for f in pending_facts(store, event)} == {"f3"}
    assert store.get("SENT#f2") is None  # Omitted is not falsely labelled as actually sent.
    mark_facts(store, "2026-10-25", dict(event_id="e1", fact_ids=["f3"], revision=2))
    mark_facts(store, "2026-10-25", dict(event_id="e1", fact_ids=["f1"], revision=1))
    assert store.get("HANDLED#e1")["revision"] == 2
    assert pending_facts(store, event) == []


def test_temporary_error_before_nine_can_recover(store, monkeypatch):
    seed_event(store)
    at = AT - timedelta(minutes=10)
    monkeypatch.setattr(
        "boangwan.pipeline.render_event",
        lambda *a: (_ for _ in ()).throw(RuntimeError("AI temporarily unavailable")),
    )
    with pytest.raises(RetryablePreparation):
        prepare(store, None, at)
    assert store.get("DRAFT", "2026-10-25") is None
    monkeypatch.setattr(
        "boangwan.pipeline.render_event",
        lambda *a: dict(
            event_id="e1",
            fact_ids=["f1"],
            title="확인된 소식",
            text="본문",
            category="cloud",
            discovered=AT.isoformat(),
            revision=1,
        ),
    )
    prepare(store, None, at + timedelta(minutes=5))
    assert store.get("DRAFT", "2026-10-25")["items"][0]["event_id"] == "e1"


def edition(store):
    store.put(
        "DRAFT",
        "2026-10-25",
        status="ready",
        category="cloud",
        cutoff=AT.isoformat(),
        usage_cutoff=AT.isoformat(),
        next_category=1,
        items=[dict(event_id="e1", revision=1)],
        parts=[
            dict(content="overview", event_id=None, fact_ids=[], mentioned_events=["e1"]),
            dict(content="story", event_id="e1", fact_ids=["f1"], revision=1, mentioned_events=["e1"]),
            dict(content="usage", event_id=None, fact_ids=[], mentioned_events=[]),
        ],
    )


def test_past_edition_complete_updates_facts_without_rewinding_state(store):
    edition(store)
    seed_event(store)
    later = (AT + timedelta(days=1)).isoformat()
    store.put("STATE", next_category=3, cutoffs={"endpoint": later}, usage_cutoff=later)
    for part in range(3):
        store.put("SEND#2026-10-25", f"{part:03}", status="unknown")
        resolve_part(store, "2026-10-25", part, "sent", str(part))
    assert close_edition(store, "2026-10-25", "complete", AT + timedelta(days=2)) == "sent"
    assert store.get("HANDLED#e1")["revision"] == 1
    assert store.get("STATE")["next_category"] == 3
    assert store.get("STATE")["usage_cutoff"] == later


def test_cancel_never_delivered_edition_releases_candidate(store):
    edition(store)
    event = seed_event(store)
    assert close_edition(store, "2026-10-25", "cancel", AT + timedelta(days=1)) == "cancelled"
    assert len(pending_facts(store, event)) == 2


def test_cancel_ambiguous_edition_is_rejected(store):
    edition(store)
    store.put("SEND#2026-10-25", "000", status="unknown")
    with pytest.raises(ValueError, match="ambiguous"):
        close_edition(store, "2026-10-25", "cancel", AT + timedelta(days=1))
    assert store.get("DRAFT", "2026-10-25")["status"] == "ready"


def test_cancel_partially_delivered_edition_closes_only_disclosed_revision(store):
    edition(store)
    seed_event(store)
    store.put("SEND#2026-10-25", "000", status="sent", message_id="1")
    close_edition(store, "2026-10-25", "cancel", AT + timedelta(days=1))
    assert store.get("HANDLED#e1")["revision"] == 1
    assert store.get("SENT#f1") is None


def test_source_link_survives_message_boundary():
    url = "<https://example.com/" + "long-path-" * 40 + ">"
    text = "가" * 1850 + "\n\n[1] 원문 " + url
    result = chunks(text)
    assert "".join(result) == text
    assert any(url in part for part in result)
    assert all(len(part.encode("utf-16-le")) // 2 <= 1900 for part in result)
    with pytest.raises(ValueError, match="URL"):
        chunks("<https://example.com/" + "x" * 2000 + ">")


def test_history_batches_do_not_drop_old_facts(store):
    item = extracted()
    key = digest(item.event_key)[:32]
    store.put("EVENT", key, id=key, title="과거 사건", published="2025-01-01T00:00:00+00:00")
    for number in range(40):
        store.put(f"FACT#{key}", f"{number:03}", id=f"{number:03}", text=str(number) + "가" * 690)
    seen = []

    class AI:
        def ask(self, schema, task, data, stage):
            assert len(str(data["history"])) < 30_000
            seen.extend(f["text"] for row in data["history"] for f in row["facts"])
            return Match(event_id=key, novel_indices=[] if len(seen) >= 40 else [0], material_update=False)

    result, _ = match_event(store, AI(), item, AT)
    assert len(seen) == 40
    assert result.event_id == key
    assert result.novel_indices == []


def test_recent_candidates_are_deduped_before_limit(store):
    # One event can occur in multiple weekly buckets; dedupe it before the cap.
    for partition, last_seen in [
        ("RECENT_EVENTS#2026-W43", "2026-10-25T00:44:30+00:00"),
        ("RECENT_EVENTS#2026-W42", "2026-10-18T00:00:00+00:00"),
        ("RECENT_EVENTS#2026-W41", "2026-10-11T00:00:00+00:00"),
        ("RECENT_EVENTS#2026-W40", "2026-10-04T00:00:00+00:00"),
        ("RECENT_EVENTS#2026-W39", "2026-09-27T00:00:00+00:00"),
    ]:
        store.put(partition, "repeat", event_id="repeat", last_seen=last_seen)
    partition = "RECENT_EVENTS#2026-W43"
    for number in range(45):
        store.put(
            partition,
            f"unique-{number:02}",
            event_id=f"unique-{number:02}",
            last_seen=f"2026-10-25T00:{number + 1:02}:00+00:00",
        )
    found = recent_events(store, AT, limit=40)
    assert len(found) == 40
    assert sum(row["event_id"] == "repeat" for row in found) == 1
    assert found[0]["event_id"] == "unique-44"
    assert "unique-00" not in {row["event_id"] for row in found}


def test_daily_candidate_lookup_reads_date_buckets_not_archive(store):
    store.put("EVENT", "expired", id="expired")
    store.put("EVENT", "eligible", id="eligible")
    store.put(f"CANDIDATES#{AT.date().isoformat()}", "eligible", event_id="eligible")
    assert candidate_event_ids(store, AT) == {"eligible"}
