from __future__ import annotations

import json
import os
from decimal import Decimal

import boto3
from boto3.dynamodb.conditions import Key
from boto3.dynamodb.types import TypeSerializer
from botocore.exceptions import ClientError

from .domain import jsonable


class Store:
    def __init__(self):
        self.table = boto3.resource("dynamodb").Table(os.environ["TABLE_NAME"])
        self.s3 = boto3.client("s3")
        self.bucket = os.environ["BODY_BUCKET"]

    def get(self, pk: str, sk: str = "META") -> dict | None:
        item = self.table.get_item(Key={"pk": pk, "sk": sk}, ConsistentRead=True).get("Item")
        return jsonable(item) if item else None

    def put(self, pk: str, sk: str = "META", *, create: bool = False, **data) -> bool:
        item = json.loads(json.dumps({**data, "pk": pk, "sk": sk}), parse_float=Decimal)
        args = {"Item": item}
        if create:
            args["ConditionExpression"] = "attribute_not_exists(pk)"
        try:
            self.table.put_item(**args)
            return True
        except ClientError as exc:
            if exc.response["Error"]["Code"] == "ConditionalCheckFailedException":
                return False
            raise

    def save(self, item: dict):
        data = dict(item)
        self.put(data.pop("pk"), data.pop("sk"), **data)

    def transact(self, items: list[dict], guard: tuple[str, str] | None = None):
        """Commit related records together; guard makes an ingestion replay a no-op."""
        serializer = TypeSerializer()
        writes = []
        for item in items:
            value = json.loads(json.dumps(item), parse_float=Decimal)
            put = {
                "TableName": self.table.name,
                "Item": {k: serializer.serialize(v) for k, v in value.items()},
            }
            if guard == (item["pk"], item["sk"]):
                put["ConditionExpression"] = "attribute_not_exists(pk)"
            writes.append({"Put": put})
        try:
            boto3.client("dynamodb").transact_write_items(TransactItems=writes)
        except ClientError as exc:
            if exc.response["Error"]["Code"] == "TransactionCanceledException" and guard and self.get(*guard):
                return False
            raise
        return True

    def query(
        self,
        pk: str,
        start: str | None = None,
        end: str | None = None,
        limit: int | None = None,
        newest: bool = False,
        distinct: str | None = None,
    ) -> list[dict]:
        condition = Key("pk").eq(pk)
        if start is not None and end is not None:
            condition &= Key("sk").between(start, end)
        result, seen, cursor = [], set(), None
        while True:
            args = {
                "KeyConditionExpression": condition,
                "ConsistentRead": True,
                "ScanIndexForward": not newest,
            }
            if limit:
                args["Limit"] = limit - len(result)
            if cursor:
                args["ExclusiveStartKey"] = cursor
            response = self.table.query(**args)
            for item in jsonable(response["Items"]):
                if distinct:
                    value = item[distinct]
                    if value in seen:
                        continue
                    seen.add(value)
                result.append(item)
                if limit and len(result) >= limit:
                    return result
            cursor = response.get("LastEvaluatedKey")
            if not cursor:
                return result

    def body_put(self, key: str, body: str):
        self.s3.put_object(
            Bucket=self.bucket, Key=key, Body=body.encode(), ContentType="text/plain; charset=utf-8"
        )

    def body_get(self, key: str) -> str:
        return self.s3.get_object(Bucket=self.bucket, Key=key)["Body"].read().decode()

    def mark_revision(self, event_id: str, revision: int):
        try:
            self.table.update_item(
                Key={"pk": f"HANDLED#{event_id}", "sk": "META"},
                UpdateExpression="SET revision = :r",
                ConditionExpression="attribute_not_exists(revision) OR revision < :r",
                ExpressionAttributeValues={":r": revision},
            )
        except ClientError as exc:
            if exc.response["Error"]["Code"] != "ConditionalCheckFailedException":
                raise

    def claim_send(self, day: str, part: int) -> bool:
        try:
            self.table.update_item(
                Key={"pk": f"SEND#{day}", "sk": f"{part:03}"},
                UpdateExpression="SET #s = :sending",
                ConditionExpression="attribute_not_exists(#s) OR #s = :retry",
                ExpressionAttributeNames={"#s": "status"},
                ExpressionAttributeValues={":sending": "sending", ":retry": "retry"},
            )
            return True
        except ClientError as exc:
            if exc.response["Error"]["Code"] == "ConditionalCheckFailedException":
                return False
            raise


def secret() -> dict:
    response = boto3.client("secretsmanager").get_secret_value(SecretId=os.environ["SECRET_ARN"])
    return json.loads(response["SecretString"])
