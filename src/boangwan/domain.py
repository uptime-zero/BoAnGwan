from __future__ import annotations

import hashlib
import json
import re
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from typing import Literal
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, Field, field_validator

KST = ZoneInfo("Asia/Seoul")
CATEGORIES = ("cloud", "application", "endpoint", "data", "ai")
LABELS = dict(
    zip(CATEGORIES, ("클라우드·인프라", "애플리케이션·공급망", "단말·계정", "데이터·개인정보", "AI 보안"))
)
Category = Literal["cloud", "application", "endpoint", "data", "ai"]


def now_iso() -> str:
    return datetime.now(UTC).isoformat()


def parse_time(value: str) -> datetime:
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if result.tzinfo is None:
        raise ValueError("Timezone required")
    return result.astimezone(UTC)


def digest(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def normalize(value: str) -> str:
    return " ".join(value.split())


def canonical_url(value: str) -> str:
    parts = urlsplit(value)
    if parts.scheme != "https" or not parts.hostname or parts.username or parts.password:
        raise ValueError("Only public HTTPS URLs are allowed")
    query = [
        (k, v) for k, v in parse_qsl(parts.query) if not k.startswith("utm_") and k not in {"fbclid", "gclid"}
    ]
    return urlunsplit(("https", parts.netloc.lower(), parts.path or "/", urlencode(query), ""))


def jsonable(value):
    return json.loads(json.dumps(value, default=lambda x: float(x) if isinstance(x, Decimal) else str(x)))


class Schema(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ExtractedFact(Schema):
    text: str = Field(min_length=10, max_length=700)
    quote: str = Field(min_length=20, max_length=1500)


class Extraction(Schema):
    relevant: bool
    title: str = Field(max_length=180)
    category: Category
    event_key: str = Field(min_length=3, max_length=160)
    identifiers: list[str] = Field(max_length=12)
    impact: int = Field(ge=0, le=40)
    threat: int = Field(ge=0, le=30)
    novelty: int = Field(ge=0, le=20)
    urgent: bool
    urgency_quote: str = Field(max_length=1500)
    facts: list[ExtractedFact] = Field(max_length=12)

    @field_validator("identifiers", mode="before")
    @classmethod
    def cap_identifiers(cls, value):
        # Vendor advisories can enumerate more CVEs than the schema's index limit.
        # Keep the strongest lookup keys instead of sending an otherwise valid
        # article to the SQS dead-letter queue because the model returned extras.
        if not isinstance(value, list) or len(value) <= 12:
            return value
        if not all(isinstance(identifier, str) for identifier in value):
            return value

        unique = []
        seen = set()
        for identifier in value:
            identifier = identifier.strip()
            normalized = identifier.casefold()
            if identifier and normalized not in seen:
                unique.append(identifier)
                seen.add(normalized)

        cves = [identifier for identifier in unique if identifier.upper().startswith("CVE-")]
        other = [identifier for identifier in unique if not identifier.upper().startswith("CVE-")]
        return (cves + other)[:12]


class Match(Schema):
    event_id: str | None
    novel_indices: list[int] = Field(max_length=12)
    material_update: bool


class Claim(Schema):
    text: str = Field(min_length=5, max_length=500)
    fact_ids: list[str] = Field(min_length=1, max_length=6)


class Brief(Schema):
    claims: list[Claim] = Field(min_length=1, max_length=6)


class Verdict(Schema):
    supported_indices: list[int]
    current: bool


def grounded(extraction: Extraction, body: str) -> bool:
    text = normalize(body)
    return bool(extraction.facts) and all(normalize(f.quote) in text for f in extraction.facts)


def event_score(event: dict, at: datetime) -> float:
    age = max(0, (at - parse_time(event["published"])).total_seconds() / 86400)
    return float(event["importance"]) + max(0, 10 * (1 - age / 14))


def eligible(event: dict, at: datetime) -> bool:
    age = at - parse_time(event["published"])
    return bool(event.get("pending")) and timedelta(days=-1) <= age <= timedelta(days=14)


def select_events(
    events: list[dict], state: dict, at: datetime, excluded=()
) -> tuple[str | None, list[dict]]:
    pool = [e for e in events if eligible(e, at) and e["id"] not in excluded]
    ranked = sorted(pool, key=lambda e: (-event_score(e, at), e["id"]))
    urgent = next((e for e in ranked if e.get("urgent")), None)
    start = int(state.get("next_category", 0))
    for offset in range(5):
        category = CATEGORIES[(start + offset) % 5]
        group = [e for e in ranked if e["category"] == category and (not urgent or e["id"] != urgent["id"])]
        last = state.get("cutoffs", {}).get(category)
        fresh = [e for e in group if not last or e["discovered"] > last]
        older = [e for e in group if last and e["discovered"] <= last]
        # Old stories supplement one slot; an entirely old edition can contain several.
        selected = (fresh[:3] + older[:1]) if fresh else older[:4]
        if fresh and not older:
            selected = fresh[:4]
        if selected or (urgent and urgent["category"] == category):
            return category, ([urgent] if urgent else []) + selected
    return (urgent["category"], [urgent]) if urgent else (None, [])


def escape_discord(text: str) -> str:
    text = text.replace("@", "＠")
    return re.sub(r"([\\`*_~|>\[\]])", r"\\\1", text)


def chunks(text: str, limit: int = 1900) -> list[str]:
    # Keep source links indivisible; split prose at paragraph/word boundaries where possible.
    def size(value):
        return len(value.encode("utf-16-le")) // 2

    result, current = [], ""
    tokens = re.findall(r"<https://[^\s<>]+>|https://[^\s<>]+|\s+|[^\s<]+|<", text)
    for token in tokens:
        if size(token) > limit:
            if token.startswith(("https://", "<https://")):
                raise ValueError("Source URL exceeds Discord message capacity")
            pieces, piece = [], ""
            for char in token:
                if size(piece + char) > limit:
                    pieces.append(piece)
                    piece = ""
                piece += char
            if piece:
                pieces.append(piece)
        else:
            pieces = [token]
        for piece in pieces:
            if current and size(current + piece) > limit:
                result.append(current)
                current = ""
            current += piece
    if current:
        result.append(current)
    return result


def usage_cost(usage: dict, rates: dict) -> Decimal:
    return sum(
        (Decimal(str(usage.get(key, 0))) * Decimal(str(rate)) / 1_000_000 for key, rate in rates.items()),
        Decimal(0),
    )
