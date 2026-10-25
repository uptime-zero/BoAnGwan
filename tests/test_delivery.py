from datetime import UTC, datetime, timedelta

import httpx

from boangwan.pipeline import publish

AT = datetime(2026, 10, 25, 0, tzinfo=UTC)
WEBHOOK = "https://discord.com/api/webhooks/test/token"


def seed(store):
    store.put(
        "DRAFT",
        "2026-10-25",
        status="ready",
        category="cloud",
        cutoff=AT.isoformat(),
        next_category=1,
        items=[],
        usage_cutoff=AT.isoformat(),
        parts=[
            {"content": "first", "fact_ids": [], "event_id": None},
            {"content": "second", "fact_ids": ["fact-1"], "event_id": "event-1"},
        ],
    )


def test_repeated_invocation_does_not_resend(store, monkeypatch):
    seed(store)
    calls = []

    def post(*args, **kwargs):
        calls.append(kwargs["json"])
        return httpx.Response(200, json={"id": str(len(calls))})

    monkeypatch.setattr(httpx, "post", post)
    publish(store, WEBHOOK, AT)
    publish(store, WEBHOOK, AT + timedelta(minutes=10))
    assert len(calls) == 2
    assert calls[1]["flags"] == 4096
    assert calls[0]["allowed_mentions"] == {"parse": []}
    assert store.get("SENT#fact-1")
    assert store.get("STATE")["next_category"] == 1


def test_unknown_response_is_never_automatically_retried(store, monkeypatch):
    seed(store)
    calls = []

    def post(*args, **kwargs):
        calls.append(1)
        raise httpx.ReadTimeout("lost response")

    monkeypatch.setattr(httpx, "post", post)
    publish(store, WEBHOOK, AT)
    publish(store, WEBHOOK, AT + timedelta(minutes=10))
    assert len(calls) == 1
    assert store.get("SEND#2026-10-25", "000")["status"] == "unknown"
    assert store.get("DRAFT", "2026-10-25")["status"] == "ready"


def test_partial_send_resumes_after_rate_limit(store, monkeypatch):
    seed(store)
    calls = []

    def post(*args, **kwargs):
        content = kwargs["json"]["content"]
        calls.append(content)
        if len(calls) == 2:
            return httpx.Response(429, json={"retry_after": 1200})
        return httpx.Response(200, json={"id": str(len(calls))})

    monkeypatch.setattr(httpx, "post", post)
    publish(store, WEBHOOK, AT)
    publish(store, WEBHOOK, AT + timedelta(minutes=10))
    assert calls == ["first", "second"]
    publish(store, WEBHOOK, AT + timedelta(minutes=20))
    assert calls == ["first", "second", "second"]
    assert store.get("DRAFT", "2026-10-25")["status"] == "sent"


def test_crash_after_ack_recovers_fact_ledger(store, monkeypatch):
    seed(store)
    store.put("SEND#2026-10-25", "000", status="sent", message_id="1")
    store.put("SEND#2026-10-25", "001", status="sent", message_id="2")
    monkeypatch.setattr(httpx, "post", lambda *a, **k: (_ for _ in ()).throw(AssertionError("no send")))
    publish(store, WEBHOOK, AT)
    assert store.get("SENT#fact-1")


def test_send_claim_is_atomic(store):
    assert store.claim_send("2026-10-25", 0)
    assert not store.claim_send("2026-10-25", 0)


def test_no_early_or_previous_day_delivery(store, monkeypatch):
    seed(store)
    monkeypatch.setattr(httpx, "post", lambda *a, **k: (_ for _ in ()).throw(AssertionError("no send")))
    publish(store, WEBHOOK, AT - timedelta(minutes=1))
    publish(store, WEBHOOK, AT + timedelta(days=1))
