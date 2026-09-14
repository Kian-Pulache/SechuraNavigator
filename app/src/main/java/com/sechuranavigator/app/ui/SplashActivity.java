// archivo: app/src/main/java/com/sechuranavigator/app/ui/SplashActivity.java
package com.sechuranavigator.app.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.sechuranavigator.app.R;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo       = findViewById(R.id.splashLogo);
        TextView  tagline    = findViewById(R.id.splashTagline);
        TextView  version    = findViewById(R.id.splashVersion);

        // ── Animación 1: Logo aparece subiendo desde abajo (300ms delay) ──
        logo.setTranslationY(60f);
        logo.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(200)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        // ── Animación 2: Texto aparece letra a letra (efecto fade-in) ─────
        // Simulamos el efecto con alpha + leve subida, con delay mayor
        tagline.setTranslationY(20f);
        tagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(750)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // ── Animación 3: Versión aparece suavemente al final ──────────────
        version.animate()
                .alpha(1f)
                .setDuration(400)
                .setStartDelay(1100)
                .start();

        // ── Navegar después de 2.2 segundos ───────────────────────────────
        logo.postDelayed(this::navigateNext, 2200);
    }

    private void navigateNext() {
        boolean hasSession =
                com.google.firebase.auth.FirebaseAuth
                        .getInstance().getCurrentUser() != null;

        // Verificar si el usuario eligió modo local anteriormente
        boolean choseLocalMode = getSharedPreferences(
                "sechura_settings", MODE_PRIVATE)
                .getBoolean("chose_local_mode", false);

        Intent intent;
        if (hasSession || choseLocalMode) {
            intent = new Intent(this, HomeActivity.class);
        } else {
            intent = new Intent(this, AuthActivity.class);
        }

        startActivity(intent);
        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out);
        finish();
    }
}