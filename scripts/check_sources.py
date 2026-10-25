"""Read-only public-source probe: no AI calls, storage writes or Discord messages."""

import json
from datetime import UTC, datetime

from boangwan.sources import configured_sources, discover, extract_article, fetch

at = datetime.now(UTC)
results = []
for source in configured_sources():
    result = {"source": source["id"]}
    try:
        entries = discover(source, at)
        result["discovered"] = len(entries)
        samples = []
        for entry in entries[:3]:
            try:
                article = extract_article(
                    fetch(entry["url"], source["hosts"]), entry["url"], entry["published"], at
                )
                samples.append({"url": entry["url"], "body_characters": len(article["body"]), "ok": True})
            except Exception as exc:
                samples.append({"url": entry["url"], "ok": False, "error": type(exc).__name__})
        result["samples"] = samples
    except Exception as exc:
        result["error"] = type(exc).__name__
    results.append(result)
print(json.dumps(results, ensure_ascii=False, indent=2))
