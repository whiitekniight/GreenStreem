package androidx.media3.decoder.ffmpeg;

import androidx.media3.common.Format;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import java.nio.ByteBuffer;

/** Adapter to the audio decoder already shipped by GreenStreem. */
public final class CastAudioDecoder implements AutoCloseable {
    private final FfmpegAudioDecoder decoder;
    public CastAudioDecoder(Format format) throws FfmpegDecoderException {
        decoder = new FfmpegAudioDecoder(format, 4, 4, 65536, false);
    }
    public boolean offer(byte[] bytes, long timeUs) throws FfmpegDecoderException {
        DecoderInputBuffer input = decoder.dequeueInputBuffer();
        if (input == null) return false;
        input.ensureSpaceForWrite(bytes.length);
        input.data.put(bytes); input.timeUs = timeUs; input.flip();
        decoder.queueInputBuffer(input);
        return true;
    }
    public interface PcmConsumer { void accept(ByteBuffer data, long timeUs, int rate, int channels); }
    public boolean drain(PcmConsumer consumer) throws FfmpegDecoderException {
        SimpleDecoderOutputBuffer output = decoder.dequeueOutputBuffer();
        if (output == null) return false;
        try { if (output.data != null) consumer.accept(output.data, output.timeUs, decoder.getSampleRate(), decoder.getChannelCount()); }
        finally { output.release(); }
        return true;
    }
    @Override public void close() { decoder.release(); }
}
