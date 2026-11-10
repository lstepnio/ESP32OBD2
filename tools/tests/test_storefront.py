"""Check the public catalog and fail-closed checkout boundary."""

import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_website import storefront_actions
from storefront import storefront_catalog


class StorefrontTest(unittest.TestCase):
    def test_each_product_has_a_distinct_inquiry_and_price(self):
        actions = storefront_actions(storefront_catalog())
        self.assertEqual(actions["LCD_PRICE"], "$79 USD")
        self.assertEqual(actions["OLED_PRICE"], "$199 USD")
        self.assertEqual(actions["VGATE_PRICE"], "$55 USD")
        self.assertEqual(actions["US_SHIPPING_PRICE"], "$9.95 USD")
        self.assertNotIn("VGATE_ORDER", actions)
        self.assertIn('href="#custom-form"', actions["LCD_ORDER"])
        self.assertIn("S3-LCD-1.28 v1 hardware", actions["LCD_ORDER"])
        self.assertIn('href="#custom-form"', actions["OLED_ORDER"])
        self.assertIn("S3-OLED-1.75 v1 with preinstalled firmware", actions["OLED_ORDER"])

    def test_public_build_rejects_test_and_live_checkout(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "storefront.json"
            for url in (
                "https://buy.stripe.com/test_example",
                "https://buy.stripe.com/liveExample",
            ):
                with self.subTest(url=url), self.assertRaises(ValueError):
                    path.write_text(
                        json.dumps(
                            {
                                "lcd": {"amountCents": 7900, "checkout": None},
                                "oled": {"amountCents": 19900, "checkout": url},
                                "vgate": {"amountCents": 5500, "checkout": None},
                            }
                        )
                    )
                    storefront_catalog(path)
