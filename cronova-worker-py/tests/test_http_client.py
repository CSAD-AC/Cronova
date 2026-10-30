import unittest

from app.config import Config
from app.http_client import serialize_json_body
from app.utils.auth_utils import AuthUtils


class HttpClientSignatureBodyTest(unittest.TestCase):
    def test_none_body_stays_empty(self):
        self.assertIsNone(serialize_json_body(None))

    def test_empty_object_is_signed_as_object(self):
        self.assertEqual("{}", serialize_json_body({}))

    def test_unicode_body_is_serialized_consistently(self):
        self.assertEqual('{"name": "调度器"}', serialize_json_body({"name": "调度器"}))

    def test_empty_object_and_no_body_have_different_signatures(self):
        Config.ACCESS_KEY = "AK_TEST"
        Config.SECRET_KEY = "SK_TEST"
        timestamp = "1700000000"
        self.assertNotEqual(
            AuthUtils.generate_signature(timestamp=timestamp, body=serialize_json_body({})),
            AuthUtils.generate_signature(timestamp=timestamp, body=serialize_json_body(None)),
        )


if __name__ == "__main__":
    unittest.main()
