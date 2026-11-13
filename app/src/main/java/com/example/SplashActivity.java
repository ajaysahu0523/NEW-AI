package com.example;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Animated JARVIS-style splash screen for SUBH AI.
 */
public class SplashActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable launchMainRunnable = () -> {
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView orb = findViewById(R.id.iv_splash_orb);
        TextView title = findViewById(R.id.tv_splash_title);
        TextView subtitle = findViewById(R.id.tv_splash_subtitle);
        TextView hindiTag = findViewById(R.id.tv_splash_hindi);

        ScaleAnimation scale = new ScaleAnimation(
                0.82f, 1.02f, 0.82f, 1.02f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scale.setDuration(1200);
        scale.setFillAfter(true);
        orb.startAnimation(scale);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(1000);
        fadeIn.setFillAfter(true);
        title.startAnimation(fadeIn);
        subtitle.startAnimation(fadeIn);
        hindiTag.startAnimation(fadeIn);

        handler.postDelayed(launchMainRunnable, 1500L);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(launchMainRunnable);
        super.onDestroy();
    }
}
