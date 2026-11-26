# Synthetic feedback-test media

These fixtures are generated from FFmpeg testsrc2 and sine filters; they contain
no provider content. They are packaged only in the instrumentation test APK.

Generation commands:

    ffmpeg -f lavfi -i testsrc2=size=160x90:rate=12 -f lavfi -i sine=frequency=440:sample_rate=48000 -t 180 -c:v libx264 -preset ultrafast -g 24 -pix_fmt yuv420p -b:v 50000 -c:a aac -b:a 32000 -f mpegts feedback-cast.ts
    ffmpeg -i feedback-cast.ts -c copy -movflags +faststart feedback-vod.mp4
