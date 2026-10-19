"""Prototype TPU pod. Millimetres. Run with requirements.txt in an isolated env."""
from pathlib import Path
import json
import numpy as np
import trimesh as tm
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection

OUT = Path(__file__).parent
# Vendor drawing: lens OD 38.51, lens VA 33.40, face-to-PCB rear 5.00,
# overall height with USB 41.0. PCB diameter below is a fit-test assumption.
P = dict(pcb_diameter=38.6, lens_diameter=38.51, clearance=.30,
         radial_grip=.15, outer_diameter=47., depth=31., pcb_seat=24.,
         foot_width=48., foot_depth=40., foot_thickness=1.5,
         service_radius=16.2, dome_center=15.5,
         face_elevation_deg=20., regulator_width=17.8, regulator_length=20.3,
         regulator_height=8.8)

def box(size, pos):
    m=tm.creation.box(size); m.apply_translation(pos); return m

def cyl(r,h,z=0,sections=160):
    m=tm.creation.cylinder(radius=r,height=h,sections=sections)
    m.apply_translation([0,0,z+h/2]); return m

def union(*meshes): return tm.boolean.union(list(meshes),engine='manifold')
def cut(m,*tools): return tm.boolean.difference([m,*tools],engine='manifold')

def placed(m, pos):
    m=m.copy();m.apply_translation(pos);return m

r=P['pcb_diameter']/2
bore=r+P['clearance']
def ellipsoid(radii, pos):
    m=tm.creation.icosphere(subdivisions=4,radius=1)
    m.apply_scale(radii);m.apply_translation(pos);return m

# Curved rear dome and a 1.5 mm rounded front bezel, open at both ends.
R=P['outer_diameter']/2; center=P['dome_center']; depth=P['depth']
profile=[[0,0]]
for z in np.linspace(0,center,40):
    profile.append([np.sqrt(R*R-(z-center)**2),z])
profile.append([R,depth-1.5])
for a in np.linspace(0,np.pi/2,14)[1:]:
    profile.append([R-1.5+1.5*np.cos(a),depth-1.5+1.5*np.sin(a)])
profile.append([0,depth])
outer=tm.creation.revolve(np.array(profile),sections=160)
# Full electronic cavity plus narrow rear converter-service opening.
inner=union(ellipsoid([21,21,21],[0,0,center]),
            cyl(bore,depth,11),cyl(P['service_radius'],7,-1))
body=cut(outer,inner)
seat=cut(cyl(bore+.15,1.2,P['pcb_seat']-1.2),
         cyl(r-1.2,3,P['pcb_seat']-2))
body=union(body,seat)
# Three small pressure ribs on the assumed PCB perimeter, never across glass.
# Top connector/button and bottom USB sectors are intentionally left open.
for deg in (0,155,205):
    a=np.deg2rad(deg)
    pad=box([1.0,3.2,1.6],[r+.5-P['radial_grip'],0,P['pcb_seat']+.8])
    pad.apply_transform(tm.transformations.rotation_matrix(a,[0,0,1]))
    body=union(body,pad)
# Generous USB cable/service opening through lower wall and rim seat.
body=cut(body,box([15,15,14],[0,-r-2,P['pcb_seat']-1]),
         box([27,12,12],[0,r+2,P['pcb_seat']-1]))
# Rear ventilation openings, outside the regulator support zone.
for x in (-10,0,10):
    body=cut(body,box([3,15,6],[x,r+3,14]))
# Small rear cover, with a short TPU friction skirt and rounded pull tab.
service=P['service_radius']
rear_radius=np.sqrt(R*R-center*center)
cap=union(cyl(rear_radius,2,-2),
          cut(cyl(service+.15,2,0),cyl(service-1.3,4,-1)))
cap=union(cap,placed(cyl(4,2,-2),[0,rear_radius-1,0]))
# Converter tray uses only perimeter ledges, with an air void underneath.
# Direct-soldered wires assumed; headers require a larger envelope.
w=P['regulator_width'];l=P['regulator_length']
tray_outer=box([w+3,l+3,7.1],[0,0,3.45])
tray=cut(tray_outer,box([w+.6,l+.6,8],[0,0,7.5]),
         box([w-2,l-2,10],[0,0,4]),
         box([8,12,6],[0,l/2+2,6]))
# PCB edge support is at z=3.5; the rear cap is the floor.
cap=union(cap,tray)
# Two narrow tie slots support a removable strap around converter PCB edges.
# Strap route must be checked against actual solder joints and components.
for x in (-w/2-1,w/2+1):
    cap=cut(cap,box([1.4,5,7],[x,0,1]))
# Cable exit and adjacent tie-down openings in rear service cap.
cap=cut(cap,placed(cyl(3.2,8,-4),[0,-13,0]))
for x in (-5,5):cap=cut(cap,box([2,4,5],[x,-13,-1]))
# Fixed upward face with a soft oval adhesive foot. No drilling holes.
T=tm.transformations.rotation_matrix(np.deg2rad(90-P['face_elevation_deg']),[1,0,0])
T[:3,3]=[0,0,24.5]
body.apply_transform(T)
fw=P['foot_width'];fd=P['foot_depth'];ft=P['foot_thickness']
foot=cyl(1,ft);foot.apply_scale([fw/2,fd/2,1]);foot.apply_translation([0,-9,0])
# Smooth convex top surface over a full, flat tape contact surface at Z=0.
foot_round=ellipsoid([fw/2,fd/2,2.5],[0,-9,ft])
foot_round=tm.boolean.intersection([foot_round,box([60,60,8],[0,-9,ft+4])],engine='manifold')
foot=union(foot,foot_round)
saddle=ellipsoid([18,16,10],[0,-9,6])
saddle=tm.boolean.intersection([saddle,box([60,60,30],[0,-9,15])],engine='manifold')
keepout=inner.copy();keepout.apply_transform(T)
saddle=cut(saddle,keepout)
foot=cut(foot,keepout)
pod=union(body,foot,saddle)
# Fit coupon: same seat and ribs, short enough to inspect from both sides.
coupon=union(cut(cyl(P['outer_diameter']/2,5),cyl(bore,7,-1)),
             cut(cyl(bore+.15,1.2),cyl(r-1.2,3,-1)))
for deg in (0,155,205):
    pad=box([1,3.2,1.6],[r+.5-P['radial_grip'],0,2])
    pad.apply_transform(tm.transformations.rotation_matrix(np.deg2rad(deg),[0,0,1]))
    coupon=union(coupon,pad)
coupon=cut(coupon,box([15,15,7],[0,-r-2,2.5]),box([27,12,5],[0,r+2,3.7]))
# Cap exported flat, outside down on print bed.
cap_print=cap.copy();cap_print.apply_translation([0,0,2])
parts={'pod-prototype':pod,'rear-cover-converter-tray':cap_print,'gauge-fit-coupon':coupon}
report={}
for name,m in parts.items():
    # Remove boolean slivers that collapse at STL's float32 resolution.
    m.vertices=m.vertices.astype(np.float32).astype(np.float64)
    m.merge_vertices(digits_vertex=5)
    m.update_faces(m.nondegenerate_faces())
    m.remove_unreferenced_vertices()
    assert m.is_watertight and m.is_winding_consistent and m.volume>0,name
    assert len(m.split())==1,(name,'disconnected geometry')
    m.export(OUT/(name+'.stl'))
    reloaded=tm.load_mesh(OUT/(name+'.stl'))
    assert reloaded.is_watertight and len(reloaded.split())==1,name
    report[name]={'bounds_mm':np.round(m.bounds,3).tolist(),
                 'size_mm':np.round(m.extents,3).tolist(),
                 'volume_cm3':round(m.volume/1000,2),'watertight':True,
                 'components':1,'triangles':len(m.faces)}
(OUT/'mesh-checks.json').write_text(json.dumps(report,indent=2)+'\n')
(OUT/'tape-template.svg').write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{fw}mm" height="{fd}mm" viewBox="0 0 {fw} {fd}">'
    f'<ellipse cx="{fw/2}" cy="{fd/2}" rx="{fw/2-1}" ry="{fd/2-1}" fill="none" stroke="black" stroke-width="0.2"/></svg>\n')
(OUT/'parameters.json').write_text(json.dumps(P,indent=2)+'\n')
# Reject interference with the assumed converter envelope and component keepout.
reg_local=box([w,l,P['regulator_height']],[0,0,3.5+P['regulator_height']/2])
reg_world=reg_local.copy();reg_world.apply_transform(T)
shell_overlap=tm.boolean.intersection([pod,reg_world],engine='manifold')
assert not len(shell_overlap.faces) or shell_overlap.volume < .001
components=cyl(17,8,P['pcb_seat']-8);components.apply_transform(T)
component_overlap=tm.boolean.intersection([components,reg_world],engine='manifold')
assert not len(component_overlap.faces) or component_overlap.volume < .001
# These conservative envelopes do not replace the vendor component STEP model.
# Assembly previews use actual exported geometry.
cap_world=cap.copy();cap_world.apply_transform(T)
board=cyl(r,1.6,P['pcb_seat']);board.apply_transform(T)
glass=cyl(P['lens_diameter']/2,1.85,P['pcb_seat']+3.15);glass.apply_transform(T)
reg=box([w,l,P['regulator_height']],[0,0,3.5+P['regulator_height']/2]);reg.apply_transform(T)

fig=plt.figure(figsize=(15,5.8),facecolor='#eef1f3')
scenes=[([(pod,'#485b65'),(board,'#176252'),(glass,'#101b21'),(cap_world,'#647580')],-85,'Rounded dome / tape mounting'),
        ([(pod,'#485b65'),(board,'#176252')],95,'Compact rear service opening'),
        ([(cap,'#647580'),(box([w,l,P['regulator_height']],[0,0,3.5+P['regulator_height']/2]),'#db9c2c')],-65,'Rear cover / converter envelope')]
for i,(items,azim,title) in enumerate(scenes):
    ax=fig.add_subplot(1,3,i+1,projection='3d');ax.set_facecolor('#eef1f3')
    triangles=np.concatenate([m.triangles for m,c in items])
    colors=sum(([c]*len(m.faces) for m,c in items),[])
    ax.add_collection3d(Poly3DCollection(triangles,facecolors=colors,linewidths=0,shade=True,
                                       lightsource=matplotlib.colors.LightSource(azdeg=315,altdeg=45)))
    if i==2:
        ax.set(xlim=(-28,28),ylim=(-28,28),zlim=(-2,18));ax.set_box_aspect((56,56,20));ax.view_init(45,azim)
    else:
        ax.set(xlim=(-28,28),ylim=(-42,20),zlim=(0,60));ax.set_box_aspect((56,62,60));ax.view_init(23,azim)
    ax.set_axis_off();ax.set_title(title,fontsize=12)
fig.suptitle('eGauge JK compact dome | TPU + double-sided tape fit prototype',fontsize=16)
fig.tight_layout();fig.savefig(OUT/'preview.png',dpi=150);plt.close(fig)
print(json.dumps(report,indent=2))
