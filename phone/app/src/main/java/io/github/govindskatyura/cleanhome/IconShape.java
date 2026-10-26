package io.github.govindskatyura.cleanhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/** Turns any app icon into an iOS tile: one full-bleed squircle, whatever shape the app shipped. */
final class IconShape {
    private IconShape() {
    }

    /** The iOS icon outline is close to the superellipse |x|^5 + |y|^5 = 1. */
    static Path squircle(float size) {
        Path p = new Path();
        float r = size / 2f;
        int steps = 120;
        for (int i = 0; i < steps; i++) {
            double t = 2 * Math.PI * i / steps;
            double c = Math.cos(t);
            double s = Math.sin(t);
            float x = (float) (r + r * Math.signum(c) * Math.pow(Math.abs(c), 0.4));
            float y = (float) (r + r * Math.signum(s) * Math.pow(Math.abs(s), 0.4));
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.close();
        return p;
    }

    static Bitmap render(Drawable d, int size) {
        Bitmap content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(content);
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            // Layers are 108 dp and launchers show the middle 72: let a sixth spill off each side.
            int spill = Math.round(size / 4f);
            Rect bounds = new Rect(-spill, -spill, size + spill, size + spill);
            Drawable bg = a.getBackground();
            Drawable fg = a.getForeground();
            if (bg != null) {
                bg.setBounds(bounds);
                bg.draw(c);
            } else {
                c.drawColor(Color.WHITE);
            }
            if (fg != null) {
                fg.setBounds(bounds);
                fg.draw(c);
            }
        } else {
            Bitmap raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, size, size);
            d.draw(new Canvas(raw));
            if (fillsSquare(raw)) {
                // Already a square tile (MIUI themes most icons this way): stretch it a little past the
                // squircle so its own rounded corners don't show inside ours.
                c.save();
                c.scale(1.08f, 1.08f, size / 2f, size / 2f);
                c.drawBitmap(raw, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
                c.restore();
            } else {
                // A round icon or a loose glyph: sit it on a white tile, as iOS does with web clips. Some
                // (MIUI's Settings gear) carry wide empty margins, so size the drawn part, not the canvas.
                c.drawColor(Color.WHITE);
                Rect box = drawnBounds(raw);
                float room = size * 0.70f;
                float scale = Math.min(room / box.width(), room / box.height());
                float left = (size - box.width() * scale) / 2f - box.left * scale;
                float top = (size - box.height() * scale) / 2f - box.top * scale;
                d.setBounds(Math.round(left), Math.round(top),
                        Math.round(left + size * scale), Math.round(top + size * scale));
                d.draw(c);
            }
            raw.recycle();
        }
        // Mask with a shader rather than clipPath: clipPath has no anti-aliasing and leaves jagged edges.
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawPath(squircle(size), p);
        content.recycle();
        return out;
    }

    /** The box around everything visibly drawn; the whole bitmap when nothing is. */
    private static Rect drawnBounds(Bitmap b) {
        int w = b.getWidth();
        int h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 24) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        return maxX < 0 ? new Rect(0, 0, w, h) : new Rect(minX, minY, maxX + 1, maxY + 1);
    }

    /** Opaque at the edge midpoints and near the corners: a square tile, not a circle or a glyph. */
    private static boolean fillsSquare(Bitmap b) {
        int s = b.getWidth();
        int e = Math.max(1, s / 25);
        int k = Math.round(s * 0.12f);
        int[][] points = {
                {s / 2, e}, {s / 2, s - 1 - e}, {e, s / 2}, {s - 1 - e, s / 2},
                {k, k}, {s - 1 - k, k}, {k, s - 1 - k}, {s - 1 - k, s - 1 - k},
        };
        for (int[] pt : points) {
            if (Color.alpha(b.getPixel(pt[0], pt[1])) < 200) return false;
        }
        return true;
    }
}
