package com.dylanvann.fastimage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.security.MessageDigest;

/**
 * Stack blur for Glide, aligned with RN Image blurRadius semantics:
 * pixelRadius ~= dpToPx(blurRadius) / 2.
 */
class FastImageBlurTransformation extends BitmapTransformation {
    private static final String ID = "com.dylanvann.fastimage.FastImageBlurTransformation";
    private static final byte[] ID_BYTES = ID.getBytes(CHARSET);

    private final int radius;

    FastImageBlurTransformation(Context context, float blurRadius) {
        float density = context.getResources().getDisplayMetrics().density;
        // Match ReactImageView: divide by 2 to more closely match other platforms.
        this.radius = Math.max(1, Math.round(blurRadius * density / 2f));
    }

    @Override
    protected Bitmap transform(
            @NonNull BitmapPool pool,
            @NonNull Bitmap toTransform,
            int outWidth,
            int outHeight) {
        Bitmap.Config config =
                toTransform.getConfig() != null ? toTransform.getConfig() : Bitmap.Config.ARGB_8888;
        Bitmap bitmap = pool.get(toTransform.getWidth(), toTransform.getHeight(), config);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(toTransform, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        return stackBlur(bitmap, radius);
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID_BYTES);
        messageDigest.update(String.valueOf(radius).getBytes(CHARSET));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FastImageBlurTransformation
                && ((FastImageBlurTransformation) o).radius == radius;
    }

    @Override
    public int hashCode() {
        return ID.hashCode() + radius * 31;
    }

    /**
     * Stack Blur Algorithm by Mario Klingemann.
     */
    private static Bitmap stackBlur(Bitmap sentBitmap, int radius) {
        if (radius < 1) {
            return sentBitmap;
        }

        int w = sentBitmap.getWidth();
        int h = sentBitmap.getHeight();
        int[] pix = new int[w * h];
        sentBitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int[] vmin = new int[Math.max(w, h)];
        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (int i = 0; i < 256 * divsum; i++) {
            dv[i] = i / divsum;
        }

        int yi = 0;
        int yw = 0;
        int[][] stack = new int[div][3];
        int r1 = radius + 1;

        for (int y = 0; y < h; y++) {
            int rsum = 0;
            int gsum = 0;
            int bsum = 0;
            int rinsum = 0;
            int ginsum = 0;
            int binsum = 0;
            int routsum = 0;
            int goutsum = 0;
            int boutsum = 0;

            for (int i = -radius; i <= radius; i++) {
                int p = pix[yi + Math.min(wm, Math.max(i, 0))];
                int[] sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = p & 0x0000ff;
                int rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            int stackpointer = radius;

            for (int x = 0; x < w; x++) {
                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                int stackstart = stackpointer - radius + div;
                int[] sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                int p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = p & 0x0000ff;

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }

        for (int x = 0; x < w; x++) {
            int rsum = 0;
            int gsum = 0;
            int bsum = 0;
            int rinsum = 0;
            int ginsum = 0;
            int binsum = 0;
            int routsum = 0;
            int goutsum = 0;
            int boutsum = 0;
            int yp = -radius * w;
            for (int i = -radius; i <= radius; i++) {
                int yis = Math.max(0, yp) + x;
                int[] sir = stack[i + radius];
                sir[0] = r[yis];
                sir[1] = g[yis];
                sir[2] = b[yis];
                int rbs = r1 - Math.abs(i);
                rsum += r[yis] * rbs;
                gsum += g[yis] * rbs;
                bsum += b[yis] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
                if (i < hm) {
                    yp += w;
                }
            }
            int yiPos = x;
            int stackpointer = radius;
            for (int y = 0; y < h; y++) {
                pix[yiPos] =
                        (0xff000000 & pix[yiPos])
                                | (dv[rsum] << 16)
                                | (dv[gsum] << 8)
                                | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                int stackstart = stackpointer - radius + div;
                int[] sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                int p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yiPos += w;
            }
        }

        sentBitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return sentBitmap;
    }
}
