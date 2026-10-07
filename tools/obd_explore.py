#!/usr/bin/env python3
"""Bounded Mac BLE exploration: standard IDs/reads and optional silent CAN.

No arbitrary request argument, VIN, enhanced PID sweep, calibration or writes.
Raw controller IDs and traffic remain in ignored private artifacts.
"""
import argparse
import asyncio
import re
import time
from pathlib import Path

from obd_capture import (AdapterSession, BLE_PROFILES, DEFAULT_OUTPUT, Recording,
                         choose_adapter, save_private)
from obd_capture_core import STANDARD_PIDS, allowed_command, support_replies, supports
from obd_temperature_candidate import COMMAND, decode_candidate
import obd_tcm_temperature_candidate as tcm_temperature
import obd_tcm_gear_candidate as tcm_gear

IDENTITY_COMMANDS = frozenset(('0900', '0904', '0906', '090A'))
MONITOR_SETUP = frozenset(('ATTP0', 'ATTP6', 'ATTP7', 'ATCSM1', 'ATCAF0', 'ATCAF1', 'ATCRA'))


def exploration_policy(command):
    return allowed_command(command) or command in IDENTITY_COMMANDS or command in MONITOR_SETUP


def candidate_policy(command):
    return exploration_policy(command) or command in (COMMAND, 'ATSH7E0', 'ATSH7DF')


def fault_policy(command):
    return exploration_policy(command) or command in ('03', '07', '0A', 'ATSH7E1', 'ATSH7DF')


def tcm_temperature_policy(command):
    return exploration_policy(command) or command in (tcm_temperature.COMMAND, 'ATSH7E1', 'ATSH7DF')


def tcm_temperature_v2_policy(command):
    return exploration_policy(command) or command in ('225043', 'ATSH7E1', 'ATSH7DF')


def tcm_temperature_v3_policy(command):
    return exploration_policy(command) or command in ('2204FE', 'ATSH7E1', 'ATSH7DF')


def tcm_gear_policy(command):
    return exploration_policy(command) or command in (*tcm_gear.COMMANDS, 'ATSH7E1', 'ATSH7DF')


async def tcm_gear_probe(session, result):
    """Two reads of each fixed published candidate at one owner-reported position."""
    if result.get('owner_reported_position') not in tcm_gear.POSITIONS:
        raise ValueError('A labelled selector position is required')
    try:
        await setup(session, ('ATSH7E1',))
        for _ in range(2):
            for command in tcm_gear.COMMANDS:
                raw = await session.request(command, timeout=5)
                sample = {'request': command, 'raw_hex': raw.hex(), 'qualified': False}
                try:
                    sample.update(tcm_gear.decode_candidate(raw, command))
                except ValueError as error:
                    sample.update(status='rejected', reason=str(error))
                result['samples'].append(sample)
                await asyncio.sleep(.5)
    finally:
        if not session.waiting_prompt:
            await setup(session, ('ATSH7DF',))


async def tcm_temperature_probe(session, result, command=tcm_temperature.COMMAND):
    """Three reads of a documented TCM route; preserve partial evidence on failure."""
    if command not in tcm_temperature.COMMANDS:
        raise ValueError('Unlisted temperature candidate')
    try:
        await setup(session, ('ATSH7E1',))
        for index in range(3):
            raw = await session.request(command, timeout=5)
            sample = {'raw_hex': raw.hex(), 'qualified': False}
            try:
                sample.update(tcm_temperature.decode_candidate(raw, command))
            except ValueError as error:
                sample.update(status='rejected', reason=str(error))
            result['samples'].append(sample)
            if index < 2:
                await asyncio.sleep(1)
    finally:
        if not session.waiting_prompt:
            await setup(session, ('ATSH7DF',))


async def fault_probe(session, result):
    """Read emissions-related fault categories from one physical TCM route."""
    try:
        await setup(session, ('ATSH7E1',))
        for command, category in (('03', 'stored'), ('07', 'pending'), ('0A', 'permanent')):
            reply = await session.request(command, timeout=5)
            result['samples'].append({'request': command, 'category': category, 'raw_hex': reply.hex()})
    finally:
        if not session.waiting_prompt:
            await setup(session, ('ATSH7DF',))


async def temperature_probe(session):
    """One published route, three bounded reads, no alternate identifiers or sessions."""
    samples = []
    try:
        await setup(session, ('ATSH7E0',))
        for index in range(3):
            raw = await session.request(COMMAND, timeout=5)
            sample = {'raw_hex': raw.hex(), 'qualified': False}
            try:
                sample.update(decode_candidate(raw))
            except ValueError as error:
                sample.update(status='rejected', reason=str(error))
            samples.append(sample)
            if index < 2:
                await asyncio.sleep(1)
    finally:
        if not session.waiting_prompt:
            await setup(session, ('ATSH7DF',))
    return {'request_id': '7E0', 'expected_response_id': '7E8', 'request': COMMAND,
            'qualified': False, 'samples': samples}


async def setup(session, commands):
    for command in commands:
        reply = await session.request(command)
        if b'OK' not in re.split(rb'[\r\n>]+', reply.upper()):
            raise ValueError(f'Adapter did not confirm {command}; stopped')


async def monitor(session, seconds=5, byte_limit=65536):
    """A separate trace channel keeps CAN broadcasts out of OBD transactions."""
    if session.waiting_prompt:
        raise ValueError('Unfinished diagnostic request before monitoring')
    while not session.rx.empty():
        session.rx.get_nowait()
    session.waiting_prompt = True
    session.recording.event('monitor_start', b'ATMA\r')
    await session.client.write_gatt_char(session.tx, b'ATMA\r', response=session.response)
    blocks = []
    received = 0
    deadline = time.monotonic() + seconds
    stopped = False
    truncated = False
    try:
        while time.monotonic() < deadline:
            try:
                raw = await asyncio.wait_for(session.rx.get(), deadline - time.monotonic())
            except asyncio.TimeoutError:
                break
            if session.rx_lost:
                raise ValueError('Monitor notification queue overflow')
            blocks.append(raw)
            received += len(raw)
            if b'>' in raw:
                stopped = True
                session.waiting_prompt = False
                break
            if received >= byte_limit:
                truncated = True
                break
    finally:
        if not stopped:
            # A single serial character stops ELM monitoring; it is not a vehicle request.
            session.recording.event('monitor_stop', b'\r')
            await session.client.write_gatt_char(session.tx, b'\r', response=session.response)
            end = time.monotonic() + 3
            while time.monotonic() < end:
                try:
                    raw = await asyncio.wait_for(session.rx.get(), end - time.monotonic())
                except asyncio.TimeoutError:
                    break
                if received < byte_limit:
                    blocks.append(raw)
                else:
                    truncated = True
                received += len(raw)
                if b'>' in raw:
                    session.waiting_prompt = False
                    stopped = True
                    break
        session.recording.event('monitor_end', status=0 if stopped else 1)
    if not stopped or session.rx_lost:
        raise ValueError('Monitor did not recover its prompt cleanly; disconnecting')
    raw = b''.join(blocks)
    adapter_buffer_full = b'BUFFER FULL' in raw.upper()
    return {'raw_hex': raw.hex(), 'bytes_received': received,
            'capture_limited': truncated or adapter_buffer_full,
            'adapter_buffer_full': adapter_buffer_full,
            'prompt_recovered': stopped}


async def explore(args):
    from bleak import BleakClient
    device = await choose_adapter(args.adapter)
    recording = Recording(args.output, args.source, 'mac_ble')
    policy = (tcm_gear_policy if args.tcm_gear_position else
              tcm_temperature_v3_policy if args.tcm_temperature_v3 else
              tcm_temperature_v2_policy if args.tcm_temperature_v2 else
              tcm_temperature_policy if args.tcm_temperature else fault_policy if args.tcm_faults
              else candidate_policy if args.hemi_temperature else exploration_policy)
    session = AdapterSession(recording, policy=policy)
    result = {'physical_port': args.source, 'identity': {}, 'monitor': [],
              'limit': 'Raw identity and CAN evidence; no enhanced transmission meaning inferred.'}
    monitoring = False

    def receive(characteristic, raw):
        if monitoring:
            session.recording.event('can_rx', bytes(raw))
            try:
                session.rx.put_nowait(bytes(raw))
            except asyncio.QueueFull:
                session.rx_lost = True
                session.recording.event('rx_overflow', status=1)
        else:
            session.notification(characteristic, raw)

    try:
        async with BleakClient(device, timeout=15,
                               disconnected_callback=lambda _: recording.event('disconnected')) as client:
            session.client = client
            gatt = {'name': device.name, 'identifier': device.address, 'services': [
                {'uuid': s.uuid, 'characteristics': [
                    {'uuid': c.uuid, 'properties': c.properties} for c in s.characteristics]}
                for s in client.services]}
            save_private(recording.directory / 'gatt.json', gatt)
            for service_uuid, tx_uuid, rx_uuid in BLE_PROFILES:
                service = client.services.get_service(service_uuid)
                if service:
                    tx, rx = service.get_characteristic(tx_uuid), service.get_characteristic(rx_uuid)
                    if tx and rx and 'notify' in rx.properties and any(
                            p in tx.properties for p in ('write', 'write-without-response')):
                        session.tx = tx
                        session.response = 'write' in tx.properties
                        break
            else:
                raise ValueError('No qualified BLE UART profile matches')
            await client.start_notify(rx, receive)
            recording.event('link_ready')
            print('Mac connected. Recording bounded controller reads.', flush=True)
            try:
                result['adapter_identity_hex'] = (await session.request('ATI')).hex()
                await setup(session, ('ATE0', 'ATL0', 'ATS0', 'ATH1', 'ATCAF1', 'ATSP0'))
                maps = {}
                for base in range(0, 0xE1, 0x20):
                    raw = await session.request(f'01{base:02X}', timeout=15 if base == 0 else 5)
                    maps[base] = support_replies(raw, base)
                    if not any(bits & 1 for bits in maps[base].values()):
                        break
                result['standard_support'] = maps
                result['protocol_hex'] = (await session.request('ATDP')).hex()
                result['protocol_number_hex'] = (await session.request('ATDPN')).hex()
                raw = await session.request('0900', timeout=8)
                result['identity']['0900'] = raw.hex()
                identity_maps = {0: support_replies(raw, 0, service=9)}
                result['identity_support'] = identity_maps
                for pid in (0x04, 0x06, 0x0A):
                    if supports(identity_maps, pid):
                        command = f'09{pid:02X}'
                        result['identity'][command] = (await session.request(command, timeout=8)).hex()
                for _ in range(3):
                    for pid in STANDARD_PIDS:
                        if supports(maps, pid):
                            await session.request(f'01{pid:02X}', timeout=5)
                            await asyncio.sleep(.15)
                save_private(recording.directory / 'diagnostic-discovery.json', result)
                if args.tcm_gear_position:
                    selected = bytes.fromhex(result['protocol_number_hex']).strip(b'\r\n >')
                    if selected not in (b'6', b'A6') or set(maps.get(0, {})) != {'7E9'}:
                        raise ValueError('TCM gear capture requires only 7E9 on 11-bit 500 kbit/s CAN')
                    result['tcm_gear_candidates'] = {
                        'owner_reported_position': args.tcm_gear_position,
                        'request_id': '7E1', 'expected_response_id': '7E9',
                        'qualified': False, 'samples': [],
                        'scope': 'Published current/target candidates; selector meaning unvalidated'}
                    print('Reading fixed gear candidates at owner-reported position ' + args.tcm_gear_position, flush=True)
                    await tcm_gear_probe(session, result['tcm_gear_candidates'])
                if args.tcm_temperature or args.tcm_temperature_v2 or args.tcm_temperature_v3:
                    selected = bytes.fromhex(result['protocol_number_hex']).strip(b'\r\n >')
                    if selected not in (b'6', b'A6') or set(maps.get(0, {})) != {'7E9'}:
                        raise ValueError('TCM temperature requires only 7E9 on 11-bit 500 kbit/s CAN')
                    command = ('2204FE' if args.tcm_temperature_v3 else
                               '225043' if args.tcm_temperature_v2 else tcm_temperature.COMMAND)
                    result['tcm_temperature_candidate'] = {
                        'request_id': '7E1', 'expected_response_id': '7E9',
                        'request': command, 'qualified': False,
                        'scope': ('OBDb Challenger candidate, not qualified for JSS' if args.tcm_temperature_v2 or args.tcm_temperature_v3
                                  else 'EcoDiesel documented candidate, not qualified for JSS'), 'samples': []}
                    print('Testing one published TCM temperature read; values remain unqualified.', flush=True)
                    await tcm_temperature_probe(session, result['tcm_temperature_candidate'], command)
                if args.tcm_faults:
                    selected = bytes.fromhex(result['protocol_number_hex']).strip(b'\r\n >')
                    if selected not in (b'6', b'A6') or '7E9' not in maps.get(0, {}):
                        raise ValueError('TCM faults require the observed 7E9 responder on 11-bit 500 kbit/s CAN')
                    result['tcm_faults'] = {'request_id': '7E1', 'expected_response_id': '7E9',
                                          'scope': 'Standard emissions-related DTCs only', 'samples': []}
                    print('Reading TCM stored, pending and permanent faults; no clearing.', flush=True)
                    await fault_probe(session, result['tcm_faults'])
                if args.hemi_temperature:
                    selected = bytes.fromhex(result['protocol_number_hex']).strip(b'\r\n >')
                    if selected not in (b'6', b'A6'):
                        raise ValueError('Temperature candidate requires the observed 11-bit 500 kbit/s protocol')
                    print('Testing the published Hemi temperature candidate; values remain unqualified.', flush=True)
                    result['temperature_candidate'] = await temperature_probe(session)
                if args.monitor:
                    for protocol in ('6', '7'):
                        await setup(session, (f'ATTP{protocol}', 'ATCSM1', 'ATCAF0', 'ATCRA'))
                        print(f'Silent 500 kbit/s CAN capture, protocol {protocol}, up to 5 seconds.', flush=True)
                        monitoring = True
                        try:
                            capture = await monitor(session)
                            capture['protocol'] = protocol
                            result['monitor'].append(capture)
                            if capture['adapter_buffer_full']:
                                print('Adapter buffer filled; saved a partial CAN sample.', flush=True)
                        finally:
                            monitoring = False
            finally:
                monitoring = False
                result['adapter_restored'] = False
                if not session.waiting_prompt:
                    try:
                        restore = ('ATSH7DF', 'ATCAF1', 'ATTP0') if args.tcm_gear_position or args.hemi_temperature or args.tcm_faults or args.tcm_temperature or args.tcm_temperature_v2 or args.tcm_temperature_v3 else ('ATCAF1', 'ATTP0')
                        await setup(session, restore)
                        result['adapter_restored'] = True
                    except (ValueError, OSError) as error:
                        result['restore_error'] = str(error)
                if not result['adapter_restored']:
                    print('Adapter state was not restored; unplug/replug it before using the gauge.', flush=True)
            await client.stop_notify(rx)
    finally:
        recording.close()
        save_private(recording.directory / 'exploration.json', result)
        print(f'Private exploration saved: {recording.directory}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', choices=('engine', 'transmission'), required=True)
    parser.add_argument('--adapter', required=True, help='Previously physically identified macOS BLE ID')
    parser.add_argument('--output', type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument('--monitor', action='store_true', help='Add bounded silent 11/29-bit CAN captures')
    parser.add_argument('--hemi-temperature', action='store_true',
                        help='Opt in to three unqualified published 229110 reads on 7E0 only')
    parser.add_argument('--tcm-faults', action='store_true',
                        help='Read standard stored/pending/permanent faults on 7E1 after confirming 7E9')
    parser.add_argument('--tcm-temperature', action='store_true',
                        help='Test three unqualified published 2208DF reads on 7E1 after confirming 7E9')
    parser.add_argument('--tcm-temperature-v2', action='store_true',
                        help='Test three unqualified OBDb Challenger 225043 reads on 7E1 after confirming 7E9')
    parser.add_argument('--tcm-temperature-v3', action='store_true',
                        help='Test three unqualified OBDb Challenger 2204FE reads on 7E1 after confirming 7E9')
    parser.add_argument('--tcm-gear-position', choices=tcm_gear.POSITIONS,
                        help='Capture fixed gear candidates at one owner-reported parked selector position')
    args = parser.parse_args()
    if args.tcm_gear_position and (args.source != 'transmission' or args.hemi_temperature or args.tcm_faults or args.monitor or args.tcm_temperature or args.tcm_temperature_v2 or args.tcm_temperature_v3):
        parser.error('--tcm-gear-position requires --source transmission and runs separately from other probes')
    if args.tcm_temperature_v3 and (args.source != 'transmission' or args.hemi_temperature or args.tcm_faults or args.monitor or args.tcm_temperature or args.tcm_temperature_v2):
        parser.error('--tcm-temperature-v3 requires --source transmission and runs separately from other probes')
    if args.tcm_temperature_v2 and (args.source != 'transmission' or args.hemi_temperature or args.tcm_faults or args.monitor or args.tcm_temperature):
        parser.error('--tcm-temperature-v2 requires --source transmission and runs separately from other probes')
    if args.tcm_temperature and (args.source != 'transmission' or args.hemi_temperature or args.tcm_faults or args.monitor):
        parser.error('--tcm-temperature requires --source transmission and runs separately from other probes')
    if args.hemi_temperature and args.source != 'engine':
        parser.error('--hemi-temperature uses the published engine-controller route; choose --source engine')
    if args.tcm_faults and (args.source != 'transmission' or args.hemi_temperature):
        parser.error('--tcm-faults requires --source transmission and excludes --hemi-temperature')
    asyncio.run(explore(args))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError) as error:
        raise SystemExit(str(error))
