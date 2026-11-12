"""Exercise the sandbox checkout contract."""

from copy import deepcopy
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from storefront import storefront_catalog
from stripe_storefront import StripeSandbox, setup, verify_order

SESSION = "cs_test_OledOrder"


class SandboxFixture:
    def __init__(self):
        self.writes = []
        self.tax_registrations = [{"livemode": False}]
        self.session = {
            "id": SESSION,
            "livemode": False,
            "status": "complete",
            "payment_status": "paid",
            "mode": "payment",
            "currency": "usd",
            "metadata": {"egauge_offer": "oled"},
            "payment_link": "plink_oled",
            "collected_information": {"shipping_details": {"address": {"country": "US"}}},
            "shipping_cost": {"shipping_rate": "shr_ground"},
            "automatic_tax": {"enabled": True, "status": "complete"},
            "total_details": {"amount_shipping": 995, "amount_tax": 1000, "amount_discount": 0},
            "amount_subtotal": 19900,
            "amount_total": 21895,
            "payment_intent": {
                "livemode": False,
                "status": "succeeded",
                "amount_received": 21895,
                "latest_charge": {
                    "livemode": False,
                    "paid": True,
                    "captured": True,
                    "amount_refunded": 0,
                    "disputed": False,
                },
            },
        }
        self.items = {
            "has_more": False,
            "data": [{"quantity": 1, "price": {"id": "price_oled"}, "amount_subtotal": 19900}],
        }

    def request(self, path, *, data=None, idempotency=None):
        if data is not None:
            self.writes.append((path, data, idempotency))
            result = {"livemode": False}
            if path == "/v1/shipping_rates":
                return result | {"id": "shr_ground"}
            if path == "/v1/products":
                key = {19900: "oled", 7900: "lcd", 5500: "vgate"}[
                    data["default_price_data[unit_amount]"]
                ]
                return result | {"default_price": "price_" + key}
            if path == "/v1/payment_links":
                key = data["metadata[egauge_offer]"]
                return result | {"id": "plink_" + key, "url": "https://buy.stripe.com/test_" + key}
        if path == "/v1/tax/settings":
            return {"livemode": False, "status": "active"}
        if path.startswith("/v1/tax/registrations?"):
            return {"data": self.tax_registrations}
        if path == f"/v1/checkout/sessions/{SESSION}?expand%5B0%5D=payment_intent.latest_charge":
            return self.session
        if path == f"/v1/checkout/sessions/{SESSION}/line_items?limit=2":
            return self.items
        raise AssertionError(path)


class StripeStorefrontTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.stripe = SandboxFixture()
        self.state = setup(self.stripe, self.root / "checkout.json", storefront_catalog(), 995)

    def test_setup_tax_shipping_and_idempotency(self):
        self.assertEqual(len(self.stripe.writes), 6)
        for path, params, idempotency in self.stripe.writes:
            self.assertTrue(idempotency.startswith("egauge-"))
            if path == "/v1/payment_links":
                self.assertEqual(params["automatic_tax[enabled]"], "true")
                self.assertEqual(params["shipping_address_collection[allowed_countries][0]"], "US")
                self.assertEqual(params["shipping_options[0][shipping_rate]"], "shr_ground")
                self.assertEqual(params["payment_method_types[0]"], "card")
                self.assertEqual(params["optional_items[0][price]"], "price_vgate")
                self.assertEqual(params["optional_items[0][quantity]"], 1)
        self.assertEqual(
            setup(self.stripe, self.root / "checkout.json", storefront_catalog(), 995), self.state
        )
        self.assertEqual(len(self.stripe.writes), 6)
        with self.assertRaises(ValueError):
            setup(self.stripe, self.root / "checkout.json", storefront_catalog(), 1000)
        self.stripe.tax_registrations = []
        with self.assertRaisesRegex(ValueError, "tax registration"):
            setup(self.stripe, self.root / "other.json", storefront_catalog(), 995)
        with self.assertRaises(ValueError):
            StripeSandbox("sk_live_Sensitive")

    def test_paid_test_order_requires_correct_checkout_tax_shipping_and_charge(self):
        self.assertEqual(
            verify_order(self.stripe, self.state, SESSION), ("oled", 19900, 995, 1000, False)
        )
        self.stripe.session.update(
            metadata={"egauge_offer": "lcd"},
            payment_link="plink_lcd",
            amount_subtotal=7900,
            amount_total=9895,
        )
        self.stripe.session["payment_intent"]["amount_received"] = 9895
        self.stripe.items["data"][0].update(price={"id": "price_lcd"}, amount_subtotal=7900)
        self.assertEqual(
            verify_order(self.stripe, self.state, SESSION), ("lcd", 7900, 995, 1000, False)
        )
        with self.assertRaises(ValueError):
            verify_order(self.stripe, self.state, "cs_live_Order")
        for patch in (
            {"livemode": True},
            {"payment_status": "unpaid"},
            {"status": "expired"},
            {"payment_link": "plink_wrong"},
            {"automatic_tax": {"enabled": True, "status": "requires_location_inputs"}},
            {"shipping_cost": {"shipping_rate": "shr_wrong"}},
            {"collected_information": None},
            {"amount_total": 0},
        ):
            original = self.stripe.session
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                self.stripe.session = original | patch
                verify_order(self.stripe, self.state, SESSION)
            self.stripe.session = original
        self.stripe.items["has_more"] = True
        with self.assertRaisesRegex(ValueError, "line items"):
            verify_order(self.stripe, self.state, SESSION)
        self.stripe.items["has_more"] = False
        for patch in ({"amount_refunded": 1}, {"disputed": True}, {"captured": False}):
            intent = deepcopy(self.stripe.session["payment_intent"])
            with self.subTest(patch=patch), self.assertRaisesRegex(ValueError, "captured"):
                self.stripe.session["payment_intent"]["latest_charge"].update(patch)
                verify_order(self.stripe, self.state, SESSION)
            self.stripe.session["payment_intent"] = intent

    def test_optional_adapter_must_be_one_catalog_item_with_a_gauge(self):
        adapter = {"quantity": 1, "price": {"id": "price_vgate"}, "amount_subtotal": 5500}
        self.stripe.items["data"].insert(0, adapter)
        self.stripe.session.update(amount_subtotal=25400, amount_total=27395)
        self.stripe.session["payment_intent"]["amount_received"] = 27395
        self.assertEqual(
            verify_order(self.stripe, self.state, SESSION), ("oled", 25400, 995, 1000, True)
        )
        gauge = self.stripe.items["data"][1]
        for lines in (
            [adapter],
            [gauge, adapter, adapter],
            [gauge, adapter | {"quantity": 2}],
            [gauge, adapter | {"amount_subtotal": 4900}],
            [gauge, adapter | {"price": {"id": "price_other"}}],
            [gauge, adapter | {"price": {"id": "price_vgate_lcd"}, "amount_subtotal": 3000}],
        ):
            with self.subTest(lines=lines), self.assertRaisesRegex(ValueError, "line items"):
                self.stripe.items["data"] = lines
                verify_order(self.stripe, self.state, SESSION)
