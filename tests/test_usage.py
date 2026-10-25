from datetime import UTC, datetime, timedelta
from decimal import Decimal

from boangwan.domain import usage_cost
from boangwan.pipeline import usage_footer


def test_cache_tokens_are_not_double_counted():
    counts = dict(input=100, output=10, cache_read=1000, cache_write=200)
    rates = dict(input=0.1, output=0.5, cache_read=0.01, cache_write=0.125)
    assert usage_cost(counts, rates) == Decimal("0.000050")


def test_late_completion_counts_in_next_report(store):
    at = datetime(2026, 10, 25, tzinfo=UTC)
    store.put("STATE", usage_cutoff=(at - timedelta(minutes=1)).isoformat())
    store.put(
        "USAGE",
        (at - timedelta(minutes=2)).isoformat() + "#request",
        status="known",
        completed=at.isoformat(),
        counts={"input": 123},
        usd="0.5",
    )
    text, _ = usage_footer(store, at)
    assert "입력 123" in text
    assert "$0.500000" in text


def test_over_budget_is_only_a_warning(store, monkeypatch):
    at = datetime(2026, 10, 25, tzinfo=UTC)
    monkeypatch.setenv("USD_KRW", "1400")
    monkeypatch.setenv("AI_BUDGET_KRW", "8000")
    store.put("USAGE", at.isoformat() + "#request", status="known", counts={}, usd="10")
    text, _ = usage_footer(store, at)
    assert "예산 초과" in text
    assert "발송을 계속" in text
