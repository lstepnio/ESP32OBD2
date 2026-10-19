import pathlib,subprocess,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
class CompanionGattReadTest(unittest.TestCase):
    def test_production_callback_contract_reconstructs_long_values(self):
        with tempfile.TemporaryDirectory() as tmp:
            binary=str(pathlib.Path(tmp)/'gatt-read')
            subprocess.run(['cc','-std=c11','-Wall','-Wextra','-Werror','-fsanitize=address,undefined',
                '-Ifirmware/gauge/tests/stubs','-Ifirmware/gauge/main/inc',
                'firmware/gauge/tests/companion_gatt_read_test.c','-o',binary],cwd=ROOT,check=True)
            subprocess.run([binary],check=True,capture_output=True)
        source=(ROOT/'firmware/gauge/main/src/ble_companion.c').read_text()
        self.assertNotIn('+ ctxt->offset',source)
        self.assertNotIn('+ctxt->offset',source)
        self.assertEqual(21,source.count('return companion_gatt_read_value('))
