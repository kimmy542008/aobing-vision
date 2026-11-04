"""Reproduce the Android TV compatibility build from a pinned GPL upstream."""
import os,subprocess,sys,urllib.request,zipfile,pathlib,shutil,json,hashlib
repo=pathlib.Path(__file__).resolve().parents[1]
stage=repo/'staging/android';stage.mkdir(parents=True,exist_ok=True)
revision='2362d6845f4b91b7846b5cd77f947cd5884e0894'
archive=stage/'upstream.zip'
if not archive.exists():urllib.request.urlretrieve('https://codeload.github.com/okcaptain/TV/zip/'+revision,archive)
with zipfile.ZipFile(archive) as z:
    prefix=z.namelist()[0].split('/')[0]
    z.extractall(stage)
src=stage/prefix
subprocess.run(['git','apply','--ignore-space-change','--ignore-whitespace',str(repo/'android/compatibility.patch')],cwd=src,check=True)
shutil.copytree(repo/'android/resources',src,dirs_exist_ok=True)
shutil.copyfile(repo/'desktop/assets/icon.png',src/'app/src/main/res/drawable/aobing_mascot.png')
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:raise RuntimeError('Set ANDROID_HOME to Android SDK with API34/build-tools34.0.0')
(src/'local.properties').write_text('sdk.dir='+sdk.replace('\\','/')+'\n',encoding='utf8')
gradle=os.environ.get('GRADLE_COMMAND','gradle')
variants = [
    ('leanbackJavaUniversal','Android9-Universal'),
    ('leanbackJavaArmeabi_v7a','Android9-ARM32'),
    ('leanbackJavaArm64_v8a','Android9-ARM64'),
    ('mobileJavaUniversal','Mobile-Universal'),
]
tasks = [':app:assemble'+name[0].upper()+name[1:]+'Debug' for name,_ in variants]
subprocess.run([gradle,*tasks,'--console=plain','--max-workers=4'],cwd=src,check=True)
out=repo/'installer/android';out.mkdir(parents=True,exist_ok=True)
report={'version':'0.4.12','minSdk':21,'targetSdk':28,'testedDeviceAndroid9':False,'upstream':revision,'assets':[]}
signer=pathlib.Path(sdk)/'build-tools/34.0.0'/('apksigner.bat' if os.name=='nt' else 'apksigner')
aapt=signer.parent/('aapt.exe' if os.name=='nt' else 'aapt')
for flavor,label in variants:
    files=list((src/'app/build/outputs/apk'/flavor/'debug').glob('*.apk'));assert len(files)==1
    dest=out/('AobingTV-'+label+'-v0.4.12.apk');shutil.copyfile(files[0],dest)
    subprocess.run([str(signer),'verify','--min-sdk-version','21',str(dest)],check=True)
    app_id='com.rios.aobing.tv.mobile' if flavor.startswith('mobile') else 'com.rios.aobing.tv'
    badging=subprocess.check_output([str(aapt),'dump','badging',str(dest)],encoding='utf-8')
    if f"name='{app_id}'" not in badging or f"versionName='{report['version']}'" not in badging:
        raise RuntimeError(f'Unexpected package metadata for {dest.name}')
    manifest=subprocess.check_output([str(aapt),'dump','xmltree',str(dest),'AndroidManifest.xml'],encoding='utf-8')
    if f'{app_id}.quickjs.provider' not in manifest:
        raise RuntimeError(f'Provider authority is not unique for {dest.name}')
    report['assets'].append({'name':dest.name,'applicationId':app_id,'size':dest.stat().st_size,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
(out/'Android-compatibility-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
sourcezip=out/'AobingTV-Android9-v0.4.12-GPL-Source.zip'
with zipfile.ZipFile(sourcezip,'w',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
    for p in src.rglob('*'):
        rel=p.relative_to(src)
        if p.is_file() and not {'build','.gradle','.git','.cxx'}.intersection(rel.parts) and p.name!='local.properties' and p.suffix not in ('.keystore','.jks','.log'):
            z.write(p,str(rel))
print(json.dumps(report,indent=2))
