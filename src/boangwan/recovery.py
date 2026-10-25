"""Explicit reconciliation of past editions; never sends Discord requests."""

from .domain import KST
from .pipeline import mark_facts


def resolve_part(store, day, part, resolution, message_id=None):
    draft = store.get("DRAFT", day)
    if not draft or not 0 <= part < len(draft["parts"]):
        raise ValueError("Edition or part does not exist")
    if draft["status"] in {"sent", "cancelled"}:
        raise ValueError("Edition is already closed")
    record = store.get(f"SEND#{day}", f"{part:03}")
    if not record or record["status"] not in {"sending", "unknown", "failed"}:
        raise ValueError("Only unresolved parts may be reconciled")
    if resolution not in {"sent", "retry"} or (resolution == "sent" and not message_id):
        raise ValueError("A confirmed delivery requires its message ID")
    store.put(f"SEND#{day}", f"{part:03}", status=resolution, message_id=message_id, manually_resolved=True)
    if resolution == "sent":
        mark_facts(store, day, draft["parts"][part])


def close_edition(store, day, action, at):
    # The publisher only operates on today's edition. Avoid racing its network request.
    if day >= at.astimezone(KST).date().isoformat():
        raise ValueError("Only past editions can be closed manually")
    draft = store.get("DRAFT", day)
    if not draft or action not in {"complete", "cancel"}:
        raise ValueError("Invalid edition or action")
    if draft["status"] in {"sent", "cancelled"}:
        return draft["status"]
    records = [store.get(f"SEND#{day}", f"{i:03}") or {} for i in range(len(draft["parts"]))]
    if any(r.get("status") in {"sending", "unknown"} for r in records):
        raise ValueError("Resolve every ambiguous part against the Discord channel first")
    if action == "complete" and any(r.get("status") != "sent" for r in records):
        raise ValueError("Completion requires every part to be confirmed delivered")
    touched = set()
    for record, part in zip(records, draft["parts"]):
        if record.get("status") == "sent":
            mark_facts(store, day, part)
            # Even the overview may already have disclosed a fact. Do not re-send that version.
            touched.update(part.get("mentioned_events", [i["event_id"] for i in draft["items"]]))
    if action == "cancel":
        for item in draft["items"]:
            if item["event_id"] in touched:
                store.mark_revision(item["event_id"], int(item.get("revision", 1)))
    state = store.get("STATE") or {"cutoffs": {}}
    footer_delivered = records and records[-1].get("status") == "sent" and draft.get("usage_cutoff")
    if footer_delivered:
        state["usage_cutoff"] = max(state.get("usage_cutoff", ""), draft["usage_cutoff"])
    if action == "complete" and draft.get("category"):
        cutoffs = state.setdefault("cutoffs", {})
        # A historical repair must not rewind a more recent rotation.
        if draft["cutoff"] > max(cutoffs.values(), default=""):
            state["next_category"] = draft["next_category"]
        cutoffs[draft["category"]] = max(cutoffs.get(draft["category"], ""), draft["cutoff"])
    store.put("STATE", **{k: v for k, v in state.items() if k not in {"pk", "sk"}})
    draft["status"] = "sent" if action == "complete" else "cancelled"
    store.save(draft)
    return draft["status"]
