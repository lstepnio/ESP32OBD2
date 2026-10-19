# JK upper-dash compact dome: TPU fit prototype 2

Status: dimensioned concept and test-print files, **not a qualified vehicle mount**.
This revision replaces the broad screw-mount base with a compact rounded dome and
an oval double-sided-tape foot. No hardware fit, tape bond, vehicle installation,
vibration or temperature testing has occurred. Intended for the bare Waveshare
board, not the optional CNC metal case. Previous geometry is retained in Git.

## Files and geometry

- `gauge-fit-coupon.stl`: print FIRST. A 5 mm-high perimeter seat and pressure-rib
  test. Inspect actual PCB/component clearance and never force or press the glass.
- `pod-prototype.stl`: 47 mm round bezel, curved rear dome and blended oval foot.
  Flat adhesive contact is 48 x 40 mm. There are no mounting holes or screw wings.
  The rear-to-front shell depth is 31 mm, down from 39 mm in revision 1.
- `rear-cover-converter-tray.stl`: smaller removable rear friction cap, with
  converter edge ledges, strap slots, cable exit and rounded pull tab. Print flat,
  exterior face down, as exported.
- `tape-template.svg`: 1:1 outline, inset 1 mm from the foot. Print at actual size
  with scaling disabled; verify a dimension with a ruler before cutting tape.
- `build.py`: editable parametric mesh CAD, dimensions in millimetres, inputs in
  `P`. This is not STEP or a complete component-level mechanical assembly.
- `parameters.json`, `mesh-checks.json`: generated parameters and actual STL sizes.
- `preview.png`: views rendered from the generated meshes, with simplified board,
  display and converter envelopes.

The face still points 20 degrees above horizontal. The converter lies behind the
gauge on a removable cover; its wiring assumes flexible soldered leads, not
upright header pins. The rear opening is smaller than the front bore, preserving
a curved shoulder. USB and top connector reliefs remain in the shell. No battery
pocket, drilling pattern, adhesive product or factory JK attachment is assumed.
The entire underside is flat for tape; a measured dash contour can replace it.

The gauge uses three small radial TPU ribs and a peripheral axial-stop ledge.
Retaining features do not cross the screen face. The seat and rib positions are
provisional until inspected against the actual PCB, glass and components.
The converter pocket has a center void beneath the PCB and exposed component
clearance. A removable insulated strap can use the slots only if its route clears
solder, traces and components; its retention is not physically qualified.

## Inputs and sources

| Input | Value | Confidence |
| --- | --- | --- |
| Waveshare lens OD / visible area | 38.51 +/- 0.05 / 33.40 +/- 0.1 mm | Vendor drawing |
| Face to PCB rear | 5.00 mm | Vendor drawing |
| Assembly height including USB | 41.0 mm | Vendor drawing, not circular PCB diameter |
| Circular PCB diameter | 38.6 mm | PROVISIONAL: measure actual board |
| Converter | Pololu D36V28F5, 17.8 x 20.3 x 8.8 mm | Manufacturer envelope |
| Board radial clearance / grip interference | 0.30 / 0.15 mm | Fit trial, printer/material dependent |
| Rear plug radial interference | 0.15 mm | Fit trial |
| Regulator pocket | 18.4 x 20.9 mm | 0.30 mm clearance per side |
| Gauge rear-component keepout depth | 8 mm | Assumed envelope, not measured |

Source references checked 2026-10-18:
- [Owner's converter listing](https://www.amazon.com/dp/B0BJKVWR2D)
- [Pololu dimensions, connections and thermal guidance](https://www.pololu.com/product/3782)
- [Waveshare resources](https://docs.waveshare.com/ESP32-S3-Touch-LCD-1.28/Resources-And-Documents)
- [Vendor mechanical PDF and STEP archive](https://files.waveshare.com/wiki/ESP32-S3-Touch-LCD-1.28/ESP32-S3-Touch-LCD-1.28.zip)

Vendor CAD remains external. Simplified envelopes and mesh intersection checks
are not full vendor-STEP collision analysis. Inspect PCB edge and seat contact,
antenna clearance, USB plug and regulator solder joints before final printing.

## Print, tape and fit sequence

1. Print the unpowered fit coupon in the intended TPU. TPU 95A is the initial
   assumption, not an approved universal filament. Start with 0.2 mm layers,
   4 walls, 5 top/bottom layers and 25-35% infill, using filament-maker temperatures.
   Support the PCB during insertion/removal, never the glass. Adjust grip and
   clearance rather than forcing a tight part.
2. Print the rear cover and trial the converter, strap and flexible wiring. The
   tray must not grip components or solder joints. Check insulation and removal.
3. Print the pod after the coupon fits. The curved shell and tilted bore require
   slicer review and accessible supports. Avoid supports on pressure ribs and the
   PCB seat. This is not a support-free or slicer-qualified design.
4. Trial on the dash while parked. Supply a photo with ruler, available dimensions
   and TPU hardness to tune the footprint, contour and viewing angle. A flat foot
   will not bond across a curved/textured surface just because the TPU can flex.
5. Use tape explicitly compatible with the actual printed TPU, dash plastic,
   surface texture and anticipated temperature. TPU and textured trim may bond
   poorly; no specific tape or primer is qualified by this design. Test a printed
   scrap first and check for trim damage before using cleaner, primer or adhesive.
   Keep the underside flat and free of support scars. Use the 1 mm-inset template
   for a broad contact patch; do not stack thick foam to bridge an unmeasured gap.
6. Follow the tape manufacturer's preparation, application and dwell instructions.
   Test parked hot-cabin creep, pull/peel resistance and touch-induced rocking
   before road/off-road use. The smaller base gives less bond area and leverage
   resistance than revision 1. Do not obstruct sightlines, vents or vehicle equipment.
7. Bench-power at intended load; measure converter/gauge temperature and check
   touch, USB access and wireless reception. Then qualify heat/vibration retention.
   The friction cap and grip ribs also need testing for TPU creep.

A vented cavity is not thermal qualification. Pololu's continuous-output rating
varies with thermal conditions. The enclosure does not establish automotive power
transient protection; qualify wiring and power protection separately.

## Rebuild and checks

Use an isolated Python 3.12 environment, separate from App/FW dependencies:

```sh
python3.12 -m venv /tmp/egauge-cad-env
/tmp/egauge-cad-env/bin/pip install -r mechanical/jk-dash-pod/requirements.txt
/tmp/egauge-cad-env/bin/python mechanical/jk-dash-pod/build.py
```

Generation removes sub-resolution boolean slivers, checks closed surfaces, positive
volume, consistent winding and one connected component before export, then reloads
each STL to check closure/connectivity. Simplified converter-to-shell and
converter-to-gauge-component keepouts are also checked. These tests do not certify
printing tolerances, components, structural strength, thermal performance or tape.
