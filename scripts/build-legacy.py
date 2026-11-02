"""Build a dependency-free API19 TV edition with SDK command-line tools."""
import hashlib,json,os,pathlib,shutil,subprocess,tempfile,zipfile
repo=pathlib.Path(__file__).resolve().parents[1]
src=repo/'android-legacy'
sdk=pathlib.Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'
bt=sdk/'build-tools/34.0.0'
jar=sdk/'platforms/android-19/android.jar'
if not jar.exists(): raise RuntimeError('Install platforms;android-19 to validate actual API19 compatibility')
win=os.name=='nt'
def binary(base,name):return str(base/(name+('.bat' if win and name in ('d8','apksigner') else '.exe' if win else '')))
def run(*args,cwd=None):subprocess.run(list(map(str,args)),cwd=cwd,check=True)
out=repo/'installer/legacy';out.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='aobing-api19-') as td:
    stage=pathlib.Path(td);classes=stage/'classes';classes.mkdir()
    buildsrc=stage/'source';shutil.copytree(src,buildsrc)
    run(binary(java,'javac'),'-encoding','UTF-8','-source','8','-target','8','-bootclasspath',jar,'-d',classes,*sorted((src/'src').rglob('*.java')))
    run(binary(bt,'d8'),'--min-api','19','--lib',jar,'--output',stage,*sorted(classes.rglob('*.class')))
    run(binary(bt,'aapt'),'package','-f','-M',buildsrc/'AndroidManifest.xml','-S',buildsrc/'res','-A',buildsrc/'assets','-I',jar,'-F',stage/'unsigned.apk')
    run(binary(bt,'aapt'),'add','unsigned.apk','classes.dex',cwd=stage)
    run(binary(bt,'zipalign'),'-f','4',stage/'unsigned.apk',stage/'aligned.apk')
    key=pathlib.Path(os.environ.get('AOBING_LEGACY_KEYSTORE',str(pathlib.Path.home()/'.aobing/legacy-debug.keystore')))
    key.parent.mkdir(parents=True,exist_ok=True)
    if not key.exists():run(binary(java,'keytool'),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','aobing','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Rios Aobing Legacy Test')
    apk=out/'aobing-api19-v0.4.1.apk'
    # Some Android tools cannot read Chinese Windows paths: sign in ASCII temp first.
    run(binary(bt,'apksigner'),'sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--ks-key-alias','aobing','--min-sdk-version','19','--v1-signing-enabled','true','--v2-signing-enabled','true','--out',stage/'signed.apk',stage/'aligned.apk')
    run(binary(bt,'apksigner'),'verify','--verbose','--min-sdk-version','19',stage/'signed.apk')
    run(binary(bt,'aapt'),'dump','badging',stage/'signed.apk')
    shutil.copyfile(stage/'signed.apk',apk)
report={'version':'0.4.1','minSdk':19,'targetSdk':27,'compileSdk':19,'nativeLibraries':False,'systemPlayer':True,'testedOnChigoBox':False,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'size':apk.stat().st_size}
(out/'compatibility.json').write_text(json.dumps(report,indent=2),encoding='utf8')
with zipfile.ZipFile(out/'aobing-api19-v0.4.1-source.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in src.rglob('*'):
        if p.is_file():z.write(p,'android-legacy/'+str(p.relative_to(src)))
    z.write(__file__,'scripts/build-legacy.py')
(out/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(out.iterdir()) if p.is_file() and p.name!='SHA256SUMS.txt'),encoding='utf8')
print(json.dumps(report))
