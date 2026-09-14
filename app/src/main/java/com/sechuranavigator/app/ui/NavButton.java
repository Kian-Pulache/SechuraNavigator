// archivo: app/src/main/java/com/sechuranavigator/app/ui/NavButton.java
package com.sechuranavigator.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NavButton extends LinearLayout {

    private TextView iconView;
    private TextView labelView;

    public NavButton(Context context) {
        super(context);
        init();
    }

    public NavButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setBackgroundColor(Color.parseColor("#0D1F3C"));
        setPadding(dp(8), dp(10), dp(8), dp(10));

        // Borde redondeado con ripple efecto táctil
        setClickable(true);
        setFocusable(true);

        iconView = new TextView(getContext());
        iconView.setTextSize(22f);
        iconView.setGravity(Gravity.CENTER);
        addView(iconView);

        labelView = new TextView(getContext());
        labelView.setTextColor(Color.parseColor("#9CA3AF"));
        labelView.setTextSize(9f);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, dp(3), 0, 0);
        addView(labelView);
    }

    public void setIcon(String emoji) {
        iconView.setText(emoji);
    }

    public void setLabel(String label) {
        labelView.setText(label);
    }

    /** Resalta el botón como acción principal (fondo ámbar) */
    public void setPrimary(boolean primary) {
        if (primary) {
            setBackgroundColor(Color.parseColor("#F59E0B"));
            labelView.setTextColor(Color.parseColor("#0A1628"));
            labelView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        } else {
            setBackgroundColor(Color.parseColor("#0D1F3C"));
            labelView.setTextColor(Color.parseColor("#9CA3AF"));
        }
    }

    private int dp(int value) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}