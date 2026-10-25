from datetime import UTC, datetime, timedelta

from boangwan.domain import ExtractedFact, Extraction, Match
from boangwan.pipeline import ingest

AT = datetime(2026, 10, 25, tzinfo=UTC)


def extracted():
    return Extraction(
        relevant=True,
        title="보안 패치 공개",
        category="application",
        event_key="vendor-product-cve-2026-12345",
        identifiers=["CVE-2026-12345"],
        impact=30,
        threat=20,
        novelty=10,
        urgent=False,
        urgency_quote="",
        facts=[
            ExtractedFact(
                text="제품의 보안 패치가 공개되었습니다.",
                quote="A security patch was released for the affected product.",
            )
        ],
    )


def test_identical_body_at_other_url_skips_ai(store, monkeypatch):
    calls = []
    body = extracted().facts[0].quote
    monkeypatch.setattr("boangwan.pipeline.fetch", lambda *a: "html")
    monkeypatch.setattr(
        "boangwan.pipeline.extract_article",
        lambda html, url, published, at: dict(body=body, title="Patch", url=url, published=AT.isoformat()),
    )

    class AI:
        def ask(self, schema, task, data, stage):
            calls.append(stage)
            return (
                extracted()
                if schema is Extraction
                else Match(event_id=None, novel_indices=[0], material_update=True)
            )

    ingest(store, AI(), {"url": "https://github.blog/article-one", "source": "github"}, AT)
    ingest(store, AI(), {"url": "https://github.blog/reprint", "source": "github"}, AT)
    assert calls == ["extract", "deduplicate"]
    assert len(store.query("EVENT")) == 1


def test_semantic_reprint_does_not_add_new_facts(store, monkeypatch):
    body = extracted().facts[0].quote
    monkeypatch.setattr("boangwan.pipeline.fetch", lambda url, hosts: url)
    monkeypatch.setattr(
        "boangwan.pipeline.extract_article",
        lambda html, url, published, at: dict(
            body=body + url, title="Patch", url=url, published=AT.isoformat()
        ),
    )

    class AI:
        def ask(self, schema, task, data, stage):
            if schema is Extraction:
                return extracted()
            if data["history"]:
                assert data["history"][0]["facts"][0]["sent"] is True
                return Match(event_id=data["history"][0]["id"], novel_indices=[], material_update=False)
            return Match(event_id=None, novel_indices=[0], material_update=True)

    ingest(store, AI(), {"url": "https://github.blog/article", "source": "github"}, AT)
    event = store.query("EVENT")[0]
    fact = store.query(f"FACT#{event['id']}")[0]
    store.put(f"SENT#{fact['id']}", day="2026-10-25")
    store.put(f"SENT_EVENT#{event['id']}", "2026-10-25")
    ingest(store, AI(), {"url": "https://github.blog/reworded", "source": "github"}, AT + timedelta(hours=1))
    assert len(store.query(f"FACT#{event['id']}")) == 1
    assert store.query("EVENT")[0]["discovered"] == AT.isoformat()
