package sa.hulksa.player.playback;

import android.os.Handler;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderException;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoder;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DecoderAudioRenderer;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.LayerIIDecoder;
import javazoom.jl.decoder.OutputBuffer;
import javazoom.jl.decoder.OutputChannels;
import javazoom.jl.decoder.SynthesisFilter;

/**
 * Recovery-only MPEG Audio Layer II renderer. MediaCodec remains first in renderer order; this
 * renderer is appended only to the existing PLATFORM_SOFTWARE_PCM recovery player and advertises
 * support for audio/mpeg-L2 only.
 */
@UnstableApi
final class BundledMp2AudioRenderer extends DecoderAudioRenderer<BundledMp2AudioRenderer.Mp2Decoder> {
  private static final String TAG = "HulkBundledMp2";
  private static final String DECODER_NAME = "hulk-bundled-mp2-jlayer-1.0.2-gdx";
  private static final int NUM_BUFFERS = 16;
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 4096;

  BundledMp2AudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    super(eventHandler, eventListener, audioSink);
  }

  static boolean isAvailable() {
    try {
      return LayerIIDecoder.class.getName() != null;
    } catch (LinkageError error) {
      return false;
    }
  }

  private static boolean isMp2MimeType(@Nullable String sampleMimeType) {
    return sampleMimeType != null && "audio/mpeg-L2".equalsIgnoreCase(sampleMimeType);
  }

  @Override
  public String getName() {
    return "BundledMp2AudioRenderer";
  }

  @Override
  protected @C.FormatSupport int supportsFormatInternal(Format format) {
    String mimeType = format.sampleMimeType;
    if (!isMp2MimeType(mimeType)) {
      return mimeType != null && MimeTypes.isAudio(mimeType)
          ? C.FORMAT_UNSUPPORTED_SUBTYPE
          : C.FORMAT_UNSUPPORTED_TYPE;
    }
    if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
      return C.FORMAT_UNSUPPORTED_DRM;
    }
    if (format.channelCount != Format.NO_VALUE && format.sampleRate != Format.NO_VALUE) {
      Format pcmFormat =
          Util.getPcmFormat(C.ENCODING_PCM_16BIT, format.channelCount, format.sampleRate);
      if (!sinkSupportsFormat(pcmFormat)) {
        return C.FORMAT_UNSUPPORTED_SUBTYPE;
      }
    }
    return C.FORMAT_HANDLED;
  }

  @Override
  public @AdaptiveSupport int supportsMixedMimeTypeAdaptation() {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  protected Mp2Decoder createDecoder(
      Format format, @Nullable androidx.media3.decoder.CryptoConfig cryptoConfig)
      throws Mp2DecoderException {
    int inputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    Log.i(
        TAG,
        "sampleMimeType=audio/mpeg-L2 bundledMp2Available=true decoderPath=BUNDLED_MP2 actualDecoder="
            + DECODER_NAME
            + " recoveryStage=PLATFORM_SOFTWARE_PCM");
    return new Mp2Decoder(format, NUM_BUFFERS, NUM_BUFFERS, inputBufferSize);
  }

  @Override
  protected Format getOutputFormat(Mp2Decoder decoder) {
    return new Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(decoder.getChannelCount())
        .setSampleRate(decoder.getSampleRate())
        .setPcmEncoding(C.ENCODING_PCM_16BIT)
        .build();
  }

  static final class Mp2Decoder
      extends SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, Mp2DecoderException> {
    private final Format inputFormat;
    private QueuedInputStream encodedInput;
    @Nullable private Bitstream bitstream;
    @Nullable private LayerIIDecoder decoder;
    @Nullable private SynthesisFilter leftFilter;
    @Nullable private SynthesisFilter rightFilter;
    @Nullable private OutputBuffer pcmBuffer;
    private int channelCount;
    private int sampleRate;

    Mp2Decoder(
        Format format, int inputBufferCount, int outputBufferCount, int initialInputBufferSize)
        throws Mp2DecoderException {
      super(
          new DecoderInputBuffer[inputBufferCount],
          new SimpleDecoderOutputBuffer[outputBufferCount]);
      if (!isMp2MimeType(format.sampleMimeType)) {
        throw new Mp2DecoderException("Bundled decoder accepts MPEG Audio Layer II only.");
      }
      inputFormat = format;
      encodedInput = new QueuedInputStream();
      decoder = null;
      channelCount = format.channelCount;
      sampleRate = format.sampleRate;
      setInitialInputBufferSize(initialInputBufferSize);
    }

    @Override
    public String getName() {
      return DECODER_NAME;
    }

    @Override
    protected DecoderInputBuffer createInputBuffer() {
      return new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    }

    @Override
    protected SimpleDecoderOutputBuffer createOutputBuffer() {
      return new SimpleDecoderOutputBuffer(this::releaseOutputBuffer);
    }

    @Override
    protected Mp2DecoderException createUnexpectedDecodeException(Throwable error) {
      return new Mp2DecoderException("Unexpected bundled MP2 decode error.", error);
    }

    @Override
    @Nullable
    protected Mp2DecoderException decode(
        DecoderInputBuffer inputBuffer, SimpleDecoderOutputBuffer outputBuffer, boolean reset) {
      if (reset) {
        resetDecoderState();
      }

      ByteBuffer inputData = inputBuffer.data;
      if (inputData == null || !inputData.hasRemaining()) {
        outputBuffer.shouldBeSkipped = true;
        return null;
      }

      encodedInput.append(inputData.duplicate());
      if (bitstream == null) {
        // JLayer's Bitstream keeps parser state and its Layer II decoder keeps synthesis-filter
        // history. Keep both alive across Media3 access units to avoid frame-boundary artifacts.
        bitstream = new Bitstream(encodedInput);
      }

      try {
        Header header = bitstream.readFrame();
        if (header == null) {
          outputBuffer.shouldBeSkipped = true;
          return null;
        }
        if (header.layer() != 2) {
          return new Mp2DecoderException("Rejected non-Layer-II MPEG audio frame.");
        }

        int channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
        int frameSampleRate = header.frequency();
        if (decoder == null) {
          pcmBuffer = new OutputBuffer(channels, /* isBigEndian= */ false);
          leftFilter = new SynthesisFilter(0, 32700.0f, null);
          rightFilter = channels == 2 ? new SynthesisFilter(1, 32700.0f, null) : null;
          decoder = new LayerIIDecoder();
          decoder.create(
              bitstream,
              header,
              leftFilter,
              rightFilter,
              pcmBuffer,
              OutputChannels.BOTH_CHANNELS);
          channelCount = channels;
          sampleRate = frameSampleRate;
        } else if (channelCount != channels || sampleRate != frameSampleRate) {
          return new Mp2DecoderException("MP2 output configuration changed mid-stream.");
        }

        decoder.decodeFrame();
        int outputSize = pcmBuffer.reset();
        if (outputSize <= 0) {
          outputBuffer.shouldBeSkipped = true;
          return null;
        }

        if (channelCount <= 0 || sampleRate <= 0) {
          return new Mp2DecoderException("Invalid MP2 PCM output format.");
        }

        ByteBuffer outputData = outputBuffer.init(inputBuffer.timeUs, outputSize);
        outputData.put(pcmBuffer.getBuffer(), 0, outputSize);
        outputData.position(0);
        outputData.limit(outputSize);
        return null;
      } catch (BitstreamException | javazoom.jl.decoder.DecoderException error) {
        return new Mp2DecoderException("Bundled MP2 decoder rejected the frame.", error);
      } catch (RuntimeException error) {
        return new Mp2DecoderException("Bundled MP2 decoder failed.", error);
      } finally {
        if (bitstream != null) {
          bitstream.closeFrame();
        }
      }
    }

    @Override
    public void release() {
      super.release();
      closeBitstream();
    }

    int getChannelCount() {
      return channelCount;
    }

    int getSampleRate() {
      return sampleRate;
    }

    private void resetDecoderState() {
      closeBitstream();
      encodedInput = new QueuedInputStream();
      bitstream = null;
      decoder = null;
      leftFilter = null;
      rightFilter = null;
      pcmBuffer = null;
      channelCount = inputFormat.channelCount;
      sampleRate = inputFormat.sampleRate;
    }

    private void closeBitstream() {
      if (bitstream == null) {
        return;
      }
      try {
        bitstream.close();
      } catch (BitstreamException ignored) {
        // The backing stream is in-memory; there is no external resource to recover here.
      }
      bitstream = null;
    }
  }

  /** Append-only in-memory source used to preserve JLayer's decoder/filter state between frames. */
  private static final class QueuedInputStream extends InputStream {
    private byte[] data = new byte[8192];
    private int readPosition;
    private int writePosition;

    void append(ByteBuffer source) {
      int length = source.remaining();
      ensureCapacity(length);
      source.get(data, writePosition, length);
      writePosition += length;
    }

    @Override
    public int read() {
      if (readPosition >= writePosition) {
        compactIfEmpty();
        return -1;
      }
      int value = data[readPosition++] & 0xFF;
      compactIfEmpty();
      return value;
    }

    @Override
    public int read(byte[] target, int offset, int length) {
      if (target == null) {
        throw new NullPointerException("target");
      }
      if (offset < 0 || length < 0 || length > target.length - offset) {
        throw new IndexOutOfBoundsException();
      }
      if (length == 0) {
        return 0;
      }
      int available = writePosition - readPosition;
      if (available <= 0) {
        compactIfEmpty();
        return -1;
      }
      int count = Math.min(length, available);
      System.arraycopy(data, readPosition, target, offset, count);
      readPosition += count;
      compactIfEmpty();
      return count;
    }

    @Override
    public int available() {
      return writePosition - readPosition;
    }

    private void ensureCapacity(int appendedLength) {
      int unread = writePosition - readPosition;
      int required = unread + appendedLength;
      if (required <= data.length) {
        if (readPosition > 0 && writePosition + appendedLength > data.length) {
          System.arraycopy(data, readPosition, data, 0, unread);
          readPosition = 0;
          writePosition = unread;
        }
        return;
      }
      int newCapacity = data.length;
      while (newCapacity < required) {
        newCapacity *= 2;
      }
      byte[] replacement = new byte[newCapacity];
      System.arraycopy(data, readPosition, replacement, 0, unread);
      data = replacement;
      readPosition = 0;
      writePosition = unread;
    }

    private void compactIfEmpty() {
      if (readPosition == writePosition) {
        readPosition = 0;
        writePosition = 0;
      }
    }
  }

  static final class Mp2DecoderException extends DecoderException {
    Mp2DecoderException(String message) {
      super(message);
    }

    Mp2DecoderException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
