from pathlib import Path
import xml.etree.ElementTree as E
import hashlib,json,re,math,argparse
from PIL import Image
parser=argparse.ArgumentParser();parser.add_argument('--repo-root',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
r=args.repo_root.resolve();p=r/'brand/HULK-SA-Android-Icon-System-v1.4';res=r/'app/src/main/res';a='{http://schemas.android.com/apk/res/android}';checks=[]
def check(n,ok,detail=''):
 checks.append({'name':n,'pass':bool(ok),'detail':detail})
def sha(f):return hashlib.sha256(f.read_bytes()).hexdigest()
colors={n.attrib['name']:n.text for n in E.parse(res/'values/colors.xml').getroot()}
check('live theme gold','val gold: Color = Color(0xFFE6C352)' in (r/'app/src/main/java/sa/hulksa/player/ui/theme/HulkTheme.kt').read_text())
check('flat gold/background',colors['hulk_icon_gold']=='#E6C352' and colors['ic_launcher_background']=='#030402')
for f in p.rglob('*.svg'):
 t=f.read_text();root=E.fromstring(t);fills={x.get('fill','').upper() for x in root.iter() if x.get('fill')}
 check('SVG palette '+str(f.relative_to(p)),not re.search('gradient|<image',t,re.I) and fills<={'#E6C352','#030402','#FFFFFF'},sorted(fills))
# Check every exported bitmap. Quantized alpha edges are allowed, extra paints are not.
for f in p.rglob('*.png'):
 im=Image.open(f);im.load();check('PNG decode/profile '+str(f.relative_to(p)),bool(im.info.get('icc_profile')) and min(im.size)>0)
 if 'previews' in f.parts:continue
 mask='monochrome' in f.name or 'ic_stat_' in f.name
 bad=[]
 for col in im.getcolors(1000000) or []:
  _,v=col;rgb=v[:3];alpha=v[3] if len(v)==4 else 255
  if alpha==0:continue
  if mask:valid=max(abs(c-255)*alpha/255 for c in rgb)<=1.5
  elif alpha<255:
   valid=min(max(abs(c-g)*alpha/255 for c,g in zip(rgb,b)) for b in [(230,195,82),(3,4,2)])<=1.5
  else:
   t=(rgb[0]-3)/227
   valid=-.01<=t<=1.01 and max(abs(rgb[i]-([3,4,2][i]+t*[227,191,80][i])) for i in range(3))<=2
  if not valid:bad.append(v)
 check('PNG palette '+str(f.relative_to(p)),not bad,bad[:3])
for density,factor in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
 for stem,w,h in [('ic_launcher',48,48),('ic_launcher_round',48,48),('ic_launcher_tv',80,80),('tv_banner',160,90)]:
  f=res/f'mipmap-{density}/{stem}.png';check('density '+str(f.relative_to(res)),f.is_file() and Image.open(f).size==(int(w*factor),int(h*factor)))
 f=res/f'drawable-{density}/ic_stat_hulk.png';check('notification density '+density,Image.open(f).size==(int(24*factor),int(24*factor)))
# Runtime-named mirror has no stale legacy names and every mapped file is byte-identical.
for f in (p/'android-res').rglob('*'):
 if not f.is_file():continue
 rel=f.relative_to(p/'android-res')
 if str(rel)=='values/colors.xml':
  vals={n.get('name'):n.text for n in E.parse(f).getroot()};check('mirror colors subset',all(colors.get(k)==v for k,v in vals.items()));continue
 dest=res/rel;check('runtime mirror '+str(rel),dest.is_file() and sha(f)==sha(dest))
check('obsolete integration names removed',not list((p/'android-res').rglob('tv_launcher.png')) and not list((p/'android-res').glob('drawable-*/tv_banner.png')))
for f in (p/'android-res').rglob('*.xml'):
 root=E.parse(f).getroot();t=f.read_text();check('XML flat '+str(f.relative_to(p)), 'gradient' not in t.lower())
 for typ,name in re.findall(r'@(drawable|mipmap|color)/(\w+)',t):
  found= name in colors if typ=='color' else any(x.stem==name for d in res.glob(typ+'*') if d.is_dir() for x in d.iterdir())
  check('resource reference '+str(f.relative_to(p))+':'+name,found)
# Explicit TV v26 must win over its legacy v21 at the same UI-mode qualifier.
for rel in ['mipmap-anydpi-v26/ic_launcher.xml','mipmap-anydpi-v26/ic_launcher_round.xml','mipmap-anydpi-v26/ic_launcher_tv.xml','mipmap-television-anydpi-v26/ic_launcher_tv.xml']:
 node=E.parse(res/rel).getroot();check('adaptive layers '+rel,node.tag=='adaptive-icon' and node.find('foreground').get(a+'drawable')=='@drawable/ic_launcher_foreground' and node.find('background').get(a+'drawable')=='@color/ic_launcher_background')
check('no oversized TV foreground override',not (res/'drawable-television/ic_launcher_foreground.xml').exists())
for n in ['ic_launcher','ic_launcher_round']:
 root=E.parse(res/f'mipmap-anydpi-v33/{n}.xml').getroot();check('themed '+n,root.find('monochrome').get(a+'drawable')=='@drawable/ic_launcher_monochrome')
for rel in ['drawable/ic_launcher_foreground.xml','drawable-v33/ic_launcher_monochrome.xml']:
 node=E.parse(res/rel).getroot();g=node.find('group');scale=float(g.get(a+'scaleX'));tx=float(g.get(a+'translateX'));ty=float(g.get(a+'translateY'));nums=list(map(float,re.findall(r'-?\d+(?:\.\d+)?',g.find('path').get(a+'pathData'))));pts=list(zip(nums[::2],nums[1::2]));out=[(tx+x*scale,ty+y*scale) for x,y in pts];radius=max(math.hypot(x-54,y-54) for x,y in out)
 check('adaptive safe circle '+rel,node.get(a+'width')=='108dp' and node.get(a+'height')=='108dp' and radius<=33,{'radius_dp':radius,'height_dp':(max(y for x,y in out)-min(y for x,y in out))})
# All bounded brand tiles share the same shield geometry; rectangular slots fit by shortest side.
ref=E.parse(res/'drawable/hulk_sa_mark_reference.xml').getroot();group=ref.find('group');pts=list(map(float,re.findall(r'-?\d+(?:\.\d+)?',group.find('path').get(a+'pathData'))));s=float(group.get(a+'scaleX'));tx=float(group.get(a+'translateX'));ty=float(group.get(a+'translateY'));xy=[(tx+x*s,ty+y*s) for x,y in zip(pts[::2],pts[1::2])]
for w,h in [(16,16),(18,18),(24,24),(48,48),(82,82),(132,132),(320,180),(180,270),(270,180)]:
 side=min(w,h);out=[((w-side)/2+x*side/1000,(h-side)/2+y*side/1000) for x,y in xy];check('fitted brand bounds '+str((w,h)),all(0<x<w and 0<y<h for x,y in out))
check('R02 banner composition', (p/'tv/tv_banner_master.svg').read_bytes()==(p/'master/hulk_sa_lockup_master.svg').read_bytes())
components=(r/'app/src/main/java/sa/hulksa/player/ui/components/HulkComponents.kt').read_text()
check('placeholder contains independently of Crop','override val intrinsicSize: Size = Size.Unspecified' in components and 'val side = minOf(size.width, size.height)' in components)
report={'candidate':'icon-system-flat-gold-20261016-r03','status':'PASS' if all(c['pass'] for c in checks) else 'FAIL','checks':checks,'count':len(checks),'failed':[c for c in checks if not c['pass']],'scope':'source/assets only; Android build and runtime separately recorded'}
args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='checks'}));raise SystemExit(bool(report['failed']))
