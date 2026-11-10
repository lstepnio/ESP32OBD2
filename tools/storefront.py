"""Shared catalog for the public shop and private checkout tooling."""

import json
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[1] / "website/storefront.json"
ADAPTER = "vgate"
ADAPTER_NAME = "Vgate iCar Pro BLE OBD-II adapter"
US_SHIPPING_CENTS = 995
OFFERS = {
    "lcd": ("LCD hardware", "S3-LCD-1.28 v1 hardware", "eGauge preflashed LCD board"),
    "oled": ("OLED bundle", "S3-OLED-1.75 v1 with preinstalled firmware", "eGauge OLED bundle"),
}


def storefront_catalog(path=CATALOG):
    settings = json.loads(path.read_text())
    if (
        not isinstance(settings, dict)
        or set(settings) != {*OFFERS, ADAPTER}
        or any(
            not isinstance(offer, dict)
            or set(offer) != {"amountCents", "checkout"}
            or type(offer["amountCents"]) is not int
            or not 0 < offer["amountCents"] < 100_000
            or offer["checkout"] is not None
            for offer in settings.values()
        )
    ):
        raise ValueError(
            "Expected two USD gauge offers and one add-on with public checkout disabled"
        )
    return {key: value["amountCents"] for key, value in settings.items()}
