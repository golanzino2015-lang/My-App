package com.questionbubble.app;

import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;

/**
 * Paints the monochrome orb: a glassy black sphere with drifting inner light, a specular
 * highlight, a rim light that slowly circles, and (while working) a bright comet around the edge.
 * Shaders are built once per radius and moved with local matrices, so a frame allocates nothing.
 */
final class OrbRenderer {

    private final float density;
    private float builtR = -1f;

    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aurora = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blob2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignette = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bounce = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint comet = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();

    OrbRenderer(float density) {
        this.density = density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, 1f * density));
        comet.setStyle(Paint.Style.STROKE);
        comet.setStrokeWidth(1.8f * density);
    }

    private void build(float r) {
        builtR = r;
        Shader.TileMode C = Shader.TileMode.CLAMP;
        shadow.setShader(new RadialGradient(0, r * 0.15f, r * 1.25f,
                new int[]{0x80000000, 0x66000000, 0x00000000}, new float[]{0f, 0.6f, 1f}, C));
        glow.setShader(new RadialGradient(0, 0, r * 1.38f,
                new int[]{0x00FFFFFF, 0x00FFFFFF, 0x45FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.69f, 0.73f, 1f}, C));
        body.setShader(new RadialGradient(-0.34f * r, -0.46f * r, 1.7f * r,
                Theme.ORB,
                new float[]{0f, 0.34f, 0.74f, 1f}, C));

        Shader sweep = new SweepGradient(0, 0,
                new int[]{0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x1FFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.16f, 0.34f, 0.56f, 0.74f, 1f});
        Shader centerMask = new RadialGradient(0, 0, r,
                new int[]{0x00000000, 0xFF000000, 0xFF000000}, new float[]{0f, 0.55f, 1f}, C);
        aurora.setShader(new ComposeShader(sweep, centerMask, PorterDuff.Mode.DST_IN));

        blob.setShader(new RadialGradient(0, 0, 0.72f * r, new int[]{0x40FFFFFF, 0x00FFFFFF}, null, C));
        blob2.setShader(new RadialGradient(0, 0, 0.5f * r, new int[]{0x26FFFFFF, 0x00FFFFFF}, null, C));
        core.setShader(new RadialGradient(0, 0, 0.8f * r, new int[]{0x66FFFFFF, 0x00FFFFFF}, null, C));
        vignette.setShader(new RadialGradient(0, 0, r,
                new int[]{0x00000000, 0x00000000, 0xB3000000}, new float[]{0f, 0.64f, 1f}, C));
        spec.setShader(new RadialGradient(0, 0, 0.5f * r,
                new int[]{0xD9FFFFFF, 0x47FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.38f, 1f}, C));
        bounce.setShader(new RadialGradient(0, 0.98f * r, 0.66f * r,
                new int[]{0x40FFFFFF, 0x00FFFFFF}, null, C));
        rim.setShader(new SweepGradient(0, 0,
                new int[]{0x1AFFFFFF, 0xA6FFFFFF, 0x1AFFFFFF, 0x4DFFFFFF, 0x1AFFFFFF},
                new float[]{0f, 0.12f, 0.4f, 0.68f, 1f}));
        comet.setShader(new SweepGradient(0, 0,
                new int[]{0x00FFFFFF, 0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 0.995f, 1f}));
    }

    /**
     * @param t      time in seconds (drives all motion)
     * @param energy 0 = calm, 1 = working
     * @param glowA  0..1 soft outer halo
     */
    void draw(Canvas c, float cx, float cy, float r, float t, float energy, float glowA, boolean drawShadow) {
        if (r <= 0) return;
        if (Math.abs(r - builtR) > 0.5f) build(r);
        c.save();
        c.translate(cx, cy);

        if (drawShadow) c.drawCircle(0, r * 0.15f, r * 1.25f, shadow);
        if (glowA > 0.01f) {
            glow.setAlpha(Math.round(255 * Math.min(1f, glowA)));
            c.drawCircle(0, 0, r * 1.38f, glow);
        }

        c.drawCircle(0, 0, r, body);

        m.setRotate(t * 26f);
        aurora.getShader().setLocalMatrix(m);
        aurora.setAlpha(Math.round(255 * (0.75f + 0.25f * energy)));
        c.drawCircle(0, 0, r, aurora);

        m.setTranslate(0.36f * r * (float) Math.cos(t * 0.8), 0.36f * r * (float) Math.sin(t * 1.1));
        blob.getShader().setLocalMatrix(m);
        c.drawCircle(0, 0, r, blob);

        m.setTranslate(0.42f * r * (float) Math.cos(-t * 1.3 + 2), 0.3f * r * (float) Math.sin(-t * 0.9 + 1));
        blob2.getShader().setLocalMatrix(m);
        c.drawCircle(0, 0, r, blob2);

        if (energy > 0.01f) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(t * 5.0);
            core.setAlpha(Math.round(255 * energy * pulse));
            c.drawCircle(0, 0, r, core);
        }

        c.drawCircle(0, 0, r, vignette);
        c.drawCircle(0, 0, r, bounce);

        c.save();
        c.translate(-0.3f * r, -0.4f * r);
        c.rotate(-35f);
        c.scale(1f, 0.6f);
        c.drawCircle(0, 0, 0.5f * r, spec);
        c.restore();

        m.setRotate(-t * 22f + 220f);
        rim.getShader().setLocalMatrix(m);
        c.drawCircle(0, 0, r - rim.getStrokeWidth() / 2f, rim);

        if (energy > 0.01f) {
            m.setRotate(t * 160f);
            comet.getShader().setLocalMatrix(m);
            comet.setAlpha(Math.round(255 * Math.min(1f, energy)));
            c.drawCircle(0, 0, r - comet.getStrokeWidth() / 2f, comet);
        }
        c.restore();
    }
}
