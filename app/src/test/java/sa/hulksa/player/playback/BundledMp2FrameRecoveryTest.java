package sa.hulksa.player.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.Format;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.decoder.SimpleDecoderOutputBuffer;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import org.junit.Test;

/**
 * Codec-level regression coverage for the bounded bundled MP2 frame recovery contract.
 *
 * <p>The frames here are synthetic MPEG-1 Layer II frames built by hand (no provider content): a
 * "valid" frame allocates one subband with zero-valued samples, and a "corrupt" frame reuses the
 * same header but requests maximal sample codewords for every allocated subband, which overruns
 * JLayer's fixed frame bit buffer exactly like the physically observed isolated frame failure.
 */
public final class BundledMp2FrameRecoveryTest {
  private static final int HEADER = 0xFFFD84C0; // MPEG1 Layer II, 128 kbps, 48 kHz, mono, no CRC
  private static final int FRAME_BODY_BYTES = 380;
  private static final int CHANNEL_COUNT = 1;
  private static final int SAMPLE_RATE = 48000;

  @Test
  public void validMp2FrameDecodesPcm() throws Exception {
    Mp2DecoderHarness harness = new Mp2DecoderHarness();
    try {
      SimpleDecoderOutputBuffer output = outputBuffer();
      assertNull(harness.decoder.decode(input(validFrame(), 0L), output, false));
      assertFalse(output.shouldBeSkipped);
      assertNotNull(output.data);
      assertTrue(output.data.remaining() > 0);
    } finally {
      harness.close();
    }
  }

  @Test
  public void isolatedFrameFailureRecoversAndDecodingContinues() throws Exception {
    Mp2DecoderHarness harness = new Mp2DecoderHarness();
    try {
      assertNull(harness.decoder.decode(input(validFrame(), 0L), outputBuffer(), false));

      SimpleDecoderOutputBuffer failed = outputBuffer();
      assertNull(
          "an isolated frame failure must not terminate the decoder",
          harness.decoder.decode(input(corruptFrame(), 20_000L), failed, false));
      assertTrue(failed.shouldBeSkipped);

      SimpleDecoderOutputBuffer resumed = outputBuffer();
      assertNull(harness.decoder.decode(input(validFrame(), 40_000L), resumed, false));
      assertFalse(resumed.shouldBeSkipped);
      assertNotNull(resumed.data);
      assertTrue(resumed.data.remaining() > 0);
    } finally {
      harness.close();
    }
  }

  @Test
  public void consecutiveFrameFailuresStillFailNormally() throws Exception {
    Mp2DecoderHarness harness = new Mp2DecoderHarness();
    try {
      assertNull(harness.decoder.decode(input(validFrame(), 0L), outputBuffer(), false));
      assertNull(harness.decoder.decode(input(corruptFrame(), 20_000L), outputBuffer(), false));

      BundledMp2AudioRenderer.Mp2DecoderException fatal =
          harness.decoder.decode(input(corruptFrame(), 40_000L), outputBuffer(), false);

      assertNotNull("persistent corruption must still fail", fatal);
      assertTrue(fatal.getCause() instanceof ArrayIndexOutOfBoundsException);
    } finally {
      harness.close();
    }
  }

  @Test
  public void flushResetRemainsDeterministicAfterRecovery() throws Exception {
    Mp2DecoderHarness harness = new Mp2DecoderHarness();
    try {
      assertNull(harness.decoder.decode(input(validFrame(), 0L), outputBuffer(), false));
      assertNull(harness.decoder.decode(input(corruptFrame(), 20_000L), outputBuffer(), false));

      SimpleDecoderOutputBuffer afterReset = outputBuffer();
      assertNull(harness.decoder.decode(input(validFrame(), 40_000L), afterReset, true));
      assertFalse(afterReset.shouldBeSkipped);
      assertNotNull(afterReset.data);
      assertTrue(afterReset.data.remaining() > 0);
    } finally {
      harness.close();
    }
  }

  private static final class Mp2DecoderHarness {
    final BundledMp2AudioRenderer.Mp2Decoder decoder;

    Mp2DecoderHarness() throws BundledMp2AudioRenderer.Mp2DecoderException {
      Format format =
          new Format.Builder()
              .setSampleMimeType("audio/mpeg-L2")
              .setChannelCount(CHANNEL_COUNT)
              .setSampleRate(SAMPLE_RATE)
              .setMaxInputSize(4096)
              .build();
      decoder = new BundledMp2AudioRenderer.Mp2Decoder(format, 4, 4, 4096);
    }

    void close() {
      decoder.release();
    }
  }

  private static DecoderInputBuffer input(byte[] frame, long timeUs) {
    DecoderInputBuffer buffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    buffer.data = ByteBuffer.wrap(frame);
    buffer.timeUs = timeUs;
    return buffer;
  }

  private static SimpleDecoderOutputBuffer outputBuffer() {
    return new SimpleDecoderOutputBuffer(buffer -> {});
  }

  private static byte[] validFrame() {
    BitWriter writer = new BitWriter();
    writer.write(2, 4); // subband 0: allocation 2 (valid, codelength 3)
    for (int i = 1; i <= 10; i++) writer.write(0, 4);
    for (int i = 11; i <= 22; i++) writer.write(0, 3);
    for (int i = 23; i <= 26; i++) writer.write(0, 2);
    writer.write(0, 2); // SCFSI
    for (int i = 0; i < 3; i++) writer.write(0, 6); // scalefactors
    for (int group = 0; group < 12; group++) {
      for (int sample = 0; sample < 3; sample++) writer.write(0, 3); // samples
    }
    return frame(writer.finish(FRAME_BODY_BYTES));
  }

  private static byte[] corruptFrame() {
    BitWriter writer = new BitWriter();
    for (int i = 0; i <= 10; i++) writer.write(15, 4);
    for (int i = 11; i <= 22; i++) writer.write(7, 3);
    for (int i = 23; i <= 26; i++) writer.write(3, 2);
    return frame(writer.finish(FRAME_BODY_BYTES));
  }

  private static byte[] frame(byte[] body) {
    byte[] frame = new byte[4 + body.length];
    frame[0] = (byte) (HEADER >>> 24);
    frame[1] = (byte) (HEADER >>> 16);
    frame[2] = (byte) (HEADER >>> 8);
    frame[3] = (byte) HEADER;
    System.arraycopy(body, 0, frame, 4, body.length);
    return frame;
  }

  /** Minimal MSB-first bit writer used to construct deterministic synthetic MP2 frames. */
  private static final class BitWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private int current;
    private int bitCount;

    void write(int value, int bits) {
      for (int i = bits - 1; i >= 0; i--) {
        current = (current << 1) | ((value >>> i) & 1);
        if (++bitCount == 8) {
          out.write(current);
          current = 0;
          bitCount = 0;
        }
      }
    }

    byte[] finish(int padToBytes) {
      if (bitCount > 0) {
        out.write(current << (8 - bitCount));
        current = 0;
        bitCount = 0;
      }
      byte[] raw = out.toByteArray();
      byte[] padded = new byte[Math.max(raw.length, padToBytes)];
      System.arraycopy(raw, 0, padded, 0, raw.length);
      return padded;
    }
  }
}
