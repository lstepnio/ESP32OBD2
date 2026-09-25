#!/usr/bin/env python3

"""
sudo apt install bluez python3-dbus python3-gi libcairo2-dev libgirepository1.0-dev
pip install bluezero pydbus
"""
import logging
from time import sleep

from bluezero import adapter, peripheral
from pydbus import SystemBus


SERVICE_DEF = {
    "uuid": "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
    "characteristics": [
        {
            "uuid": "6e400003-b5a3-f393-e0a9-e50e24dcca9e",
            "id": 1,
            "flags": ["notify"],
        },
        {
            "uuid": "6e400002-b5a3-f393-e0a9-e50e24dcca9e",
            "id": 2,
            "flags": ["write"],
        },
    ]
}

SERVICE_DEF2 = {
    "uuid": "e7810a71-73ae-499d-8c15-faa9aef0c3f2",
    "characteristics": [
        {
            "uuid": "bef8d6c9-9c21-4c9e-b632-bd58c1009f9f",
            "id": 1,
            "flags": ["notify", "write"],
        },
    ]
}

SERVICE_DEF3 = {
    "uuid": "0x18f0",
    "characteristics": [
        {
            "uuid": "0x2af0",
            "id": 1,
            "flags": ["notify"],
        },
        {
            "uuid": "0x2af1",
            "id": 2,
            "flags": ["write"],
        },
    ]
}

SERVICE_DEF = SERVICE_DEF3

class OBD2Peripheral:

    def __init__(self, service_def) -> None:
        self.service_def = service_def

        self.obd_values = {
            '010C': {'name': 'RPM', 'value': 1000, 'min': 1000, 'max': 8000, 'step': 100},
            '010D': {'name': 'Speed', 'value': 40, 'min': 40, 'max': 200, 'step': 5},
            '012F': {'name': 'Fuel', 'value': 100, 'min': 0, 'max': 100, 'step': -1},
            '0104': {'name': 'Engine Load', 'value': 50, 'min': 0, 'max': 100, 'step': 5},
            '0105': {'name': 'Coolant Temp', 'value': 90, 'min': -40, 'max': 215, 'step': 5},
        }

        self.periph = peripheral.Peripheral(
            adapter_address=adapter.list_adapters()[0],
            local_name='ESP32-OBD2',
            appearance=0,
        )

        self.periph.add_service(
            srv_id=1,
            uuid=service_def["uuid"],
            primary=True,
        )

        self.notify_char = None
        for char in service_def["characteristics"]:
            kwargs = {
                "srv_id": 1,
                "chr_id": char["id"],
                "uuid": char["uuid"],
                "value": b'',
                "notifying": "notify" in char.get("flags", []),
                "flags": char.get("flags", []),
            }
            if "write" in char.get("flags", []):
                kwargs["write_callback"] = self.rx_write

            self.periph.add_characteristic(**kwargs)

            if "notify" in char.get("flags", []):
                self.notify_char = self.periph.characteristics[-1]

        if not self.notify_char:
            raise ValueError("TX characteristic not found in service definition (notify).")

    def rx_write(self, value, options):
        command = value.decode(errors='ignore').strip()
        logging.info(f"Received from central: {command}")
        response = self.handle_command(command)

        self.notify_char.set_value((command + '\r').encode())
        self.notify_char.set_value((response + '\r').encode())
        self.notify_char.set_value(('>\r').encode())
        logging.info(f"Sent response: {response}")

    def handle_command(self, command: str) -> str:
        if command in self.obd_values:
            entry = self.obd_values[command]
            # Update value
            entry['value'] += entry['step']
            if entry['step'] > 0 and entry['value'] > entry['max']:
                entry['value'] = entry['min']
            elif entry['step'] < 0 and entry['value'] < entry['min']:
                entry['value'] = entry['max']

            if command == '010C':  # RPM
                rpm_val = int(entry['value'] * 4)
                A = (rpm_val >> 8) & 0xFF
                B = rpm_val & 0xFF
                return f'41 0C {A:02X} {B:02X}'
            elif command == '010D':  # Speed
                return f'41 0D {entry["value"]:02X}'
            elif command == '012F':  # Fuel Level
                return f'41 2F {entry["value"]:02X}'
            elif command == '0104':  # Engine Load
                return f'41 04 {entry["value"]:02X}'
            elif command == '0105':  # Coolant Temp
                coolant_val = entry['value'] + 40
                return f'41 05 {coolant_val:02X}'
            return '?'
        else:
            return '?'

    def start(self):
        logging.info(f"Advertising as:         ESP32-OBD2")
        logging.info(f"Adapter address:        {adapter.list_adapters()[0]}")
        logging.info(f"Service UUID:           {self.service_def['uuid']}")
        self.periph.publish()
        # blocks until KeyboardInterrupt (internally handled)
        self.periph.srv_mng.unregister_application(self.periph.app)
        self.reset_adapter()

    def reset_adapter(self) -> None:
        """Reset the Bluetooth adapter to clear any errors."""
        logging.info("Resetting Bluetooth adapter...")
        bus = SystemBus()
        adapter = bus.get('org.bluez', '/org/bluez/hci0')
        adapter.Powered = False
        sleep(0.1)
        adapter.Powered = True

def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format='[%(asctime)s.%(msecs)03d] %(message)s',
        datefmt='%H:%M:%S'
    )

    obd2 = OBD2Peripheral(SERVICE_DEF)
    obd2.start()
    logging.info("Exiting...")

if __name__ == '__main__':
    main()
