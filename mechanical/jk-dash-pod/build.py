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
         radial_grip=.15, outer_diameter=47., depth=39., pcb_seat=32.,
         foot_width=70., foot_depth=58., foot_thickness=3.,
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
# Local Z: rear service opening at 0, PCB rim seat at 32, open face at 39.
# 1.2 mm-wide peripheral seat; central electronic keepout stays empty.
body=cut(cyl(P['outer_diameter']/2,P['depth']),
         cyl(bore,P['depth']+2,-1))
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
body=cut(body,box([15,15,14],[0,-r-2,31]),
         box([27,12,12],[0,r+2,31]))
# Rear ventilation openings, outside the regulator support zone.
for x in (-10,0,10):
    body=cut(body,box([3,15,8],[x,r+3,11]))
# Rear plug retention: 2 mm solid cap with a 4 mm annular insertion skirt.
# 0.15 mm radial interference is provisional for TPU 95A.
cap=union(cyl(P['outer_diameter']/2,2,-2),
          cut(cyl(bore+.15,4,0),cyl(bore-1.4,6,-1)))
cap=union(cap,box([10,7,2],[0,r+3,-1])) # pull tab
# Converter tray uses only perimeter ledges, with an air void underneath.
# Direct-soldered wires assumed; headers require a larger envelope.
w=P['regulator_width'];l=P['regulator_length']
tray_outer=box([w+4,l+4,7.1],[0,0,3.45])
tray=cut(tray_outer,box([w+.6,l+.6,8],[0,0,7.5]),
         box([w-2,l-2,10],[0,0,4]),
         box([8,12,6],[0,l/2+2,6]))
# Bottom of ledges at z=2, PCB support at z=3.5; rear cap itself remains floor.
cap=union(cap,tray)
# Two narrow tie slots support a removable strap around converter PCB edges.
# Strap route must be checked against actual solder joints and components.
for x in (-w/2-1,w/2+1):
    cap=cut(cap,box([1.4,5,7],[x,0,1]))
# Cable exit and adjacent tie-down openings in rear service cap.
cap=cut(cap,placed(cyl(3.2,8,-4),[0,-13,0]))
for x in (-5,5):cap=cut(cap,box([2,4,5],[x,-13,-1]))
# Fixed 20-degree upward face. Foot is flat and intentionally not JK-contoured.
T=tm.transformations.rotation_matrix(np.deg2rad(90-P['face_elevation_deg']),[1,0,0])
T[:3,3]=[0,0,27]
body.apply_transform(T)
# Rounded 70 x 58 mm foot from a convex hull of corner cylinders.
corners=[placed(cyl(6,3),[x,y,0]) for x in (-29,29) for y in (-37,9)]
foot=tm.convex.convex_hull(tm.util.concatenate(corners))
# Saddle pier extends into lower shell only; no material enters the electronic bore.
saddle=box([24,20,9],[0,-9,6.5])
keepout=cyl(bore,P['pcb_seat']-1.2);keepout.apply_transform(T)
saddle=cut(saddle,keepout)
pod=union(body,foot,saddle)
# Optional M3 through-mount holes in exposed side wings, 56 x 32 mm pattern.
for x in (-28,28):
    for y in (-30,2):pod=cut(pod,placed(cyl(1.8,8,-2),[x,y,0]))
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
(OUT/'parameters.json').write_text(json.dumps(P,indent=2)+'\n')
# Assembly and cutaway previews use actual exported geometry.
cap_world=cap.copy();cap_world.apply_transform(T)
board=cyl(r,1.6,P['pcb_seat']);board.apply_transform(T)
glass=cyl(P['lens_diameter']/2,1.85,P['pcb_seat']+3.15);glass.apply_transform(T)
reg=box([w,l,P['regulator_height']],[0,0,3.5+P['regulator_height']/2]);reg.apply_transform(T)

fig=plt.figure(figsize=(15,5.8),facecolor='#eef1f3')
scenes=[([(pod,'#485b65'),(board,'#176252'),(glass,'#101b21'),(cap_world,'#647580')],-85,'Front / 20-degree upward view'),
        ([(pod,'#485b65'),(board,'#176252')],95,'Rear opening / cap removed'),
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
        ax.set(xlim=(-39,39),ylim=(-63,20),zlim=(0,66));ax.set_box_aspect((78,83,66));ax.view_init(23,azim)
    ax.set_axis_off();ax.set_title(title,fontsize=12)
fig.suptitle('eGauge JK dash pod | TPU fit prototype',fontsize=16)
fig.tight_layout();fig.savefig(OUT/'preview.png',dpi=150);plt.close(fig)
print(json.dumps(report,indent=2))
