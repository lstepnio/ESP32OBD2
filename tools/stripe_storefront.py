#!/usr/bin/env python3
"""Set up and fulfill eGauge orders in a Stripe test sandbox."""

import argparse
from fcntl import LOCK_EX, flock
from getpass import getpass
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import uuid4

from storefront import (
    ADAPTER,
    ADAPTER_NAME,
    OFFERS,
    US_SHIPPING_CENTS,
    storefront_catalog,
)

API_VERSION = "2026-10-28.endive"
TEST_LINK = re.compile(r"https://buy\.stripe\.com/test_[A-Za-z0-9]+")
SESSION = re.compile(r"cs_test_[A-Za-z0-9]+")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


class StripeSandbox:
    def __init__(self, key):
        if not re.fullmatch(r"(sk|rk)_test_[A-Za-z0-9]+", key):
            raise ValueError("A Stripe test-mode secret or restricted key is required")
        self.key = key
        self.http = build_opener(NoRedirect)

    def request(self, path, *, data=None, idempotency=None):
        if not re.fullmatch(r"/v1/[A-Za-z0-9_/?=&%.-]+", path):
            raise ValueError("Invalid Stripe API path")
        headers = {"Authorization": f"Bearer {self.key}", "Stripe-Version": API_VERSION}
        if idempotency:
            headers["Idempotency-Key"] = idempotency
        payload = None if data is None else urlencode(data).encode()
        request = Request("https://api.stripe.com" + path, data=payload, headers=headers)
        try:
            with self.http.open(request, timeout=15) as response:
                return json.loads(response.read(1_000_000))
        except URLError as error:
            code = getattr(error, "code", None)
            raise ValueError(f"Stripe API request failed (HTTP {code or 'unavailable'})") from None


def test_object(obj):
    if obj.get("livemode") is not False:
        raise ValueError("Expected a Stripe sandbox object")
    return obj


def save_state(path, state):
    fd, temporary = tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(fd, "w") as output:
            json.dump(state, output, indent=2)
            output.write("\n")
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def setup(stripe, path, prices, shipping_cents):
    if not 0 < shipping_cents <= 10_000:
        raise ValueError("Supply a positive test shipping amount in USD cents")
    if test_object(stripe.request("/v1/tax/settings"))["status"] != "active":
        raise ValueError("Configure the Stripe Tax origin in this sandbox first")
    registrations = stripe.request("/v1/tax/registrations?status=active&limit=1")["data"]
    if not registrations:
        raise ValueError("Add an active sandbox tax registration first")
    test_object(registrations[0])
    state = (
        json.loads(path.read_text())
        if path.exists()
        else {"setup": uuid4().hex, "shippingCents": shipping_cents, "prices": prices, "offers": {}}
    )
    if state["shippingCents"] != shipping_cents or state["prices"] != prices:
        raise ValueError(
            "Use a fresh private workspace for a different test catalog or shipping rate"
        )
    save_state(path, state)

    def create(path, role, data):
        return test_object(
            stripe.request(path, data=data, idempotency=f"egauge-{state['setup']}-{role}")
        )

    if "shippingRate" not in state:
        shipping = create(
            "/v1/shipping_rates",
            "shipping",
            {
                "display_name": "Standard shipping",
                "type": "fixed_amount",
                "fixed_amount[currency]": "usd",
                "fixed_amount[amount]": shipping_cents,
                "tax_behavior": "exclusive",
                "tax_code": "txcd_92010001",
            },
        )
        state["shippingRate"] = shipping["id"]
        save_state(path, state)

    def ensure_price(key, name, item):
        if "price" not in item:
            product = create(
                "/v1/products",
                f"{key}-product",
                {
                    "name": name,
                    "shippable": "true",
                    "tax_code": "txcd_99999999",
                    "default_price_data[currency]": "usd",
                    "default_price_data[unit_amount]": prices[key],
                    "default_price_data[tax_behavior]": "exclusive",
                },
            )
            item["price"] = product["default_price"]
            save_state(path, state)
        return item["price"]

    adapter_price = ensure_price(ADAPTER, ADAPTER_NAME, state.setdefault("adapter", {}))
    for offer, (_, _, name) in OFFERS.items():
        item = state["offers"].setdefault(offer, {})
        gauge_price = ensure_price(offer, name, item)
        if "link" not in item:
            parameters = {
                "line_items[0][price]": gauge_price,
                "line_items[0][quantity]": 1,
                "optional_items[0][price]": adapter_price,
                "optional_items[0][quantity]": 1,
                "automatic_tax[enabled]": "true",
                "shipping_address_collection[allowed_countries][0]": "US",
                "shipping_options[0][shipping_rate]": state["shippingRate"],
                "payment_method_types[0]": "card",
                "metadata[egauge_offer]": offer,
                "after_completion[type]": "hosted_confirmation",
                "after_completion[hosted_confirmation][custom_message]": "Test order. Nothing will ship.",
            }
            if offer == "oled":
                parameters["custom_text[submit][message]"] = "Estimated lead time is about 30 days."
            link = create("/v1/payment_links", f"{offer}-link", parameters)
            if not TEST_LINK.fullmatch(link["url"]):
                raise ValueError("Stripe did not return a test Payment Link")
            item.update(link=link["id"], url=link["url"])
            save_state(path, state)
        if not TEST_LINK.fullmatch(item["url"]):
            raise ValueError("Invalid stored test Payment Link")
    return state


def verify_order(stripe, state, session_id):
    if not SESSION.fullmatch(session_id):
        raise ValueError("Expected a cs_test_ Checkout Session ID")
    path = f"/v1/checkout/sessions/{session_id}"
    session = test_object(stripe.request(path + "?expand%5B0%5D=payment_intent.latest_charge"))
    if (
        session.get("id") != session_id
        or session.get("status") != "complete"
        or session.get("payment_status") != "paid"
        or session.get("mode") != "payment"
        or session.get("currency") != "usd"
    ):
        raise ValueError("Checkout is not a completed, paid USD test order")
    offer = session.get("metadata", {}).get("egauge_offer")
    item = state["offers"].get(offer)
    if not item or session.get("payment_link") != item["link"]:
        raise ValueError("Checkout does not match a configured eGauge Payment Link")
    line_items = stripe.request(path + "/line_items?limit=2")
    lines = line_items["data"]
    allowed = {item["price"]: state["prices"][offer]}
    adapter_price = item.get("adapter", {}).get("price")
    # Earlier sandbox links stored their adapter price separately for each gauge.
    adapter_key = "vgate_lcd" if adapter_price and offer == "lcd" else ADAPTER
    if not adapter_price:
        adapter_price = state.get("adapter", {}).get("price")
    if adapter_price:
        allowed[adapter_price] = state["prices"][adapter_key]
    purchased = {line["price"]["id"]: line for line in lines}
    if (
        line_items["has_more"]
        or len(purchased) != len(lines)
        or item["price"] not in purchased
        or any(
            price not in allowed
            or (line["quantity"], line["amount_subtotal"]) != (1, allowed[price])
            for price, line in purchased.items()
        )
    ):
        raise ValueError("Checkout line items or price do not match the offer")
    subtotal = sum(allowed[price] for price in purchased)
    shipping = session.get("shipping_cost") or {}
    ship_to = (session.get("collected_information") or {}).get("shipping_details") or {}
    tax = session.get("automatic_tax") or {}
    details = session.get("total_details") or {}
    shipping_cents = details.get("amount_shipping")
    tax_cents = details.get("amount_tax")
    if (
        shipping.get("shipping_rate") != state["shippingRate"]
        or (ship_to.get("address") or {}).get("country") != "US"
        or shipping_cents != state["shippingCents"]
        or tax.get("enabled") is not True
        or tax.get("status") != "complete"
        or type(tax_cents) is not int
        or tax_cents < 0
        or details.get("amount_discount") != 0
        or session.get("amount_subtotal") != subtotal
        or session.get("amount_total") != subtotal + shipping_cents + tax_cents
    ):
        raise ValueError("Checkout tax, shipping or totals are incomplete or do not match")
    intent = test_object(session.get("payment_intent") or {})
    charge = test_object(intent.get("latest_charge") or {})
    if (
        intent.get("status") != "succeeded"
        or intent.get("amount_received") != session["amount_total"]
        or charge.get("paid") is not True
        or charge.get("captured") is not True
        or charge.get("amount_refunded") != 0
        or charge.get("disputed") is not False
    ):
        raise ValueError("Checkout payment is not captured and available for fulfillment")
    return offer, subtotal, shipping_cents, tax_cents, adapter_price in purchased


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--workspace", type=Path, required=True, help="Private, mode-0700 directory"
    )
    parser.add_argument("--stripe-key-file", type=Path, help="Private key file, or prompt securely")
    sub = parser.add_subparsers(dest="command", required=True)
    configure = sub.add_parser("setup")
    configure.add_argument("--shipping-cents", type=int, default=US_SHIPPING_CENTS)
    order = sub.add_parser("verify-order")
    order.add_argument("session", help="Checkout Session ID starting with cs_test_")
    args = parser.parse_args()
    os.umask(0o077)
    try:
        if (
            not args.workspace.is_dir()
            or args.workspace.stat().st_mode & 0o077
            or args.workspace.stat().st_uid != os.getuid()
        ):
            raise ValueError("Use an owner-only test workspace")
        if args.stripe_key_file:
            if (
                not args.stripe_key_file.is_file()
                or args.stripe_key_file.stat().st_mode & 0o077
                or args.stripe_key_file.stat().st_uid != os.getuid()
            ):
                raise ValueError("Restrict the Stripe test key file to its owner")
            key = args.stripe_key_file.read_text().strip()
        elif sys.stdin.isatty():
            key = getpass("Stripe sandbox API key: ")
        else:
            raise ValueError("Use --stripe-key-file for scripted or piped input")
        stripe = StripeSandbox(key)
        config = args.workspace / "checkout.json"
        if args.command == "setup":
            with (args.workspace / "setup.lock").open("a") as lock:
                flock(lock, LOCK_EX)
                state = setup(stripe, config, storefront_catalog(), args.shipping_cents)
            for name, item in state["offers"].items():
                print(f"{name.upper()} test checkout: {item['url']}")
            return
        offer, subtotal, shipping, tax, has_adapter = verify_order(
            stripe, json.loads(config.read_text()), args.session
        )
        adapter_label = "included" if has_adapter else "not selected"
        print(
            f"Verified {offer.upper()} sandbox order (USD cents): items {subtotal}, "
            f"shipping {shipping}, tax {tax}. Vgate add-on: {adapter_label}."
        )
    except ValueError as error:
        parser.exit(1, f"{error}\n")
    except (OSError, KeyError, TypeError):
        parser.exit(
            1, "Sandbox operation failed. Check the private workspace, keys and Stripe setup.\n"
        )


if __name__ == "__main__":
    main()
