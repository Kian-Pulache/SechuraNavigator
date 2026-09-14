// archivo: app/src/main/java/com/sechuranavigator/app/ui/DataCell.java
package com.sechuranavigator.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DataCell extends LinearLayout {

    private TextView labelView;
    private TextView valueView;

    // ── Constructores requeridos por Android ──────────────────────────────
    public DataCell(Context context) {
        super(context);
        init();
    }

    public DataCell(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DataCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    // ── Construcción de la vista ──────────────────────────────────────────
    private void init() {
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#0A1628"));
        setPadding(dp(8), dp(6), dp(8), dp(6));
        setGravity(Gravity.START);

        // Etiqueta superior (ej. "LATITUD")
        labelView = new TextView(getContext());
        labelView.setTextColor(Color.parseColor("#6B7280"));
        labelView.setTextSize(9f);
        labelView.setAllCaps(false);
        addView(labelView);

        // Valor principal (ej. "-5.628431°")
        valueView = new TextView(getContext());
        valueView.setTextColor(Color.parseColor("#F59E0B")); // ámbar por defecto
        valueView.setTextSize(13f);
        valueView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        addView(valueView);
    }

    // ── API pública ───────────────────────────────────────────────────────

    public void setLabel(String label) {
        labelView.setText(label);
    }

    public void setValue(String value) {
        valueView.setText(value);
    }

    /**
     * Permite cambiar el color del valor. Útil para indicar estados:
     * verde = buena precisión, rojo = mala señal, etc.
     */
    public void setValueColor(String hexColor) {
        valueView.setTextColor(Color.parseColor(hexColor));
    }

    // ── Utilidad: convertir dp a píxeles ─────────────────────────────────
    private int dp(int value) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}