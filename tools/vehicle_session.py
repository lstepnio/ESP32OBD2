#!/usr/bin/env python3
"""Interactive Mac companion for the first parked, read-only vehicle session."""
import argparse
import asyncio
from pathlib import Path
import subprocess
import sys

from obd_capture import DEFAULT_OUTPUT, ROOT, scan_adapters


def run(*arguments):
    result = subprocess.run([sys.executable, str(ROOT / "tools/obd_capture.py"), *arguments])
    if result.returncode:
        print("The attempt stopped. Any recorded evidence remains saved; check the message above.")


def main():
    print("eGauge Jeep capture\nRecordings are private and saved on this Mac.\n")
    while True:
        print("1. Capture engine port directly from the Vgate")
        print("2. Capture transmission port directly from the same Vgate")
        print("3. Record gauge USB while checking the Pixel and display")
        print("4. Run the offline recorder/replay check")
        print("5. Exit")
        choice = input("Choose a step: ").strip()
        if choice in ("1", "2"):
            source = "engine" if choice == "1" else "transmission"
            print(f"\nPlug the Vgate into the {source} port. Power off the gauge and close other OBD apps so this Mac can connect.")
            input("Turn ignition on, then press Return when ready. ")
            candidates = asyncio.run(scan_adapters())
            if not candidates:
                print("No candidate adapter found. Check power and macOS Bluetooth permission.")
                continue
            for i, (device, adv) in enumerate(candidates, 1):
                print(f"{i}. {adv.local_name or device.name or 'Unnamed adapter'} ({device.address})")
            try:
                index = int(input("Choose the physical Vgate number: ")) - 1
                if index < 0 or index >= len(candidates): raise ValueError()
            except ValueError:
                print("Selection cancelled.")
                continue
            run("ble", "--source", source, "--adapter", candidates[index][0].address, "--duration", "45")
            print("Capture closed its connection. Move the adapter only after this step has finished.\n")
        elif choice == "3":
            import serial.tools.list_ports
            ports = [p.device for p in serial.tools.list_ports.comports() if 'usb' in p.device.lower()]
            if not ports:
                print("No gauge USB serial port found. Connect its data cable.")
                continue
            port = ports[0] if len(ports) == 1 else input("Gauge USB serial path: ").strip()
            source = input("Which physical port holds the Vgate (engine/transmission)? ").strip()
            if source not in ("engine", "transmission"):
                print("Use the actual port name so evidence is attributed correctly.")
                continue
            print("Connect the capture firmware gauge, open eGauge on the Pixel, and observe the physical display. The Mac will record for two minutes.")
            input("Press Return when ready. ")
            run("serial", "--source", source, "--port", port, "--duration", "120")
        elif choice == "4":
            run("self-test")
        elif choice == "5":
            print(f"Recordings remain in {DEFAULT_OUTPUT}")
            return
        else:
            print("Choose 1 through 5.")


if __name__ == "__main__":
    try:
        main()
    except (KeyboardInterrupt, EOFError):
        print("\nSession closed. Saved recordings remain on the Mac.")
