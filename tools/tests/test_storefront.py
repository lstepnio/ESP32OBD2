"""Check request-only and configured checkout modes."""

from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_website import storefront_actions


class StorefrontTest(unittest.TestCase):
    def test_each_product_has_a_distinct_inquiry(self):
        actions = storefront_actions({"lcdCheckout": None, "oledCheckout": None})
        self.assertIn('href="#custom-form"', actions["LCD_ORDER"])
        self.assertIn("S3-LCD-1.28 v1 hardware", actions["LCD_ORDER"])
        self.assertIn("S3-OLED-1.75 v1 hardware and license", actions["OLED_ORDER"])

    def test_configured_checkout_does_not_enable_the_other_product(self):
        actions = storefront_actions(
            {"lcdCheckout": "https://buy.stripe.com/test_example", "oledCheckout": None}
        )
        self.assertIn('href="https://buy.stripe.com/test_example"', actions["LCD_ORDER"])
        self.assertIn("Order LCD hardware", actions["LCD_ORDER"])
        self.assertIn("Request OLED bundle", actions["OLED_ORDER"])

    def test_untrusted_links_fail_the_build(self):
        for url in (
            "http://buy.stripe.com/id",
            "https://buy.stripe.com.invalid/id",
            "https://buy.stripe.com/id?x=y",
            True,
        ):
            with self.subTest(url=url), self.assertRaises(ValueError):
                storefront_actions({"lcdCheckout": None, "oledCheckout": url})
