"""Inspect a delivery or resolve a manually verified ambiguous part; never sends Discord messages."""

import argparse
import json
from datetime import UTC, datetime

from boangwan.recovery import close_edition, resolve_part
from boangwan.storage import Store

parser = argparse.ArgumentParser()
parser.add_argument("day", help="Korean edition date YYYY-MM-DD")
parser.add_argument("--part", type=int)
parser.add_argument("--resolve", choices=["sent", "retry"])
parser.add_argument("--message-id", help="Discord message ID verified in the channel")
parser.add_argument("--confirm-checked-channel", action="store_true")
parser.add_argument(
    "--close", choices=["complete", "cancel"], help="Reconcile a past edition without sending"
)
args = parser.parse_args()
store = Store()
if args.resolve:
    if args.part is None or not args.confirm_checked_channel:
        parser.error("Resolution requires --part and --confirm-checked-channel")
    try:
        resolve_part(store, args.day, args.part, args.resolve, args.message_id)
    except ValueError as exc:
        parser.error(str(exc))
if args.close:
    if not args.confirm_checked_channel:
        parser.error("Closing an edition requires --confirm-checked-channel")
    try:
        print(close_edition(store, args.day, args.close, datetime.now(UTC)))
    except ValueError as exc:
        parser.error(str(exc))
print(json.dumps(store.query(f"SEND#{args.day}"), ensure_ascii=False, indent=2))
