from datetime import UTC, datetime

import pytest

from boangwan.domain import Brief, Claim, ExtractedFact, Extraction, Verdict, grounded
from boangwan.indexes import candidate_expiry
from boangwan.pipeline import prepare, render_event
from boangwan.sources import RejectedArticle, extract_article

AT = datetime(2026, 10, 25, tzinfo=UTC)


def test_invented_quote_rejected():
    extraction = Extraction(
        relevant=True,
        title="사건",
        category="cloud",
        event_key="incident",
        identifiers=[],
        impact=20,
        threat=10,
        novelty=10,
        urgent=False,
        urgency_quote="",
        facts=[
            ExtractedFact(
                text="테스트용으로 작성된 사실 설명입니다.",
                quote="This exact sentence must exist in the source.",
            )
        ],
    )
    assert not grounded(extraction, "A different article entirely.")
    assert grounded(extraction, "This exact sentence must exist\nin the source.")


def test_short_excerpt_and_missing_date_rejected():
    with pytest.raises(RejectedArticle):
        extract_article("<html><article>Only a teaser</article></html>", "https://example.com", "", AT)


def test_verifier_rejects_unsupported_claims(store, monkeypatch):
    fact = dict(
        id="f1",
        text="패치가 공개되었습니다.",
        quote="A security patch was released today.",
        body_key="bodies/test",
        url="https://github.blog/test",
        source="github",
        published=AT.isoformat(),
        discovered=AT.isoformat(),
    )
    store.put("FACT#e1", "f1", **fact)
    store.body_put("bodies/test", fact["quote"])
    monkeypatch.setattr("boangwan.pipeline.fetch", lambda *a: "html")
    monkeypatch.setattr("boangwan.pipeline.extract_article", lambda *a: {"body": fact["quote"]})

    class AI:
        def ask(self, schema, *args):
            if schema is Brief:
                return Brief(
                    claims=[
                        Claim(text="패치가 공개되었습니다.", fact_ids=["f1"]),
                        Claim(text="이미 전 세계가 피해를 봤습니다.", fact_ids=["f1"]),
                    ]
                )
            return Verdict(supported_indices=[0], current=True)

    result = render_event(
        store, AI(), dict(id="e1", category="cloud", published=AT.isoformat(), discovered=AT.isoformat()), AT
    )
    assert "패치가 공개" in result["text"]
    assert "전 세계" not in result["text"]
    assert "https://github.blog/test" in result["text"]


def test_all_failed_candidates_produce_status_not_crash(store, monkeypatch):
    store.put("FACT#e1", "f1", id="f1", published=AT.isoformat())
    store.put(
        "EVENT",
        "e1",
        id="e1",
        category="cloud",
        pending=True,
        importance=50,
        published=AT.isoformat(),
        discovered=AT.isoformat(),
    )
    store.put(
        f"CANDIDATES#{AT.date().isoformat()}",
        "e1",
        event_id="e1",
        expires=candidate_expiry(AT.isoformat()),
    )
    monkeypatch.setattr("boangwan.pipeline.render_event", lambda *args: None)
    prepare(store, None, AT)
    draft = store.get("DRAFT", "2026-10-25")
    assert draft["category"] is None
    assert "검증 실패" in draft["parts"][0]["content"]


def test_failed_category_falls_forward(store, monkeypatch):
    for key, category in [("e1", "cloud"), ("e2", "application")]:
        store.put(f"FACT#{key}", f"f{key}", id=f"f{key}", published=AT.isoformat())
        store.put(
            "EVENT",
            key,
            id=key,
            category=category,
            pending=True,
            importance=50,
            published=AT.isoformat(),
            discovered=AT.isoformat(),
        )
        store.put(
            f"CANDIDATES#{AT.date().isoformat()}",
            key,
            event_id=key,
            expires=candidate_expiry(AT.isoformat()),
        )

    def render(store, ai, event, at):
        if event["id"] == "e1":
            return None
        return dict(
            event_id="e2",
            fact_ids=["f2"],
            title="검증된 사건입니다.",
            text="본문",
            category="application",
            discovered=at.isoformat(),
        )

    monkeypatch.setattr("boangwan.pipeline.render_event", render)
    prepare(store, None, AT)
    assert store.get("DRAFT", "2026-10-25")["category"] == "application"


def test_already_sent_facts_do_not_trigger_verification_or_failure_notice(store, monkeypatch):
    store.put(
        "EVENT",
        "e1",
        id="e1",
        category="cloud",
        pending=True,
        importance=50,
        published=AT.isoformat(),
        discovered=AT.isoformat(),
    )
    store.put(
        f"CANDIDATES#{AT.date().isoformat()}",
        "e1",
        event_id="e1",
        expires=candidate_expiry(AT.isoformat()),
    )
    store.put("FACT#e1", "f1", id="f1", published=AT.isoformat())
    store.put("SENT#f1", day="2026-10-24")
    store.put("HEALTH", "COLLECT", at=AT.isoformat(), sources=[{"status": "ok"}])
    monkeypatch.setattr("boangwan.pipeline.render_event", lambda *a: (_ for _ in ()).throw(AssertionError()))
    prepare(store, None, AT)
    assert "선정할 사건이 없습니다" in store.get("DRAFT", "2026-10-25")["parts"][0]["content"]
