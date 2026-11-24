#!/usr/bin/env bash
set -euo pipefail
source /opt/devkitpro/switchvars.sh
mkdir -p /tmp/aobing-ffmpeg
cd /tmp/aobing-ffmpeg
curl -fL --retry 3 https://ffmpeg.org/releases/ffmpeg-7.1.tar.xz -o ffmpeg.tar.xz
echo '40973d44970dbc83ef302b0609f2e74982be2d85916dd2ee7472d30678a7abe6  ffmpeg.tar.xz' | sha256sum -c -
for name in ffmpeg-7.1.patch tls.patch; do
  curl -fL --retry 3 "https://raw.githubusercontent.com/devkitPro/pacman-packages/master/switch/ffmpeg/$name" -o "$name"
done
echo '1792380b992e3554a4abcddf0d7b395bfd8c118ac7c6e38c8f2fb0d39753a390  ffmpeg-7.1.patch' | sha256sum -c -
echo '57ea7ec8ed26d13d3172d0fd589b7883d22f0c180d50b7434fcc73fb2b3ab7d7  tls.patch' | sha256sum -c -
tar xf ffmpeg.tar.xz
cd ffmpeg-7.1
patch -p1 < ../ffmpeg-7.1.patch
patch -p1 < ../tls.patch
# Match devkitPro's port, enabling HTTPS and AES-encrypted HLS explicitly.
./configure --prefix="$PORTLIBS_PREFIX" --enable-gpl --disable-shared --enable-static \
 --cross-prefix=aarch64-none-elf- --enable-cross-compile --arch=aarch64 --cpu=cortex-a57 --target-os=horizon --enable-pic \
 --extra-cflags='-D__SWITCH__ -D_GNU_SOURCE -O2 -march=armv8-a -mtune=cortex-a57 -mtp=soft -fPIC -ftls-model=local-exec' \
 --extra-cxxflags='-D__SWITCH__ -D_GNU_SOURCE -O2 -march=armv8-a -mtune=cortex-a57 -mtp=soft -fPIC -ftls-model=local-exec' \
 --extra-ldflags="-fPIE -L${PORTLIBS_PREFIX}/lib -L${DEVKITPRO}/libnx/lib" \
 --disable-runtime-cpudetect --disable-programs --disable-debug --disable-doc --disable-autodetect \
 --enable-asm --enable-neon --disable-avdevice --disable-encoders --disable-muxers \
 --enable-swscale --enable-swresample --enable-network --disable-protocols \
 --enable-protocol=file,http,https,tcp,udp,rtmp,tls,httpproxy,crypto,data \
 --enable-zlib --enable-bzlib --enable-libass --enable-libfreetype --enable-libfribidi --enable-libdav1d \
 --enable-libnx --enable-nvtegra
grep -q '^#define CONFIG_HTTPS_PROTOCOL 1' config_components.h
grep -q '^#define CONFIG_CRYPTO_PROTOCOL 1' config_components.h
make -j4
make install
