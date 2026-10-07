#!/usr/bin/env bash
# Starts go2rtc with six synthetic "cameras" made by FFmpeg's lavfi sources, for the README
# screenshots. Usage: streams.sh <path-to-go2rtc-binary> <scratch-dir>
# go2rtc listens on all local addresses (127.0.0.1, and 192.0.2.10 if that documentation
# address was added to loopback); the FFmpeg processes publish to it over RTSP.
set -euo pipefail
GO2RTC=$1
DIR=$2
mkdir -p "$DIR"
cat > "$DIR/go2rtc.yaml" <<YAML
api:
  listen: ":1984"
rtsp:
  listen: ":8554"
webrtc:
  candidates:
    - 192.0.2.10:8555
    - 127.0.0.1:8555
log:
  level: warn
streams:
  front_door:
  driveway:
  garden:
  garage:
  doorbell:
  backyard:
YAML
"$GO2RTC" -config "$DIR/go2rtc.yaml" > "$DIR/go2rtc.log" 2>&1 &
sleep 2

FONT=/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf
# Camera-style overlay inset from the edges (so a cropped tile keeps it): timestamp top left, camera label bottom right, a little sensor noise.
osd() {
  echo "noise=alls=3:allf=t,drawtext=fontfile=$FONT:text='%{localtime}':x=w*0.14+12:y=h*0.03+8:fontsize=$2:fontcolor=white:box=1:boxcolor=black@0.35:boxborderw=6,drawtext=fontfile=$FONT:text='$1':x=w*0.86-tw-12:y=h*0.97-th-8:fontsize=$2:fontcolor=white@0.9"
}
push() { # name, lavfi source, extra filters, overlay label, font size
  ffmpeg -hide_banner -loglevel error -re -f lavfi -i "$2" \
    -vf "$3,$(osd "$4" "$5"),format=yuv420p" \
    -c:v libx264 -preset ultrafast -tune zerolatency -profile:v baseline -g 20 -bf 0 \
    -f rtsp -rtsp_transport tcp "rtsp://127.0.0.1:8554/$1" > "$DIR/ffmpeg-$1.log" 2>&1 &
}
push front_door "gradients=s=1280x720:r=10:c0=0x1e2a38:c1=0x6b7f8e:c2=0x8c7b5a:c3=0x2f3b26:nb_colors=4:speed=0.004:type=linear" "eq=saturation=0.8" "CAM 01" 26
push driveway "gradients=s=1280x720:r=10:c0=0x4a4038:c1=0x8f8574:c2=0x24303a:c3=0x6e7b62:nb_colors=4:speed=0.005:type=linear:x0=0:y0=0:x1=1280:y1=720" "eq=saturation=0.7" "CAM 02" 26
push garden "life=s=320x180:r=10:mold=10:ratio=0.12:death_color=#2b4a26:life_color=#9cc96b:mold_color=#4f6f3a" "scale=1280:720:flags=neighbor,boxblur=2" "CAM 03" 26
push garage "cellauto=s=1280x720:r=10:rule=110:scroll=1:random_fill_ratio=0.5" "boxblur=1,colorchannelmixer=rr=0.55:gg=0.75:bb=0.6" "CAM 04 IR" 26
push doorbell "gradients=s=720x1280:r=10:c0=0x3a2c22:c1=0xb08a5e:c2=0x5d6e7a:nb_colors=3:speed=0.006:type=radial" "eq=saturation=0.85" "DOORBELL" 30
push backyard "gradients=s=1280x720:r=10:c0=0x0f1c2e:c1=0x35506b:c2=0x1d3320:c3=0x5b7a4a:nb_colors=4:speed=0.003:type=circular" "eq=saturation=0.8" "CAM 06" 26
echo "go2rtc on http://127.0.0.1:1984 (logs in $DIR)"
