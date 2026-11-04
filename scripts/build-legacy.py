"""Build a dependency-free API19 TV edition with SDK command-line tools."""
import hashlib,json,os,pathlib,shutil,subprocess,tempfile,zipfile,urllib.request
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
    aar=stage/'conscrypt.aar'
    cached_aar=os.environ.get('AO_BING_CONSCRYPT_AAR')
    if cached_aar:
        shutil.copyfile(cached_aar,aar)
    else:
        urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/conscrypt/conscrypt-android/2.5.2/conscrypt-android-2.5.2.aar',aar)
    if hashlib.sha256(aar.read_bytes()).hexdigest()!='42d18979caf53f5ef68548c76d4c98b41adb910a32ad9448133f9c5b20bd65a3':
        raise RuntimeError('Conscrypt checksum mismatch')
    with zipfile.ZipFile(aar) as dep:
        tls=stage/'conscrypt.jar';tls.write_bytes(dep.read('classes.jar'))
        for name in dep.namelist():
            if name.startswith('jni/') and name.endswith('.so'):
                dest=stage/('lib/'+name[4:]);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(dep.read(name))
    run(binary(java,'javac'),'-encoding','UTF-8','-source','8','-target','8','-bootclasspath',jar,'-classpath',tls,'-d',classes,*sorted((src/'src').rglob('*.java')))
    run(binary(bt,'d8'),'--min-api','19','--lib',jar,'--output',stage,tls,*sorted(classes.rglob('*.class')))
    run(binary(bt,'aapt'),'package','-f','-M',buildsrc/'AndroidManifest.xml','-S',buildsrc/'res','-A',buildsrc/'assets','-I',jar,'-F',stage/'unsigned.apk')
    run(binary(bt,'aapt'),'add','unsigned.apk','classes.dex',cwd=stage)
    with zipfile.ZipFile(stage/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as package:
        for lib in (stage/'lib').rglob('*.so'):
            package.write(lib,lib.relative_to(stage).as_posix())
    run(binary(bt,'zipalign'),'-f','4',stage/'unsigned.apk',stage/'aligned.apk')
    key=pathlib.Path(os.environ.get('AOBING_LEGACY_KEYSTORE',str(pathlib.Path.home()/'.aobing/legacy-debug.keystore')))
    key.parent.mkdir(parents=True,exist_ok=True)
    if not key.exists():run(binary(java,'keytool'),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','aobing','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Rios Aobing Legacy Test')
    apk=out/'aobing-api19-v0.4.9.apk'
    # Some Android tools cannot read Chinese Windows paths: sign in ASCII temp first.
    run(binary(bt,'apksigner'),'sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--ks-key-alias','aobing','--min-sdk-version','19','--v1-signing-enabled','true','--v2-signing-enabled','true','--out',stage/'signed.apk',stage/'aligned.apk')
    run(binary(bt,'apksigner'),'verify','--verbose','--min-sdk-version','19',stage/'signed.apk')
    run(binary(bt,'aapt'),'dump','badging',stage/'signed.apk')
    shutil.copyfile(stage/'signed.apk',apk)
report={'version':'0.4.9','minSdk':19,'targetSdk':27,'compileSdk':19,'tlsProvider':'Conscrypt 2.5.2','systemPlayer':True,'testedOnChigoBox':False,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'size':apk.stat().st_size}
(out/'compatibility.json').write_text(json.dumps(report,indent=2),encoding='utf8')
with zipfile.ZipFile(out/'aobing-api19-v0.4.9-source.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in src.rglob('*'):
        if p.is_file():z.write(p,'android-legacy/'+str(p.relative_to(src)))
    z.write(__file__,'scripts/build-legacy.py')
(out/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(out.iterdir()) if p.is_file() and ('0.4.9' in p.name or p.name=='compatibility.json')),encoding='utf8')
print(json.dumps(report))
