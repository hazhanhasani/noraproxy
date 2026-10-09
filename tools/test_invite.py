import unittest
from create_invite import make_invite

class InviteTests(unittest.TestCase):
    def test_encoded_seller_and_query(self):
        link = make_invite("فروشنده من", "https://example.com/sub?token=a&b=c")
        self.assertTrue(link.startswith("noraproxy://setup?"))
        self.assertIn("seller=", link)
        self.assertIn("token%3Da%26b%3Dc", link)

    def test_rejects_http(self):
        with self.assertRaises(ValueError):
            make_invite("Seller", "http://example.com/sub")

if __name__ == "__main__":
    unittest.main()
