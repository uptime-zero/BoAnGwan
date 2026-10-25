import boto3
import pytest
from moto import mock_aws

from boangwan.storage import Store


@pytest.fixture
def store(monkeypatch):
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "testing")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "testing")
    monkeypatch.setenv("AWS_DEFAULT_REGION", "us-east-1")
    monkeypatch.setenv("TABLE_NAME", "test-table")
    monkeypatch.setenv("BODY_BUCKET", "test-bodies")
    with mock_aws():
        boto3.client("dynamodb").create_table(
            TableName="test-table",
            BillingMode="PAY_PER_REQUEST",
            AttributeDefinitions=[
                {"AttributeName": "pk", "AttributeType": "S"},
                {"AttributeName": "sk", "AttributeType": "S"},
            ],
            KeySchema=[
                {"AttributeName": "pk", "KeyType": "HASH"},
                {"AttributeName": "sk", "KeyType": "RANGE"},
            ],
        )
        boto3.client("s3").create_bucket(Bucket="test-bodies")
        yield Store()
