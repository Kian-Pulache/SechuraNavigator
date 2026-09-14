// archivo: app/src/main/java/com/sechuranavigator/app/ui/PremiumActivity.java
package com.sechuranavigator.app.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.managers.PremiumManager;

public class PremiumActivity extends AppCompatActivity
        implements PremiumManager.OnPremiumListener {

    private PremiumManager premiumManager;
    private TextView tvPriceMonthly, tvPriceYearly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_premium);

        tvPriceMonthly = findViewById(R.id.tvPriceMonthly);
        tvPriceYearly  = findViewById(R.id.tvPriceYearly);

        premiumManager = new PremiumManager(this);
        premiumManager.setListener(this);

        // Actualizar precios desde Google Play cuando carguen
        // (se actualizan automáticamente via BillingClient)

        // Botón volver
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Botón plan mensual
        findViewById(R.id.cardMonthly).setOnClickListener(v ->
                //Descomentar las dos lineas de abajo cuando la app se suba en play store
                //premiumManager.launchPurchaseFlow(this,
                //        PremiumManager.PRODUCT_MONTHLY));
                showComingSoonDialog());

        // Botón plan anual
        findViewById(R.id.cardYearly).setOnClickListener(v ->
                //Descomentar las dos lineas de abajo cuando la app se suba en play store
                //premiumManager.launchPurchaseFlow(this,
                //        PremiumManager.PRODUCT_YEARLY));
                showComingSoonDialog());

        // Botón continuar gratis
        findViewById(R.id.btnContinueFree).setOnClickListener(v ->
                finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        premiumManager.destroy();
    }

    @Override
    public void onPremiumStatusChanged(boolean isPremium) {
        if (isPremium) {
            Toast.makeText(this,
                    "¡Ya tienes el plan Pro activo! 🎉",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onPurchaseSuccess() {
        Toast.makeText(this,
                "✓ Suscripción activada. ¡Bienvenido a Pro!",
                Toast.LENGTH_LONG).show();
        finish();
    }

    /**
     * Muestra un diálogo informativo mientras los pagos no estén activos.
     * Reemplazar showComingSoonDialog() por launchPurchaseFlow() cuando
     * la app esté publicada en Google Play con los productos configurados.
     */
    private void showComingSoonDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⭐ Plan Pro — Próximamente")
                .setMessage(
                        "Los pagos estarán disponibles muy pronto.\n\n" +
                                "Mientras tanto, puedes seguir usando Sechura Navigator " +
                                "de forma gratuita con todas las funciones básicas.\n\n" +
                                "¡Gracias por tu interés en el plan Pro!")
                .setPositiveButton("Entendido", null)
                .show();
    }
}