# JK upper-dash eGauge pod: fit prototype 1

Status: dimensioned concept and test-print files, **not a qualified vehicle mount**.
No hardware fit, vehicle installation, vibration or temperature testing has occurred.
The housing is for the bare Waveshare board, not its optional CNC metal case.

## Files

- `gauge-fit-coupon.stl`: print FIRST. A 5 mm-high version of the perimeter seat and
  pressure ribs. Check the actual PCB edge, LCD stack and connector relief before
  inserting a powered assembly. Do not force the display or press the glass to insert.
- `pod-prototype.stl`: integrated round pod and generic flat dash foot.
- `rear-cover-converter-tray.stl`: removable friction cap with converter ledges,
  strap slots, cable exit, and pull tab. It is exported exterior face down at Z=0.
- `build.py`: editable parametric source. All dimensions are millimetres. `P` owns
  the principal dimensions. This is mesh CAD, not an exported STEP solid.
- `parameters.json`, `mesh-checks.json`: actual generated parameters and geometry checks.
- `preview.png`: assembly concept rendered from generated meshes with simplified
  board/display/converter envelopes. No simulated dashboard screenshot is implied.

## Design

The face points 20 degrees above horizontal (70 degrees from horizontal face plane).
The pod is 47 mm outside diameter; the base is 70 x 58 mm. The back-to-front shell
is 39 mm deep. The nominal PCB rim seat is 32 mm forward of the rear opening.
Three small radial TPU ribs grip the board perimeter; a peripheral ledge provides
an axial stop. The screen remains open and no retention feature crosses its face.
The converter sits behind the gauge on the removable cover with an air space
between the two electronic assemblies. There is no battery pocket.

The base currently has a flat underside and four optional 3.6 mm through holes
on a 56 x 32 mm pattern. These are **new mounting holes**, not claimed factory JK
fastener locations. There is no assumed vehicle drilling authorization. A flat
TPU foot is only an initial template for the top plastic above the radio stack;
its contour, viewing direction and attachment remain to be fitted to the Jeep.
The base is integrated in this first mesh; later angle/base adjustments regenerate
it from the parameters rather than an articulated joint.

## Dimensions and sources

| Input | Value | Confidence |
| --- | --- | --- |
| Waveshare lens outside diameter | 38.51 +/- 0.05 mm | Vendor drawing |
| Lens visible area | 33.40 +/- 0.1 mm | Vendor drawing |
| Face to PCB rear | 5.00 mm | Vendor drawing |
| Board assembly height including USB | 41.0 mm | Vendor drawing; not a circular PCB diameter |
| Circular PCB diameter | 38.6 mm | PROVISIONAL; measure actual board before final fit |
| Converter | Pololu D36V28F5, 17.8 x 20.3 x 8.8 mm | Manufacturer envelope |
| Board radial clearance | 0.30 mm | Prototype allowance |
| Rib radial interference | 0.15 mm | TPU fit trial, material/printer dependent |
| Rear plug radial interference | 0.15 mm | TPU fit trial |
| Converter edge pocket | 18.4 x 20.9 mm | 0.30 mm clearance each side |

Sources checked 2026-10-18:
- [Owner's converter listing](https://www.amazon.com/dp/B0BJKVWR2D)
- [Pololu manufacturer dimensions, connections and thermal guidance](https://www.pololu.com/product/3782)
- [Waveshare hardware resources](https://docs.waveshare.com/ESP32-S3-Touch-LCD-1.28/Resources-And-Documents)
- [Vendor mechanical archive with dimensioned PDF and STEP](https://files.waveshare.com/wiki/ESP32-S3-Touch-LCD-1.28/ESP32-S3-Touch-LCD-1.28.zip)

Vendor CAD remains external and is not redistributed here. The simplified envelopes
are not a full component collision check. PCB edge clearance at the selected ribs,
underside ledge, regulator strap and actual solder joints must be inspected. The
USB relief is provisional, particularly for a large molded USB-C plug or adapter.
Converter wiring assumes flexible soldered leads, not upright header pins.

## First print and fit sequence

1. Start with a small unpowered fit coupon in the intended TPU. TPU 95A is the
   initial geometry assumption, not a universal approved filament specification.
   Use the filament maker's printer and temperature settings. Starting slicing
   parameters: 0.2 mm layers, 4 walls, 5 top/bottom layers, 25-35% infill.
2. Support the PCB rather than the display during insertion/removal. Inspect every
   contact point. Adjust `pcb_diameter`, `clearance` and `radial_grip` before the
   full print if it bends the board, touches components, or offers weak retention.
3. Print the cap flat and trial the converter separately. Secure the converter with
   an insulated strap through the slots only if its route clears all components,
   solder and traces. Add insulation where required; TPU alone is not electrical
   qualification. Leave the component face unobstructed.
4. Print the pod only after fit approval. Its tilted bore has overhangs; review
   supports in the slicer and their removal access. Prefer manual support away
   from grip ribs, PCB seat and USB opening. The prototype is not support-free.
5. Trial the assembled housing on the dash while parked. Check driver view, touch,
   glare, cable routing, removal access, vents and vehicle equipment clearances.
   Provide a photo with ruler and available mounting dimensions before tailoring
   the underside or selecting the final attachment.
6. Bench-power at the intended load and test the enclosed regulator/gauge
   temperature, wireless reception and touch. Then test parked hot-cabin retention
   and vibration before relying on a road/off-road installation. TPU creep,
   compound-specific heat limits and friction change must be measured.

The manufacturer notes that converter continuous output depends on thermal
conditions. A vented pocket is not proof of adequate heat dissipation. Likewise,
this enclosure does not establish automotive transient protection for the power
circuit. Choose and verify wiring/protection separately before permanent power.

## Rebuild

Use a separate Python 3.12 environment. Do not add CAD dependencies to the App/FW environment.

```sh
python3.12 -m venv /tmp/egauge-cad-env
/tmp/egauge-cad-env/bin/pip install -r mechanical/jk-dash-pod/requirements.txt
/tmp/egauge-cad-env/bin/python mechanical/jk-dash-pod/build.py
```

The build checks each exported and reloaded STL for a closed surface and one
connected part, and checks positive volume and consistent winding before export.
These mesh checks do not certify printer tolerances, structural strength or fit.
