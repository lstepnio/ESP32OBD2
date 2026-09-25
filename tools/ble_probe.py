"""Read the experimental public eGauge BLE capability characteristic.

Requires bleak from tools/requirements-ble.txt. No pairing or vehicle commands.
"""

import asyncio
import json

from bleak import BleakClient, BleakScanner

SERVICE = "6f1a0000-9e3b-4f45-a714-69c9d23b6c00"
CAPABILITIES = "6f1a0001-9e3b-4f45-a714-69c9d23b6c00"


async def main() -> None:
    device = await BleakScanner.find_device_by_filter(
        lambda _device, adv: SERVICE in (value.lower() for value in adv.service_uuids),
        timeout=12,
    )
    if device is None:
        raise SystemExit("No eGauge BLE advertisement found")
    async with BleakClient(device, timeout=15) as client:
        data = bytes(await client.read_gatt_char(CAPABILITIES))
        capabilities = json.loads(data)
        if capabilities.get("protocolMajor") != 0:
            raise SystemExit("Unexpected experimental protocol version")
        if capabilities.get("configWrite") is not False or capabilities.get("ota") is not False:
            raise SystemExit("Unexpected writable or OTA capability")
        print(json.dumps(capabilities, indent=2, sort_keys=True))


if __name__ == "__main__":
    asyncio.run(main())
