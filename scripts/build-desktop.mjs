import {packager} from '@electron/packager';
import fs from 'node:fs';import path from 'node:path';import cp from 'node:child_process';
const platform=process.argv[2]||process.platform;
if(!['win32','darwin'].includes(platform))throw Error('Use win32 or darwin');
fs.mkdirSync('staging',{recursive:true});fs.cpSync('desktop','staging/app',{recursive:true});
let icon='desktop/assets/icon.ico';
if(platform==='darwin'){
 let s=fs.readFileSync('staging/app/index.html','utf8').replace('WINDOWS · 影音空间','MAC INTEL · 影音空间').replace('Windows 0.2.1','Mac Intel 0.2.1');fs.writeFileSync('staging/app/index.html',s);
 const png=fs.readFileSync('desktop/assets/icon.png'),head=Buffer.alloc(16);head.write('icns');head.writeUInt32BE(png.length+16,4);head.write('ic08',8);head.writeUInt32BE(png.length+8,12);fs.writeFileSync('staging/icon.icns',Buffer.concat([head,png]));icon='staging/icon.icns';
}
const dirs=await packager({dir:'staging/app',name:'敖丙视界',platform,arch:'x64',electronVersion:'45.0.0',out:'release',overwrite:true,asar:true,icon,appBundleId:'com.rios.aobing.vision',appCopyright:'Copyright © 2026 Rios敖丙',osxSign:false,win32metadata:{CompanyName:'Rios敖丙',FileDescription:'敖丙视界 · 影视直播',ProductName:'敖丙视界'}});
if(platform==='darwin'){
 const bundle=path.join(dirs[0],'敖丙视界.app');
 cp.execFileSync('/usr/bin/codesign',['--force','--deep','--sign','-',bundle],{stdio:'inherit'});
 cp.execFileSync('/usr/bin/codesign',['--verify','--deep','--strict',bundle],{stdio:'inherit'});
 fs.mkdirSync('installer',{recursive:true});
 cp.execFileSync('/usr/bin/ditto',['-c','-k','--sequesterRsrc','--keepParent',bundle,'installer/Aobing-Vision-Mac-Intel-0.2.1.zip'],{stdio:'inherit'});
}
