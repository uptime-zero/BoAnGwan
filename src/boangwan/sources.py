from __future__ import annotations

import ipaddress
import json
import socket
from datetime import UTC, datetime, timedelta
from pathlib import Path
from urllib.parse import urljoin, urlsplit

import feedparser
import httpx
import trafilatura
from bs4 import BeautifulSoup
from dateutil.parser import parse

from .domain import canonical_url, normalize


class RejectedArticle(ValueError):
    pass


def configured_sources() -> list[dict]:
    return [
        s for s in json.loads((Path(__file__).parent.parent / "sources.json").read_text()) if s["enabled"]
    ]


def fetch(url: str, hosts: list[str]) -> str:
    with httpx.Client(
        timeout=25, headers={"User-Agent": "BoAnGwan/0.1 (personal security news reader)"}
    ) as client:
        for _ in range(5):
            url = canonical_url(url)
            host = urlsplit(url).hostname
            if host not in hosts or urlsplit(url).port not in (None, 443):
                raise RejectedArticle("URL outside configured source")
            addresses = socket.getaddrinfo(host, 443)
            if any(not ipaddress.ip_address(a[4][0]).is_global for a in addresses):
                raise RejectedArticle("Non-public source address")
            with client.stream("GET", url) as response:
                if response.is_redirect:
                    url = urljoin(url, response.headers["location"])
                    continue
                response.raise_for_status()
                content_type = response.headers.get("content-type", "").lower()
                if not any(t in content_type for t in ("html", "xml", "text", "rss", "atom")):
                    raise RejectedArticle("Unsupported content type")
                body = bytearray()
                for piece in response.iter_bytes():
                    body.extend(piece)
                    if len(body) > 4_000_000:
                        raise RejectedArticle("Source exceeds 4 MB")
                return body.decode(response.encoding or "utf-8", errors="replace")
    raise RejectedArticle("Too many redirects")


def discover(source: dict, at: datetime) -> list[dict]:
    document = fetch(source["url"], source["hosts"])
    entries = []
    if source["kind"] == "rss":
        feed = feedparser.parse(document)
        if not feed.entries:
            raise ValueError("Empty or invalid feed")
        for entry in feed.entries[:60]:
            if entry.get("link"):
                entries.append({"url": entry.link, "published": entry.get("published", "")})
    else:
        soup = BeautifulSoup(document, "html.parser")
        for link in soup.select("a[href]"):
            url = urljoin(source["url"], link["href"])
            path = urlsplit(url).path
            if path.startswith(source["path"]) and not any(
                path.startswith(prefix) for prefix in source.get("exclude_paths", [])
            ):
                entries.append({"url": url, "published": ""})
    unique = {}
    for entry in entries:
        try:
            url = canonical_url(entry["url"])
            if urlsplit(url).hostname not in source["hosts"]:
                continue
            if entry["published"]:
                date = parse(entry["published"])
                date = date.replace(tzinfo=UTC) if date.tzinfo is None else date
                if at - date > timedelta(days=14):
                    continue
            unique[url] = {**entry, "url": url, "source": source["id"]}
        except (ValueError, OverflowError):
            continue
    return list(unique.values())[:60]


def extract_article(html: str, url: str, published: str, at: datetime) -> dict:
    soup = BeautifulSoup(html, "html.parser")
    for node in soup.select('script[type="application/ld+json"]'):
        if '"isAccessibleForFree":false' in normalize(node.get_text()).replace(" ", ""):
            raise RejectedArticle("Paywalled article")
    document = trafilatura.bare_extraction(
        html, url=url, with_metadata=True, include_comments=False, favor_precision=True
    )
    if document is None:
        raise RejectedArticle("No article body")
    body = document.text or ""
    if not 400 <= len(body) <= 120_000:
        raise RejectedArticle("Body too short or too long; excerpts are not used")
    # Feed publication date is preferred to HTML's potentially updated timestamp.
    date_meta = soup.select_one('meta[property="article:published_time"], meta[name="datePublished"]')
    date_time = soup.select_one("article time[datetime], main time[datetime]")
    raw_date = published or (date_meta.get("content") if date_meta else None)
    if not raw_date and date_time:
        raw_date = date_time.get("datetime")
    if not raw_date:
        raise RejectedArticle("Publication date unavailable")
    try:
        date = parse(raw_date)
    except (ValueError, OverflowError) as exc:
        raise RejectedArticle("Invalid publication date") from exc
    date = date.replace(tzinfo=UTC) if date.tzinfo is None else date.astimezone(UTC)
    if not timedelta(days=-1) <= at - date <= timedelta(days=14):
        raise RejectedArticle("Publication date outside eligibility window")
    return {"title": document.title or "", "body": body, "published": date.isoformat(), "url": url}
