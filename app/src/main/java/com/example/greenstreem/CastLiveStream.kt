package com.example.greenstreem

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.decoder.ffmpeg.CastAudioDecoder
import androidx.media3.extractor.*
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import java.io.*
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.Locale

/** Our live casting adapter: existing extractor/decoder, Android AAC encoder, and our TS writer. */
internal class CastLiveStream(private val media: CastSupport.Media, private val directory: File): AutoCloseable {
    data class Segment(val number: Int,val file: File,val duration: Double)
    @Volatile var failure: String?=null; private set
    @Volatile private var stopped=false
    private val segments=ArrayDeque<Segment>()
    private val timestamps=TimestampAdjuster(0)
    private val transportClocks=CastTransportClock()
    private var reportedDecodeClock=false
    private var sequence=0
    private var segmentStart=-1L
    private var segmentFile: File?=null
    private var segmentOutput: OutputStream?=null
    private var mux: CastTransportStream?=null
    private var audio: CastAudioDecoder?=null
    private var encoder: MediaCodec?=null
    private var audioRate=48000
    private var audioChannels=2
    private var audioTime=0L
    private var audioFrames=0L
    private var videoFormat: Format?=null
    private var audioTrackId: Int?=null
    private val source=DefaultHttpDataSource.Factory().setUserAgent(media.headers.entries.firstOrNull { it.key.equals("User-Agent",true) }?.value ?: "VLC/3.0.21 LibVLC/3.0.21")
        .setDefaultRequestProperties(media.headers).setConnectTimeoutMs(10000).setReadTimeoutMs(15000).setAllowCrossProtocolRedirects(true).createDataSource()
    private val worker=Thread({ produce() },"Cast audio compatibility")
    init { directory.mkdirs();worker.start() }
    fun ready()=synchronized(segments) { segments.size>=2 }
    fun playlist(): String=synchronized(segments) {
        "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:${kotlin.math.ceil(segments.maxOfOrNull { it.duration } ?: 12.0).toInt()}\n#EXT-X-MEDIA-SEQUENCE:${segments.firstOrNull()?.number ?: 0}\n"+
            segments.joinToString("") { "#EXTINF:${String.format(Locale.US,"%.3f",it.duration)},\n${it.number}.ts\n" }
    }
    fun segment(number: Int): ByteArray?=synchronized(segments) { segments.firstOrNull { it.number==number }?.file?.readBytes() }
    fun continuous(): InputStream=object: InputStream() {
        private var next=synchronized(segments) { segments.firstOrNull()?.number ?: 0 }
        private var current: ByteArrayInputStream?=null
        override fun read(): Int { val one=ByteArray(1);return if(read(one,0,1)<0) -1 else one[0].toInt() and 255 }
        override fun read(bytes: ByteArray,offset: Int,length: Int): Int {
            if(length==0)return 0
            while(!stopped) {
                current?.let { val count=it.read(bytes,offset,length);if(count>=0)return count;current=null;next++ }
                val data=synchronized(segments) {
                    segments.firstOrNull()?.let { if(next<it.number) next=it.number }
                    segments.firstOrNull { it.number==next }?.file?.readBytes()
                }
                if(data!=null)current=ByteArrayInputStream(data)
                else { if(failure!=null)return -1;Thread.sleep(100) }
            }
            return -1
        }
    }
    private fun rotate(time: Long) {
        segmentOutput?.close()
        segmentFile?.let { file -> synchronized(segments) {
            segments.add(Segment(sequence++,file,(time-segmentStart).coerceAtLeast(1)/1e6))
            while(segments.size>8) segments.removeFirst().file.delete()
        } }
        segmentStart=time;segmentFile=File(directory,"$sequence.ts")
        segmentOutput=BufferedOutputStream(FileOutputStream(segmentFile!!))
        mux=CastTransportStream(segmentOutput!!).also { it.tables() }
    }
    private fun sample(format: Format,bytes: ByteArray,time: Long,flags: Int) {
        if(stopped)throw InterruptedIOException()
        when {
            format.sampleMimeType=="video/avc" -> {
                val key=flags and C.BUFFER_FLAG_KEY_FRAME!=0
                if(mux==null && !key)return
                check(mux==null || time-segmentStart<30_000_000) { "The stream has no usable video keyframes" }
                if(key && (mux==null || time-segmentStart>=3_000_000)) rotate(time)
                val dts=transportClocks.decodeTime(time,timestamps.timestampOffsetUs)
                if(dts!=null && !reportedDecodeClock) { reportedDecodeClock=true;android.util.Log.i("GreenStreemCast","Original video decode timing retained") }
                if(key) mux?.tables()
                val prefix=if(key) format.initializationData.fold(ByteArray(0)) { all,data -> all+data } else ByteArray(0)
                mux?.sample(true,prefix+bytes,time,dts)
            }
            format.sampleMimeType?.startsWith("audio/")==true -> {
                if(mux==null)return
                val decoder=audio ?: CastAudioDecoder(format).also { audio=it }
                while(!decoder.offer(bytes,time)) { drainAudio();Thread.sleep(2) }
                drainAudio()
            }
        }
    }
    private fun drainAudio() {
        while(audio?.drain { pcm,time,rate,channels ->
            val codec=encoder ?: MediaCodec.createEncoderByType("audio/mp4a-latm").also {
                audioRate=rate;audioChannels=minOf(channels,2);audioTime=time
                val f=MediaFormat.createAudioFormat("audio/mp4a-latm",rate,audioChannels).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE,160000);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,32768)
                }
                it.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);it.start();encoder=it
            }
            pcm.order(ByteOrder.nativeOrder())
            // FFmpeg emits signed 16-bit PCM in native channel order; retain centre dialogue when downmixing.
            val frames=pcm.remaining()/(2*channels)
            val converted=java.nio.ByteBuffer.allocate(frames*audioChannels*2).order(ByteOrder.nativeOrder())
            val values=ShortArray(channels)
            repeat(frames) {
                repeat(channels) { values[it]=pcm.short }
                if(channels<=2) repeat(channels) { converted.putShort(values[it]) }
                else {
                    val centre=values[2]*0.707
                    val left=(values[0]+centre+(if(channels>4) values[4]*0.707 else 0.0))*0.5
                    val right=(values[1]+centre+(if(channels>5) values[5]*0.707 else 0.0))*0.5
                    converted.putShort(left.toInt().coerceIn(-32768,32767).toShort());converted.putShort(right.toInt().coerceIn(-32768,32767).toShort())
                }
            }
            converted.flip()
            while(converted.hasRemaining() && !stopped) {
                drainEncoder(codec)
                val index=codec.dequeueInputBuffer(10000)
                if(index>=0) {
                    val input=codec.getInputBuffer(index)!!;input.clear()
                    val size=minOf(input.remaining(),converted.remaining())/(2*audioChannels)*(2*audioChannels)
                    val part=converted.duplicate();part.limit(converted.position()+size);input.put(part);converted.position(converted.position()+size)
                    codec.queueInputBuffer(index,0,size,audioTime+audioFrames*1_000_000/audioRate,0);audioFrames+=size/(2*audioChannels)
                }
            }
            drainEncoder(codec)
        }==true) { }
    }
    private fun drainEncoder(codec: MediaCodec) {
        val info=MediaCodec.BufferInfo()
        while(true) {
            val index=codec.dequeueOutputBuffer(info,0)
            if(index<0) { if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)continue;return }
            try {
                if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                    val bytes=ByteArray(info.size+7);val size=bytes.size
                    val frequency=listOf(96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350).indexOf(audioRate)
                    check(frequency>=0) { "Unsupported audio sample rate" }
                    bytes[0]=0xff.toByte();bytes[1]=0xf1.toByte();bytes[2]=((1 shl 6) or (frequency shl 2) or (audioChannels shr 2)).toByte()
                    bytes[3]=(((audioChannels and 3) shl 6) or (size shr 11)).toByte();bytes[4]=(size shr 3).toByte();bytes[5]=(((size and 7) shl 5) or 31).toByte();bytes[6]=0xfc.toByte()
                    codec.getOutputBuffer(index)!!.apply { position(info.offset);limit(info.offset+info.size);get(bytes,7,info.size) }
                    mux?.sample(false,bytes,info.presentationTimeUs)
                }
            } finally { codec.releaseOutputBuffer(index,false) }
        }
    }
    private inner class Track: TrackOutput {
        private var format: Format?=null
        private var pending=ByteArrayOutputStream()
        override fun format(format: Format) { this.format=format;if(format.sampleMimeType?.startsWith("video/")==true) {
            check(format.sampleMimeType=="video/avc") { "This live video codec needs a compatible TV" };videoFormat=format
        } }
        override fun sampleData(input: androidx.media3.common.DataReader,length: Int,allowEndOfInput: Boolean,sampleDataPart: Int): Int {
            val bytes=ByteArray(minOf(length,65536));val count=input.read(bytes,0,bytes.size)
            if(count<0) { if(allowEndOfInput)return -1;throw EOFException() };pending.write(bytes,0,count);return count
        }
        override fun sampleData(data: ParsableByteArray,length: Int,sampleDataPart: Int) { val bytes=ByteArray(length);data.readBytes(bytes,0,length);pending.write(bytes) }
        override fun sampleMetadata(timeUs: Long,flags: Int,size: Int,offset: Int,cryptoData: TrackOutput.CryptoData?) {
            val bytes=pending.toByteArray();val start=bytes.size-offset-size
            check(start>=0 && bytes.size<=16*1024*1024) { "Invalid live sample" }
            format?.let { sample(it,bytes.copyOfRange(start,start+size),timeUs,flags) }
            pending=ByteArrayOutputStream().also { if(offset>0) it.write(bytes,bytes.size-offset,offset) }
        }
    }
    private fun produce() {
        try {
            val length=source.open(DataSpec(android.net.Uri.parse(media.url)))
            val reader=androidx.media3.common.DataReader { buffer,offset,count ->
                val read=source.read(buffer,offset,count)
                if(read>0)transportClocks.consume(buffer,offset,read)
                read
            }
            val input=DefaultExtractorInput(reader,0,length)
            val extractor=TsExtractor(TsExtractor.MODE_SINGLE_PMT,timestamps,DefaultTsPayloadReaderFactory())
            try {
                extractor.init(object: ExtractorOutput {
                    override fun track(id: Int,type: Int): TrackOutput {
                        if(type==C.TRACK_TYPE_VIDEO)return Track()
                        if(type==C.TRACK_TYPE_AUDIO && (audioTrackId==null || audioTrackId==id)) { audioTrackId=id;return Track() }
                        return DummyTrackOutput()
                    }
                    override fun endTracks() {}
                    override fun seekMap(seekMap: SeekMap) {}
                })
                val holder=PositionHolder()
                while(!stopped && extractor.read(input,holder)!=Extractor.RESULT_END_OF_INPUT) { }
                if(!stopped)failure="The live stream ended"
            } finally { extractor.release() }
        } catch(e: Exception) { if(!stopped) { failure=e.message ?: "Could not prepare live stream";android.util.Log.w("GreenStreemCast","Live compatibility stream failed: ${e.javaClass.simpleName}") } }
        finally { runCatching { source.close() };runCatching { audio?.close() };runCatching { encoder?.stop() };runCatching { encoder?.release() };runCatching { segmentOutput?.close() } }
    }
    override fun close() { stopped=true;runCatching { source.close() };worker.interrupt();worker.join(2000);directory.deleteRecursively() }
}
