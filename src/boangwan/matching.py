"""Bounded candidate retrieval, with complete fact comparison within selected candidates."""

import json
from datetime import datetime

from .domain import Match, digest, normalize
from .indexes import recent_events


def match_event(store, ai, extraction, at: datetime):
    identifiers = sorted({normalize(i).lower() for i in extraction.identifiers + [extraction.event_key]})
    scores = {}
    exact = digest(extraction.event_key.lower())[:32]
    if store.get("EVENT", exact):
        scores[exact] = 1000
    for identifier in identifiers:
        weight = 20 if identifier.startswith("cve-") or identifier == extraction.event_key.lower() else 2
        for key in {
            row["event_id"]
            for row in store.query(f"INDEX#{digest(identifier)}", limit=60, newest=True, distinct="event_id")
        }:
            scores[key] = scores.get(key, 0) + weight
    for row in recent_events(store, at):
        scores.setdefault(row["event_id"], 1)
    candidates = sorted(scores, key=lambda key: (-scores[key], key))[:24]
    # Split a large event's history into multiple entries instead of dropping old facts.
    batches, batch, length = [], [], 0
    for key in candidates:
        event = store.get("EVENT", key)
        if not event:
            continue
        facts = store.query(f"FACT#{key}")
        for offset in range(0, max(1, len(facts)), 12):
            record = {
                "id": key,
                "title": event["title"],
                "published": event["published"],
                "facts": [
                    {"text": f["text"], "sent": bool(store.get(f"SENT#{f['id']}"))}
                    for f in facts[offset : offset + 12]
                ],
            }
            size = len(json.dumps(record, ensure_ascii=False))
            if batch and length + size > 24_000:
                batches.append(batch)
                batch, length = [], 0
            batch.append(record)
            length += size
    if batch:
        batches.append(batch)
    chosen = None
    novel = set(range(len(extraction.facts)))
    material = False
    for history in batches or [[]]:
        result = ai.ask(
            Match,
            """Resolve this event against the supplied historical incident fragments.
Match only the SAME incident/vulnerability/research, not merely a vendor/topic. event_id must be a supplied ID.
novel_indices are zero-based facts absent from this history fragment; paraphrases are not new facts.
material_update is true only for a patch, confirmed exploitation, changed impact, correction or substantive
new incident information. A newer article date alone is not material. For no matching incident return null
and all extracted fact indices. History can be split across requests; compare only this supplied fragment.""",
            {"extracted": extraction.model_dump(), "history": history},
            "deduplicate",
        )
        if any(i < 0 or i >= len(extraction.facts) for i in result.novel_indices):
            raise ValueError("Invalid fact index")
        if result.event_id:
            if result.event_id not in {r["id"] for r in history}:
                raise ValueError("Unknown event selected")
            if chosen and chosen != result.event_id:
                raise ValueError("Ambiguous event identity")
            chosen = result.event_id
            novel.intersection_update(result.novel_indices)
            material = material or result.material_update
    return Match(
        event_id=chosen, novel_indices=sorted(novel), material_update=material if chosen else True
    ), identifiers
