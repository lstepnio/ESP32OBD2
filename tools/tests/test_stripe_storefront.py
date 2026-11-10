"""Exercise the sandbox checkout contract."""

from copy import deepcopy
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from storefront import storefront_catalog
from stripe_storefront import MAX_ADAPTER_QUANTITY, StripeSandbox, setup, verify_order

SESSION = "cs_test_OledOrder"
ADAPTER_CENTS = 2995


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
                key = idempotency.split("-")[-2]
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
        self.state = setup(
            self.stripe, self.root / "checkout.json", storefront_catalog(), 995, ADAPTER_CENTS
        )

    def test_setup_tax_shipping_and_idempotency(self):
        self.assertEqual(len(self.stripe.writes), 6)
        for path, params, idempotency in self.stripe.writes:
            self.assertTrue(idempotency.startswith("egauge-"))
            if path == "/v1/payment_links":
                self.assertEqual(params["automatic_tax[enabled]"], "true")
                self.assertEqual(params["shipping_address_collection[allowed_countries][0]"], "US")
                self.assertEqual(params["shipping_options[0][shipping_rate]"], "shr_ground")
                self.assertEqual(params["payment_method_types[0]"], "card")
                self.assertEqual(params["line_items[0][quantity]"], 1)
                self.assertEqual(params["optional_items[0][price]"], "price_adapter")
                self.assertEqual(params["optional_items[0][quantity]"], 1)
                self.assertEqual(params["optional_items[0][adjustable_quantity][enabled]"], "true")
                self.assertEqual(params["optional_items[0][adjustable_quantity][minimum]"], 1)
                self.assertEqual(
                    params["optional_items[0][adjustable_quantity][maximum]"], MAX_ADAPTER_QUANTITY
                )
        self.assertEqual(
            setup(
                self.stripe, self.root / "checkout.json", storefront_catalog(), 995, ADAPTER_CENTS
            ),
            self.state,
        )
        self.assertEqual(len(self.stripe.writes), 6)
        with self.assertRaises(ValueError):
            setup(
                self.stripe, self.root / "checkout.json", storefront_catalog(), 1000, ADAPTER_CENTS
            )
        self.stripe.tax_registrations = []
        with self.assertRaisesRegex(ValueError, "tax registration"):
            setup(self.stripe, self.root / "other.json", storefront_catalog(), 995, ADAPTER_CENTS)
        with self.assertRaises(ValueError):
            StripeSandbox("sk_live_Sensitive")

    def test_paid_test_order_requires_correct_checkout_tax_shipping_and_charge(self):
        self.assertEqual(
            verify_order(self.stripe, self.state, SESSION), ("oled", 19900, 995, 1000, 0)
        )
        self.stripe.session.update(
            metadata={"egauge_offer": "lcd"},
            payment_link="plink_lcd",
            amount_subtotal=7500,
            amount_total=9495,
        )
        self.stripe.session["payment_intent"]["amount_received"] = 9495
        self.stripe.items["data"][0].update(price={"id": "price_lcd"}, amount_subtotal=7500)
        self.assertEqual(
            verify_order(self.stripe, self.state, SESSION), ("lcd", 7500, 995, 1000, 0)
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

    def test_optional_adapters_on_either_order(self):
        for offer, base in self.state["prices"].items():
            self.stripe.session.update(
                metadata={"egauge_offer": offer}, payment_link=f"plink_{offer}"
            )
            for quantity in (1, 2, MAX_ADAPTER_QUANTITY):
                with self.subTest(offer=offer, quantity=quantity):
                    subtotal = base + quantity * ADAPTER_CENTS
                    self.stripe.session.update(
                        amount_subtotal=subtotal, amount_total=subtotal + 1995
                    )
                    self.stripe.session["payment_intent"]["amount_received"] = subtotal + 1995
                    self.stripe.items["data"] = [
                        {
                            "quantity": quantity,
                            "price": {"id": "price_adapter"},
                            "amount_subtotal": quantity * ADAPTER_CENTS,
                        },
                        {"quantity": 1, "price": {"id": f"price_{offer}"}, "amount_subtotal": base},
                    ]
                    self.assertEqual(
                        verify_order(self.stripe, self.state, SESSION),
                        (offer, subtotal, 995, 1000, quantity),
                    )
                    for patch in (
                        {"price": {"id": "price_wrong"}},
                        {"amount_subtotal": 1},
                        {"quantity": MAX_ADAPTER_QUANTITY + 1},
                        {"quantity": 0},
                    ):
                        adapter = self.stripe.items["data"][0]
                        self.stripe.items["data"][0] = adapter | patch
                        with self.assertRaisesRegex(ValueError, "adapter quantity or price"):
                            verify_order(self.stripe, self.state, SESSION)
                        self.stripe.items["data"][0] = adapter

    def test_historical_order_can_still_be_verified(self):
        self.state.pop("adapterPrice")
        self.state.pop("adapterCents")
        self.assertEqual(
            verify_order(self.stripe, self.state, SESSION), ("oled", 19900, 995, 1000, 0)
        )
