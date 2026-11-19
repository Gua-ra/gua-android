/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

// Runs on an Android 13+ device or emulator through app_process, with no APK: builds a RuntimeShader
// from each .agsl file (Android's own SkSL compiler) and sets every uniform for each golden case; from
// Android 14 it also draws each case on the GPU with HardwareBufferRenderer. Software canvases refuse
// RuntimeShader, so there is no CPU drawing. Driven by check-agsl-device.mjs (javac, d8, adb).
// Arguments: <work dir> <width> <height> <id,id,...> <case> ... where a case is name:time:pulse:dark and
// each drawing is written to <work dir>/gpu/<id>-<name>.png.

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.HardwareBufferRenderer;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.hardware.HardwareBuffer;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class AgslDeviceCheck {
    private static final ColorSpace SRGB = ColorSpace.get(ColorSpace.Named.SRGB);

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        int width = Integer.parseInt(args[1]);
        int height = Integer.parseInt(args[2]);
        String[] ids = args[3].split(",");
        boolean draw = Build.VERSION.SDK_INT >= 34;
        System.out.println("device API " + Build.VERSION.SDK_INT + (draw ? "" : ": compiles and uniforms only, drawing needs API 34"));
        for (String id : ids) {
            RuntimeShader shader;
            try {
                shader = new RuntimeShader(new String(Files.readAllBytes(new File(dir, id + ".agsl").toPath()), StandardCharsets.UTF_8));
            } catch (Throwable e) {
                System.out.println("FAIL " + id + " compile: " + e);
                continue;
            }
            boolean ok = true;
            for (int i = 4; i < args.length && ok; i++) {
                String[] c = args[i].split(":");
                try {
                    // Throws IllegalArgumentException for a uniform SkSL does not know, including one it dropped.
                    shader.setFloatUniform("u_res", width, height);
                    shader.setFloatUniform("u_time", Float.parseFloat(c[1]));
                    shader.setFloatUniform("u_pulse", Float.parseFloat(c[2]));
                    shader.setFloatUniform("u_dark", Float.parseFloat(c[3]));
                    shader.setFloatUniform("u_tilt", 0f, 0f);
                    shader.setFloatUniform("u_origin", 0f, 0f);
                    Paint paint = new Paint();
                    paint.setShader(shader);
                    if (draw) write(drawGpu(paint, width, height), new File(dir, "gpu/" + id + "-" + c[0] + ".png"));
                } catch (Throwable e) {
                    System.out.println("FAIL " + id + " " + c[0] + ": " + e);
                    ok = false;
                }
            }
            if (ok) System.out.println("ok " + id);
        }
    }

    private static Bitmap drawGpu(Paint paint, int width, int height) throws Exception {
        RenderNode node = new RenderNode("gua-agsl-check");
        node.setPosition(0, 0, width, height);
        RecordingCanvas canvas = node.beginRecording(width, height);
        canvas.drawRect(0, 0, width, height, paint);
        node.endRecording();
        HardwareBuffer buffer = HardwareBuffer.create(width, height, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        HardwareBufferRenderer renderer = new HardwareBufferRenderer(buffer);
        try {
            renderer.setContentRoot(node);
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<HardwareBufferRenderer.RenderResult> result = new AtomicReference<>();
            renderer.obtainRenderRequest().setColorSpace(SRGB).draw(Runnable::run, r -> {
                result.set(r);
                done.countDown();
            });
            if (!done.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("GPU draw timed out");
            if (result.get().getStatus() != HardwareBufferRenderer.RenderResult.SUCCESS) {
                throw new IllegalStateException("GPU draw status " + result.get().getStatus());
            }
            result.get().getFence().awaitForever();
            Bitmap wrapped = Bitmap.wrapHardwareBuffer(buffer, SRGB);
            return wrapped.copy(Bitmap.Config.ARGB_8888, false);
        } finally {
            renderer.close();
            buffer.close();
        }
    }

    private static void write(Bitmap bitmap, File file) throws Exception {
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
