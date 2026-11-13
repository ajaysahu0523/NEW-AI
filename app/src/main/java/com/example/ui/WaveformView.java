package com.example.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Animated 24-bar sound waveform reacting to speech recognition RMS
 * and TTS speaking states.
 */
public class WaveformView extends View {

    private static final int BAR_COUNT = 24;
    private final float[] barHeights = new float[BAR_COUNT];
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean active = false;
    private boolean speakingMode = false;
    private float targetLevel = 0.1f;
    private float animPhase = 0f;
    private ValueAnimator animator;

    public WaveformView(Context context) {
        super(context);
        init();
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < BAR_COUNT; i++) {
            barHeights[i] = 0.12f;
        }
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(1200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            animPhase = (float) a.getAnimatedValue();
            updateBars();
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

    public void setWaveformState(boolean isActive, boolean isSpeaking) {
        this.active = isActive;
        this.speakingMode = isSpeaking;
        if (!isActive) {
            this.targetLevel = 0.1f;
        }
        invalidate();
    }

    public void pushAmplitude(float normalizedLevel) {
        this.targetLevel = Math.max(0.08f, Math.min(1f, normalizedLevel));
    }

    private void updateBars() {
        for (int i = 0; i < BAR_COUNT; i++) {
            float centerDist = Math.abs(i - (BAR_COUNT - 1) / 2f) / (BAR_COUNT / 2f);
            float envelope = 1.0f - 0.55f * centerDist;
            float wave;
            if (!active) {
                wave = 0.10f + 0.05f * (float) Math.sin(animPhase + i * 0.45f);
            } else if (speakingMode) {
                wave = (0.28f + 0.65f * (float) Math.abs(Math.sin(animPhase * 2.2f + i * 0.55f))) * envelope;
            } else {
                float ripple = 0.5f + 0.5f * (float) Math.sin(animPhase * 2.6f + i * 0.6f);
                wave = Math.max(0.14f, targetLevel * (0.45f + 0.55f * ripple) * envelope);
            }
            barHeights[i] = barHeights[i] * 0.5f + wave * 0.5f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        barPaint.setShader(new LinearGradient(
                0f, 0f, w, 0f,
                new int[]{
                        Color.parseColor("#00E5FF"),
                        Color.parseColor("#2979FF"),
                        Color.parseColor("#A855F7")
                },
                null,
                Shader.TileMode.CLAMP
        ));

        float slotWidth = w / BAR_COUNT;
        float barWidth = Math.max(3f, slotWidth * 0.52f);
        float radius = barWidth * 0.5f;
        float midY = h * 0.5f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float barH = Math.max(barWidth, barHeights[i] * h * 0.9f);
            float left = i * slotWidth + (slotWidth - barWidth) * 0.5f;
            float top = midY - barH * 0.5f;
            float bottom = midY + barH * 0.5f;
            canvas.drawRoundRect(left, top, left + barWidth, bottom, radius, radius, barPaint);
        }
    }
}
