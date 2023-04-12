/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.transformer.mh;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import androidx.media3.transformer.AndroidTestUtil;
import androidx.media3.transformer.EncoderUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for video frame processing, outputting to a texture, via {@link
 * DefaultVideoFrameProcessor}.
 *
 * <p>Uses a {@link DefaultVideoFrameProcessor} to process one frame, and checks that the actual
 * output matches expected output, either from a golden file or from another edit.
 */
// TODO(b/263395272): Move this test to effects/mh tests, and remove @TestOnly dependencies.
@RunWith(AndroidJUnit4.class)
public final class DefaultVideoFrameProcessorTextureOutputPixelTest {
  private static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original.png";
  private static final String BITMAP_OVERLAY_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_bitmap_FrameProcessor.png";
  private static final String OVERLAY_PNG_ASSET_PATH = "media/bitmap/input_images/media3test.png";

  private static final String ORIGINAL_HLG10_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original_hlg10.png";
  private static final String ORIGINAL_HDR10_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original_hdr10.png";

  /** Input SDR video of which we only use the first frame. */
  private static final String INPUT_SDR_MP4_ASSET_STRING = "media/mp4/sample.mp4";
  /** Input PQ video of which we only use the first frame. */
  private static final String INPUT_PQ_MP4_ASSET_STRING = "media/mp4/hdr10-720p.mp4";
  /** Input HLG video of which we only use the first frame. */
  private static final String INPUT_HLG10_MP4_ASSET_STRING = "media/mp4/hlg-1080p.mp4";

  private @MonotonicNonNull VideoFrameProcessorTestRunner videoFrameProcessorTestRunner;

  @After
  public void release() {
    if (videoFrameProcessorTestRunner != null) {
      videoFrameProcessorTestRunner.release();
    }
  }

  @Test
  public void noEffects_matchesGoldenFile() throws Exception {
    String testId = "noEffects_matchesGoldenFile";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ null)) {
      return;
    }
    videoFrameProcessorTestRunner = getDefaultFrameProcessorTestRunnerBuilder(testId).build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    Bitmap actualBitmap = videoFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void bitmapOverlay_matchesGoldenFile() throws Exception {
    String testId = "bitmapOverlay_matchesGoldenFile";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        getApplicationContext(),
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ null)) {
      return;
    }
    Bitmap overlayBitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setEffects(new OverlayEffect(ImmutableList.of(bitmapOverlay)))
            .build();
    Bitmap expectedBitmap = readBitmap(BITMAP_OVERLAY_PNG_ASSET_PATH);

    Bitmap actualBitmap = videoFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  @Test
  public void noEffects_hlg10Input_matchesGoldenFile() throws Exception {
    String testId = "noEffects_hlg10Input_matchesGoldenFile";
    Context context = getApplicationContext();
    Format format = MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
    if (!deviceSupportsHdrEditing(format)) {
      recordTestSkipped(context, testId, "No HLG editing support");
      return;
    }
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ format, /* outputFormat= */ null)) {
      return;
    }
    ColorInfo hlg10ColorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_HLG)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setInputColorInfo(hlg10ColorInfo)
            .setOutputColorInfo(hlg10ColorInfo)
            .setVideoAssetPath(INPUT_HLG10_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HLG10_PNG_ASSET_PATH);

    Bitmap actualBitmap = videoFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  @Test
  public void noEffects_hdr10Input_matchesGoldenFile() throws Exception {
    String testId = "noEffects_hdr10Input_matchesGoldenFile";
    Context context = getApplicationContext();
    Format format = MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
    if (!deviceSupportsHdrEditing(format)) {
      recordTestSkipped(context, testId, "No HLG editing support");
      return;
    }
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ format, /* outputFormat= */ null)) {
      return;
    }
    ColorInfo hdr10ColorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .build();
    videoFrameProcessorTestRunner =
        getDefaultFrameProcessorTestRunnerBuilder(testId)
            .setInputColorInfo(hdr10ColorInfo)
            .setOutputColorInfo(hdr10ColorInfo)
            .setVideoAssetPath(INPUT_PQ_MP4_ASSET_STRING)
            .build();
    Bitmap expectedBitmap = readBitmap(ORIGINAL_HDR10_PNG_ASSET_PATH);

    Bitmap actualBitmap = videoFrameProcessorTestRunner.processFirstFrameAndEnd();

    // TODO(b/207848601): Switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceFp16(
            expectedBitmap, actualBitmap);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE_FP16);
  }

  private VideoFrameProcessorTestRunner.Builder getDefaultFrameProcessorTestRunnerBuilder(
      String testId) {
    TextureBitmapReader textureBitmapReader = new TextureBitmapReader();
    DefaultVideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setOnTextureRenderedListener(textureBitmapReader::readBitmapFromTexture)
            .build();
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
        .setVideoAssetPath(INPUT_SDR_MP4_ASSET_STRING)
        .setBitmapReader(textureBitmapReader);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Reads from an OpenGL texture. Only for use on physical devices.
   */
  private static final class TextureBitmapReader
      implements VideoFrameProcessorTestRunner.BitmapReader {
    // TODO(b/239172735): This outputs an incorrect black output image on emulators.
    private boolean useHighPrecisionColorComponents;

    private @MonotonicNonNull Bitmap outputBitmap;

    @Override
    public Surface getSurface(int width, int height, boolean useHighPrecisionColorComponents) {
      this.useHighPrecisionColorComponents = useHighPrecisionColorComponents;
      int texId;
      try {
        texId = GlUtil.createExternalTexture();
      } catch (GlUtil.GlException e) {
        throw new RuntimeException(e);
      }
      SurfaceTexture surfaceTexture = new SurfaceTexture(texId);
      return new Surface(surfaceTexture);
    }

    @Override
    public Bitmap getBitmap() {
      return checkStateNotNull(outputBitmap);
    }

    public void readBitmapFromTexture(GlTextureInfo outputTexture, long presentationTimeUs)
        throws GlUtil.GlException {
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      outputBitmap =
          createBitmapFromCurrentGlFrameBuffer(
              outputTexture.width, outputTexture.height, useHighPrecisionColorComponents);
    }

    private static Bitmap createBitmapFromCurrentGlFrameBuffer(
        int width, int height, boolean useHighPrecisionColorComponents) throws GlUtil.GlException {
      if (!useHighPrecisionColorComponents) {
        return BitmapPixelTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(width, height);
      }
      checkState(Util.SDK_INT > 26, "useHighPrecisionColorComponents only supported on API 26+");
      return BitmapPixelTestUtil.createFp16BitmapFromCurrentGlFramebuffer(width, height);
    }
  }

  private static boolean deviceSupportsHdrEditing(Format format) {
    return !EncoderUtil.getSupportedEncodersForHdrEditing(
            checkNotNull(checkNotNull(format).sampleMimeType), format.colorInfo)
        .isEmpty();
  }
}