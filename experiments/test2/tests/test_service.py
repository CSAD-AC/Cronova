# Test cases
import unittest
from src.services import process_data, transform_data, validate_data


class TestDataService(unittest.TestCase):
    def test_process_data(self):
        data = [
            {"id": 1, "enabled": True},
            {"id": 2, "enabled": False}
        ]
        result = process_data(data)
        self.assertEqual(len(result), 1)

    def test_transform_data(self):
        data = {"id": 1, "name": "test", "value": 10}
        result = transform_data(data)
        self.assertEqual(result["name"], "TEST")

    def test_validate_data(self):
        self.assertTrue(validate_data({"id": 1, "name": "test"}))
        self.assertFalse(validate_data({"id": 1}))


if __name__ == "__main__":
    unittest.main()
