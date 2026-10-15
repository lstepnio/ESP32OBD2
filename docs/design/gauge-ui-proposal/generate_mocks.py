"""Review-only 240 px gauge concepts. Uses production Noto fonts, not LVGL rendering."""
from pathlib import Path
from math import hypot
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
S = 3
BG = '#05080A'; WHITE = '#E4EAED'; MUTED = '#9CAAB2'
TRACK = '#1B2A30'; GREEN = '#00FF70'; AMBER = '#FF8A00'; RED = '#FF1744'
FONT = ROOT / 'firmware/gauge/assets/fonts'
rows = []
ROUND = 3
PREVIOUS_ROUND = 2
CHANGES = {
    'bar': 'Reserved the lower alert row by raising the 6 px scale, with stable geometry when an alert appears.',
    'trend': 'Raised the reading and chart together to reserve a separate lower alert row without alert-time reflow.',
    'threshold': 'Moved the warning symbol, condition and direction below the reading, replacing page dots.',
    'threshold-mil': 'Kept CEL and its count at the top; moved the warning condition below both readings.',
    'critical-acked': 'Moved the acknowledged critical indicator below the reading; its condition remains active.',
    'last-seen': 'Moved the retained last-seen warning below the reading, with its affected condition named.',
    'warning-simulated': 'Placed the warning below the reading while retaining a separate quiet Demo footer.',
}

class Gauge:
    def __init__(self):
        self.im = Image.new('RGB', (240*S,240*S), BG)
        self.d = ImageDraw.Draw(self.im)
        self.bounds = []
        self.shapes = []
    def shape(self,name,box):
        assert all(hypot(x-120,y-120)<=104 for x in (box[0],box[2]) for y in (box[1],box[3])), (name,box)
        self.shapes.append({'shape':name,'bounds':list(box)})
    def font(self,size,bold=False):
        return ImageFont.truetype(str(FONT / ('NotoSans-SemiBold.ttf' if bold else 'NotoSans-Medium.ttf')), round(size*S))
    def rect(self,box,fill,outline=None,r=0,width=1):
        xy=tuple(round(v*S) for v in box)
        self.d.rounded_rectangle(xy,round(r*S),fill=fill,outline=outline,width=round(width*S))
    def line(self,points,fill,width=1):
        self.d.line([(round(x*S),round(y*S)) for x,y in points],fill=fill,width=round(width*S))
    def text(self,text,y,size=16,color=WHITE,x=120,bold=False,max_width=176,minimum=16):
        f=self.font(size,bold)
        while size>minimum:
            bb=self.d.textbbox((0,0),text,font=f)
            ww=(bb[2]-bb[0])/(2*S);hh=(bb[3]-bb[1])/(2*S)
            safe=all(hypot(xx-120,yy-120)<=104 for xx in (x-ww,x+ww) for yy in (y-hh,y+hh))
            if f.getlength(text)<=max_width*S and safe: break
            size=next((v for v in (64,48,40,32,24,16) if minimum<=v<size), minimum)
            f=self.font(size,bold)
        b=self.d.textbbox((0,0),text,font=f)
        w=b[2]-b[0];h=b[3]-b[1]
        px=x*S-w/2-b[0];py=y*S-h/2-b[1]
        self.d.text((round(px),round(py)),text,font=f,fill=color)
        box=(x-w/(2*S),y-h/(2*S),x+w/(2*S),y+h/(2*S))
        assert w/S<=max_width+1, (text,'width',w/S)
        assert all(hypot(xx-120,yy-120)<=104 for xx in (box[0],box[2]) for yy in (box[1],box[3])), (text,box)
        self.bounds.append({'text':text,'fontPx':size,'bounds':[round(v,2) for v in box]})
    def engine(self,x,y,c=AMBER,scale=1):
        self.shape('engine',(x-17*scale,y-10*scale,x+13*scale,y+11*scale))
        def points(raw): return [(x+dx*scale,y+dy*scale) for dx,dy in raw]
        self.line(points([(-12,-6),(-4,-6),(-4,-9),(5,-9),(5,-5),(12,-5),(12,7),(5,7),(2,10),(-6,10),(-9,6),(-12,6),(-12,-6)]),c,2*scale)
        self.line(points([(-16,-3),(-16,5)]),c,2*scale)
        self.line(points([(-16,1),(-12,1)]),c,2*scale)
    def triangle(self,x,y,c=AMBER,scale=1):
        self.shape('warning',(x-11*scale,y-10*scale,x+11*scale,y+9*scale))
        def points(raw):return [(x+dx*scale,y+dy*scale) for dx,dy in raw]
        self.line(points([(0,-9),(10,8),(-10,8),(0,-9)]),c,2*scale)
        self.line(points([(0,-3),(0,2)]),c,2*scale)
        self.rect((x-scale,y+5*scale,x+scale,y+7*scale),c,r=scale)
    def diamond(self,x,y,c=RED,scale=1):
        self.shape('critical',(x-11*scale,y-11*scale,x+11*scale,y+11*scale))
        def points(raw):return [(x+dx*scale,y+dy*scale) for dx,dy in raw]
        self.line(points([(0,-10),(10,0),(0,10),(-10,0),(0,-10)]),c,2*scale)
        self.line(points([(0,-4),(0,1)]),c,2*scale)
        self.rect((x-scale,y+4*scale,x+scale,y+6*scale),c,r=scale)
    def wrench(self,x,y,c=MUTED):
        self.shape('recorded faults',(x-10,y-9,x+12,y+10))
        self.line([(x-8,y+8),(x+6,y-6)],c,3)
        self.line([(x+6,y-6),(x+10,y-3),(x+6,y+2),(x+2,y-2),(x+5,y-7)],c,2)
    def status(self,kind='mil',text='4'):
        """The top lane belongs to diagnostic lamp/coverage status."""
        c=AMBER if kind=='mil' else MUTED
        if kind in ('mil','stored'):
            counted=text not in ('0','1','')
            x=110 if counted else 120
            if kind=='mil': self.engine(x,42,c)
            else: self.wrench(x,42,c)
            if counted:self.text(text,42,16,c,x=137,max_width=34)
        elif kind=='unknown':self.text('Not checked',42,16,c,max_width=110)
        else:raise ValueError('Use the lower alert row for reading alerts.')
    def alert(self,alias='Coolant',kind='warn',direction='up',more=0):
        """One bounded condition below readings; page dots yield to active alerts."""
        y=194;c=RED if kind=='critical' else AMBER
        width=self.font(16).getlength(alias)/S
        count='+'+str(more) if more else ''
        count_width=self.font(16).getlength(count)/S if count else 0
        total=18+5+width+(5+8 if direction else 0)+(6+count_width if count else 0)
        assert total<=124, ('Author a shorter alert alias',alias,total)
        self.rect((48,183,192,222),BG)
        x=120-total/2
        if kind=='critical':self.diamond(x+9,y,c,scale=.8)
        else:self.triangle(x+9,y,c,scale=.8)
        x+=23;self.text(alias,y,16,c,x=x+width/2,max_width=width+1);x+=width
        if direction:
            assert direction in ('up','down')
            x+=9;dy=1 if direction=='up' else -1
            self.shape('direction',(x-4,y-7,x+4,y+7))
            self.line([(x,y+6*dy),(x,y-6*dy),(x-3,y-2*dy)],c,2)
            self.line([(x,y-6*dy),(x+3,y-2*dy)],c,2);x+=4
        if count:self.text(count,y,16,c,x=x+6+count_width/2,max_width=count_width+1)
    def demo(self):
        # Normal data, a missing adapter and the phone preview do not enable this.
        self.rect((96,205,144,221),BG)
        self.text('Demo',213,16,AMBER,max_width=48)
    def dots(self,n=0):
        for i in range(3): self.rect((110+i*10,210,114+i*10,214),WHITE if i==n else TRACK,r=2)
    def numeric(self,label='RPM',value='675',unit='',size=64,positions=(87,126,169)):
        self.text(label,positions[0],16,MUTED,max_width=166)
        self.text(value,positions[1],size,WHITE,bold=True,minimum=32)
        if unit: self.text(unit,positions[2],24,MUTED,max_width=140)
        self.dots()
    def dual_row(self,label,value,unit,y,unavailable=False):
        self.text(label,y-25,16,MUTED,max_width=166)
        size=40;f=self.font(size,True);uf=self.font(16)
        while f.getlength(value)+(uf.getlength(unit)+6*S if unit else 0)>176*S and size>32:
            size=32;f=self.font(size,True)
        v=f.getlength(value)/S;u=(uf.getlength(unit)/S+6 if unit else 0)
        self.text(value,y,size,MUTED if unavailable else WHITE,x=120-u/2,bold=True,max_width=176-u,minimum=32)
        if unit:self.text(unit,y+7,16,MUTED,x=120+v/2+3,max_width=44)
    def dual(self,missing=None):
        self.dual_row('Gear','--' if missing in ('TCM','both') else 'P','',97,missing in ('TCM','both'))
        self.line([(50,120),(190,120)],TRACK)
        self.dual_row('RPM','--' if missing in ('ECM','both') else '675','',160,missing in ('ECM','both'))
        self.dots(1)
    def chart(self,renderer,value='112'):
        if renderer=='arc':
            self.d.arc((11*S,11*S,229*S,229*S),135,405,TRACK,11*S)
            self.d.arc((11*S,11*S,229*S,229*S),135,322,GREEN,11*S)
        if renderer=='trend':
            self.text('Coolant',72,16,MUTED);self.text(value,102,48,bold=True);self.text('°C',132,16,MUTED)
            self.shape('trend chart',(48,144,192,178))
            self.line([(48,177),(192,177)],TRACK)
            self.line([(48,173),(65,170),(82,171),(99,163),(116,165),(133,160),(150,158),(167,150),(192,146)],GREEN,3)
            self.dots(2)
        else:
            self.numeric('Coolant',value,'°C',positions=(70,108,147) if renderer=='bar' else (87,126,169))
            if renderer=='bar':
                self.shape('bar scale',(50,174,190,180))
                self.rect((50,174,190,180),TRACK,r=3);self.rect((50,174,145,180),GREEN,r=3)
    def button(self,text='Back'):
        self.rect((56,168,184,200),TRACK,r=16)
        self.text(text,184,16,WHITE,max_width=114)
    def critical(self,label='Coolant',value='118',unit='°C',detail='Limit 110 °C'):
        self.text('CRITICAL',48,16,RED,max_width=110,bold=True)
        self.text(label,77,16,MUTED)
        self.text(value+' '+unit,118,48,WHITE,bold=True,minimum=32)
        self.text(detail,151,16,MUTED,max_width=160)
        self.button('Acknowledge')
    def system(self,title,lines,code=None,identifier=False,button=None):
        self.text(title,75,24,WHITE,max_width=174)
        if code:self.text(code,118,48,WHITE,bold=True,minimum=40)
        else:
            for i,line in enumerate(lines): self.text(line,111+i*24,16,MUTED,max_width=172)
        if identifier:self.text('ID ABC123',186,16,MUTED,max_width=112)
        if button:self.button(button)
    def finish(self,slug,title,category,note):
        elements=[b for b in self.bounds if b['text']]+self.shapes
        for i,a in enumerate(elements):
            for b in elements[i+1:]:
                ax,ay,ar,ab=a['bounds'];bx,by,br,bb=b['bounds']
                assert not (ax<br and bx<ar and ay<bb and by<ab), ('overlap',slug,a,b)
        im=self.im.resize((240,240),Image.Resampling.LANCZOS)
        mask=Image.new('L',(240,240));ImageDraw.Draw(mask).ellipse((0,0,239,239),fill=255)
        rgba=im.convert('RGBA');rgba.putalpha(mask);rgba.save(OUT/(slug+'.png'))
        prior=OUT/f'v{PREVIOUS_ROUND}'/(slug+'.png')
        revised=not prior.exists() or prior.read_bytes()!=(OUT/(slug+'.png')).read_bytes()
        rows.append({'revised':revised,'isNew':not prior.exists(),'changeSummary':CHANGES.get(slug,'New lower-alert-row example.' if not prior.exists() else 'Unchanged from revision 2.'),'id':slug,'title':title,'category':category,'note':note,'bounds':self.bounds,'essentialShapes':self.shapes})

for renderer in ('numeric','arc','bar','trend','dual'):
    g=Gauge()
    if renderer=='dual':g.dual()
    elif renderer in ('trend','bar'):g.chart(renderer,'92')
    else:
        if renderer=='arc':
            g.d.arc((11*S,11*S,229*S,229*S),135,405,TRACK,11*S)
            g.d.arc((11*S,11*S,229*S,229*S),135,322,GREEN,11*S)
        g.numeric('RPM' if renderer=='numeric' else 'Coolant','675' if renderer=='numeric' else '92','' if renderer=='numeric' else '°C',positions=(70,108,147) if renderer=='bar' else (87,126,169))
    g.finish(renderer,renderer.title(),'Reading layouts','Normal view; no routine status sentence or brand footer.')

g=Gauge();g.status();g.dual();g.finish('dual-mil','Dual + engine warning','Faults and alerts','Unboxed engine icon higher on the display, with a small count for multiple known codes; code text stays in inspection.')
g=Gauge();g.status('stored','2');g.numeric();g.finish('stored','Stored codes, lamp off','Faults and alerts','Muted wrench distinguishes recorded codes from a lit warning lamp.')
g=Gauge();g.status('mil','?');g.numeric();g.finish('mil-unknown-count','MIL on, count unknown','Faults and alerts','An unknown count never becomes zero or a false all-clear.')
g=Gauge();g.numeric('Coolant','112','°C');g.alert();g.finish('threshold','Warning threshold','Faults and alerts','Reading first; one compact warning condition below. The lower alert row replaces page dots.')
g=Gauge();g.status();g.dual();g.alert();g.finish('threshold-mil','Threshold + MIL','Faults and alerts','CEL and fault count stay at the top; Coolant warning stays below both readings, including when its PID is on another page.')
g=Gauge();g.critical();g.finish('critical','Critical attention','Faults and alerts','One takeover: condition, value, limit and acknowledgment. No background labels bleed through.')
g=Gauge();g.numeric('Coolant','118','°C');g.alert(kind='critical');g.finish('critical-acked','Acknowledged critical','Faults and alerts','Acknowledgment returns readings; the active critical condition stays red in the lower row and is not cleared.')
g=Gauge();g.text('Engine',69,16,MUTED);g.text('U0121',113,40,bold=True);g.text('Stored  1/4',150,16,MUTED);g.button();g.finish('fault-view','Fault inspection','Faults and alerts','Proposed optional status tap; one code at a time with controller and category, swipes move codes.')
g=Gauge();g.numeric();g.alert('ATF last seen',direction=None);g.finish('last-seen','Warning source unavailable','Faults and alerts','ATF warning input is missing while RPM remains live. Its retained last-seen condition stays below the reading.')
g=Gauge();g.status('unknown');g.numeric();g.finish('not-checked','Diagnostic coverage unknown','Faults and alerts','Proposed neutral coverage state, with no green healthy claim.')

for source in ('ECM','TCM'):
    g=Gauge();g.dual(source);g.finish('missing-'+source.lower(),'Missing '+source+' source','Limits and custom PIDs','Each row expires independently; the other reading remains usable.')
g=Gauge();g.numeric('Coolant','--','°C');g.finish('missing','Reading unavailable','Limits and custom PIDs','Dashes indicate absent, invalid or stale input. Arc/bar become inactive; trend shows a gap.')
g=Gauge();g.numeric('Input speed','16383','rpm');g.finish('long-value','Five-digit value','Limits and custom PIDs','48 px value fits completely; no clipping, scrolling or automatic digit truncation.')
g=Gauge();g.numeric('Fuel rail pressure','1498.25','psi',40);g.finish('custom-pressure','Custom decimal PID','Limits and custom PIDs','Metadata selects precision and unit; font selection uses measured glyph bounds.')
g=Gauge();g.dual_row('Ratio','1.235','',97);g.line([(50,120),(190,120)],TRACK);g.dual_row('Transmission','LOCKED','',160);g.dots(1);g.finish('custom-enum','Custom numeric + enum','Limits and custom PIDs','Bounded enum strings and numeric formats use the same row component.')
g=Gauge();g.numeric('Ignition timing','-12.5','°',48);g.finish('negative','Negative and fractional','Limits and custom PIDs','Sign and decimal precision remain intact.')
g=Gauge();g.numeric();g.demo();g.finish('simulated','Simulated data','System and gestures','Deliberate bench/demo source, with a small Demo footer and no simulation pill. Missing vehicle data never becomes demo data.')
g=Gauge();g.system('Pair gauge',['Open eGauge App'],identifier=True);g.finish('pair-ready','Pairing ready','System and gestures','One instruction; identifying suffix retained for selecting the right gauge.')
g=Gauge();g.system('Pair gauge',['Hold to pair'],identifier=True);g.finish('pair-waiting','Pairing window closed','System and gestures','Existing hold gesture remains; no engineering owner-state sentence.')
g=Gauge();g.system('Pairing code',[],code='123 456',identifier=True);g.finish('passkey','Passkey entry','System and gestures','Six digits fit at 48 px, grouped for readability; ID occupies a separate row.')
g=Gauge();g.system('Phone paired',[''],button='Done');g.finish('paired','Pairing success','System and gestures','Brief confirmation then automatic return; Done is optional, not required.')
g=Gauge();g.system('Forget phone?',['Pairing will be','removed'],button='Forget');g.finish('reset','Owner reset confirmation','System and gestures','Review deliberate reset separately; explicit target, timeout/cancel behavior retained.')
g=Gauge();g.system('Updating',['Do not power off']);g.text('64%',157,32,bold=True);g.finish('update','Update maintenance','System and gestures','Proposed maintenance view only while an actual update is active, not for routine phone reads.')
g=Gauge();g.system('Checking update',['Please wait']);g.finish('checking','Post-update confirmation','System and gestures','Transfer and healthy activation stay separate; automatic return after verification.')
g=Gauge();g.system('Update paused',['Open eGauge App']);g.finish('update-paused','Actionable update pause','System and gestures','Concise next action; technical failure remains in the App.')
g=Gauge();g.system('No pages',['Add a page','in eGauge']);g.finish('empty','Empty dashboard','System and gestures','Only for an actual empty dashboard, never an adapter disconnect.')
g=Gauge();g.numeric();g.text('2/3',54,16,GREEN,max_width=104);g.finish('gesture','Page shortcut progress','System and gestures','Temporary progress uses the status lane only when no higher-priority state owns it.')
g=Gauge();g.numeric();g.text('Page changed',54,16,GREEN,max_width=122);g.finish('gesture-done','Shortcut complete','System and gestures','Brief feedback returns to the shared lane; it cannot cover a fault.')

g=Gauge();g.text('CRITICAL',48,16,RED,bold=True);g.text('Coolant',77,16,MUTED);g.text('--',118,48,MUTED,bold=True);g.text('Last seen 118 °C',151,16,MUTED);g.button('Acknowledge');g.finish('critical-missing','Critical input lost','Faults and alerts','Severity remains active; last observed value is labelled and never presented as fresh.')
g=Gauge();g.d.arc((11*S,11*S,229*S,229*S),135,405,TRACK,8*S);g.d.arc((11*S,11*S,229*S,229*S),135,315,AMBER,8*S);g.triangle(120,67,AMBER);g.text('Road hazard',112,24,WHITE);g.button('Back');g.finish('provider','Future phone/provider alert','Faults and alerts','Countdown arc illustrates 20 of 30 seconds remaining and drains toward expiry. No Paired phone or Expires in text. Source details stay in App context; local critical alerts retain priority.')
g=Gauge();g.system('Not applied',['Open eGauge App']);g.finish('rollback','Update not applied','System and gestures','Proposed actionable rollback outcome; retained pages are restored when the outcome is known.')
g=Gauge();g.numeric('Coolant','112','°C');g.alert();g.demo();g.finish('warning-simulated','Warning + simulation','System and gestures','Generated bench values remain labelled Demo below the alert row. Missing vehicle data never enables demo input.')
g=Gauge();g.critical();g.demo();g.finish('critical-simulated','Critical + simulation','System and gestures','Deliberate demo data remains identified by the small Demo footer; there is no pill or extra attention text.')

# Cross-layout and stressful cases for the third owner review.
for renderer in ('numeric','arc','bar','trend'):
    g=Gauge();g.status()
    if renderer=='numeric':g.numeric()
    else:g.chart(renderer)
    g.alert();g.finish(renderer+'-alert-mil',renderer.title()+' + CEL + alert','Lower alert row','Fixed top CEL/count and lower condition. Charts and units keep dedicated space; no alert-time reflow.')
g=Gauge();g.status();g.numeric('Fuel rail pressure','1498.25','psi',40);g.alert('Fuel rail');g.finish('custom-pressure-alert','Custom decimal + alert','Lower alert row','Full decimal value and converted unit remain readable; authored short alert alias is bounded independently.')
g=Gauge();g.status();g.dual_row('Coolant','112','°C',97);g.line([(50,120),(190,120)],TRACK);g.dual_row('Gear','D','',160);g.alert();g.finish('dual-units-alert','Dual units + alert','Lower alert row','Units appear once beside their value; the condition stays below both rows.')
g=Gauge();g.status();g.dual_row('Ratio','1.235','',97);g.line([(50,120),(190,120)],TRACK);g.dual_row('Transmission','LOCKED','',160);g.alert('ATF');g.finish('custom-enum-alert','Long enum + alert','Lower alert row','The complete enum keeps its 40 px fit; the lower condition never truncates its digits or label.')
g=Gauge();g.status();g.dual();g.alert(more=2);g.finish('multiple-alerts','Multiple alerts + CEL','Lower alert row','Top 4 counts fault entries. Lower +2 means two additional active alerts. The highest-priority condition stays stable; tap opens inspection, never timed text cycling.')
g=Gauge();g.status();g.numeric('Coolant','118','°C');g.alert(kind='critical');g.finish('critical-acked-mil','Acknowledged critical + CEL','Lower alert row','After explicit acknowledgment, CEL remains above and active critical condition below. New critical entry/escalation still uses the full attention screen.')
g=Gauge();g.status('mil','?');g.numeric('Battery','11.5','V',48);g.alert('Voltage',direction='down');g.finish('below-threshold-mil','Below threshold + unknown CEL count','Lower alert row','Down arrow identifies a low condition; the top question mark keeps unknown fault coverage explicit.')
g=Gauge();g.status('mil','?');g.dual('TCM');g.alert('ATF last seen',direction=None);g.finish('dual-last-seen-alert','Lost TCM + retained alert','Lower alert row','Missing gear becomes dashes, sibling RPM stays live and the prior ATF warning remains explicitly last seen.')
g=Gauge();g.status('mil','?');g.dual('both');g.alert('ATF last seen',direction=None);g.finish('both-missing-alert','Both sources absent + retained alert','Lower alert row','Both readings are unavailable. Retained attention is not a fresh sample; unknown CEL count is not zero.')
for i in range(5):
    g=Gauge()
    if i==0:
        for j,color in enumerate(('#FF0000','#00FF00','#0000FF','#FFFF00','#00FFFF','#FF00FF')):
            x=62+(j%3)*40;y=72+(j//3)*40;g.rect((x,y,x+32,y+32),color)
        g.text('Color',176,16,WHITE)
    elif i==1:
        for j,color in enumerate(('#101010','#606060','#FFFFFF')):g.rect((52,70+j*28,188,90+j*28),color)
        g.text('Neutral',176,16,WHITE)
    elif i==2:
        for radius,c in ((112,AMBER),(104,WHITE),(96,GREEN)):
            g.d.ellipse(((120-radius)*S,(120-radius)*S,(120+radius)*S,(120+radius)*S),outline=c,width=2*S)
        g.text('Safe area',108,16,WHITE);g.text('104 px',140,24,WHITE)
    elif i==3:g.text('Typography',71,16,MUTED);g.text('16,383',119,48,WHITE,bold=True);g.text('rpm',169,24,MUTED)
    else:
        g.d.arc((11*S,11*S,229*S,229*S),135,405,TRACK,11*S);g.d.arc((11*S,11*S,229*S,229*S),135,322,GREEN,11*S);g.numeric('Coolant','92','°C')
    g.finish('calibration-'+str(i+1),'Calibration '+str(i+1),'Calibration','Bench-only transport, geometry and type reference, not a production status screen.')

review_meta={'reviewRound':ROUND,'previousRound':PREVIOUS_ROUND,'reviewId':f'gauge-ui-2026-10-14-v{ROUND}'}
(OUT/'mock-manifest.json').write_text(json.dumps({'status':'proposed-not-implemented',**review_meta,'logicalSize':[240,240],'essentialRadius':104,'fonts':'production Noto TTF; rasterizer differs from LVGL','screens':rows},indent=2)+'\n')
(OUT/'mock-data.js').write_text('window.gaugeMockReview='+json.dumps(review_meta)+';\nwindow.gaugeMockScreens='+json.dumps(rows,ensure_ascii=False)+';\n')

def board(ids,name,title,subtitle):
    cols=3;cw=352;ch=350;head=108
    im=Image.new('RGB',(cols*cw+48,head+((len(ids)+cols-1)//cols)*ch+30),'#10171C');d=ImageDraw.Draw(im)
    def f(size,bold=False):return ImageFont.truetype(str(FONT/('NotoSans-SemiBold.ttf' if bold else 'NotoSans-Medium.ttf')),size)
    d.text((30,22),title,font=f(27,True),fill=WHITE);d.text((30,65),subtitle,font=f(15),fill=MUTED)
    for j,key in enumerate(ids):
        row=next(v for v in rows if v['id']==key);x=24+(j%cols)*cw;y=head+(j//cols)*ch
        circle=Image.open(OUT/(key+'.png')).resize((264,264),Image.Resampling.LANCZOS)
        im.paste(circle,(x+44,y),circle);d.text((x+44,y+280),row['title'],font=f(18,True),fill=WHITE)
        d.text((x+44,y+310),'240 × 240 concept, enlarged 1.1×',font=f(12),fill=MUTED)
    im.save(OUT/(name+'.png'))
board(['threshold-mil','numeric-alert-mil','arc-alert-mil','bar-alert-mil','trend-alert-mil','multiple-alerts'],'overview','eGauge: top CEL, lower alerts (v3)','CEL/count above. Condition below. Stable readings and charts. Review concepts only.')
board(['numeric','arc','bar','trend','dual','custom-pressure'],'layouts','All five reading layouts + custom PID','Production palette and Noto fonts. Text glyph bounds checked against the radius-104 safe region.')
board(['stored','mil-unknown-count','threshold','critical-acked','last-seen','not-checked'],'status-states','Fault, warning and coverage states','Severity, fresh versus last seen, and unknown coverage remain distinct.')
board(['pair-ready','passkey','paired','reset','update','empty'],'system-states','System screens','Proposed: preserve interaction semantics, show one instruction, avoid reading/status leakage.')
board(['dual-units-alert','custom-enum-alert','custom-pressure-alert','critical-acked-mil','dual-last-seen-alert','both-missing-alert'],'alert-edge-cases','Alert layout: stressful combinations','Complete values, clear units, retained versus current data, and independent source availability.')
print(f'Generated {len(rows)} concepts with glyph, essential-icon and non-overlap checks; five review boards')
