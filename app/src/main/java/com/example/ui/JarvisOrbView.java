package com.example.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Custom JARVIS-style holographic animated AI orb reacting to
 * IDLE, LISTENING, THINKING, SPEAKING, and SLEEPING states.
 */
public class JarvisOrbView extends View {

    public enum OrbState {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
        SLEEPING
    }

    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private OrbState state = OrbState.IDLE;
    private float phase = 0f;
    private float rmsLevel = 0f; // 0..1
    private ValueAnimator animator;

    public JarvisOrbView(Context context) {
        super(context);
        init();
    }

    public JarvisOrbView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JarvisOrbView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        outerGlowPaint.setStyle(Paint.Style.STROKE);

        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(4000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null && !animator.isRunning()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }

    public void setState(OrbState newState) {
        this.state = newState;
        if (newState != OrbState.LISTENING && newState != OrbState.SPEAKING) {
            this.rmsLevel = 0f;
        }
        invalidate();
    }

    public void setAudioLevel(float normalizedLevel) {
        float clamped = Math.max(0f, Math.min(1f, normalizedLevel));
        // Smooth attack/decay
        this.rmsLevel = (this.rmsLevel * 0.45f) + (clamped * 0.55f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float minDim = Math.min(w, h);

        float rad = (float) Math.toRadians(phase);
        float breathe = (float) (0.5 + 0.5 * Math.sin(rad * 2.0));

        int cyan = Color.parseColor("#00E5FF");
        int blue = Color.parseColor("#2979FF");
        int purple = Color.parseColor("#A855F7");

        float coreScale = 0.25f;
        float speedFactor = 1f;
        int primaryColor = cyan;
        int secondaryColor = blue;

        switch (state) {
            case LISTENING:
                coreScale = 0.26f + 0.08f * rmsLevel + 0.02f * breathe;
                primaryColor = cyan;
                secondaryColor = purple;
                speedFactor = 1.8f;
                break;
            case THINKING:
                coreScale = 0.24f + 0.03f * breathe;
                primaryColor = purple;
                secondaryColor = cyan;
                speedFactor = 3.2f;
                break;
            case SPEAKING:
                float speakPulse = (float) Math.abs(Math.sin(rad * 4.5));
                coreScale = 0.26f + 0.06f * speakPulse + 0.04f * rmsLevel;
                primaryColor = blue;
                secondaryColor = cyan;
                speedFactor = 2.2f;
                break;
            case SLEEPING:
                coreScale = 0.20f + 0.01f * breathe;
                primaryColor = Color.parseColor("#475A80");
                secondaryColor = Color.parseColor("#2A3B61");
                speedFactor = 0.4f;
                break;
            case IDLE:
            default:
                coreScale = 0.23f + 0.025f * breathe;
                primaryColor = cyan;
                secondaryColor = blue;
                speedFactor = 1.0f;
                break;
        }

        float coreRadius = minDim * coreScale;

        // Soft ambient halo
        outerGlowPaint.setStrokeWidth(minDim * 0.04f);
        outerGlowPaint.setColor(primaryColor);
        outerGlowPaint.setAlpha(state == OrbState.SLEEPING ? 35 : (int) (70 + 60 * breathe));
        canvas.drawCircle(cx, cy, coreRadius * 1.45f, outerGlowPaint);

        // Glowing radial sphere core
        RadialGradient coreGradient = new RadialGradient(
                cx, cy, coreRadius * 1.15f,
                new int[]{Color.WHITE, primaryColor, secondaryColor, Color.TRANSPARENT},
                new float[]{0f, 0.42f, 0.78f, 1f},
                Shader.TileMode.CLAMP
        );
        corePaint.setShader(coreGradient);
        canvas.drawCircle(cx, cy, coreRadius * 1.15f, corePaint);

        // Rotating orbital rings
        float ring1Radius = minDim * (0.37f + 0.03f * rmsLevel);
        arcBounds.set(cx - ring1Radius, cy - ring1Radius, cx + ring1Radius, cy + ring1Radius);
        ringPaint.setStrokeWidth(minDim * 0.026f);
        ringPaint.setShader(new SweepGradient(cx, cy,
                new int[]{primaryColor, secondaryColor, purple, primaryColor}, null));
        float angle1 = (phase * speedFactor) % 360f;
        canvas.drawArc(arcBounds, angle1, 130f, false, ringPaint);
        canvas.drawArc(arcBounds, angle1 + 180f, 110f, false, ringPaint);

        // Second counter-rotating outer ring
        float ring2Radius = minDim * (0.44f + 0.02f * breathe);
        arcBounds.set(cx - ring2Radius, cy - ring2Radius, cx + ring2Radius, cy + ring2Radius);
        ringPaint.setStrokeWidth(minDim * 0.016f);
        ringPaint.setAlpha(state == OrbState.SLEEPING ? 80 : 190);
        float angle2 = (-phase * speedFactor * 0.8f) % 360f;
        canvas.drawArc(arcBounds, angle2, 95f, false, ringPaint);
        canvas.drawArc(arcBounds, angle2 + 165f, 80f, false, ringPaint);
        ringPaint.setShader(null);
        ringPaint.setAlpha(255);
    }
}
