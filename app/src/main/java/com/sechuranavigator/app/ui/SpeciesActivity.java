// archivo: app/src/main/java/com/sechuranavigator/app/ui/SpeciesActivity.java
package com.sechuranavigator.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.managers.WaypointManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpeciesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_species);

        RecyclerView recycler = findViewById(R.id.recyclerSpecies);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new SpeciesAdapter());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    // ── Adapter de especies ────────────────────────────────────────────────

    class SpeciesAdapter extends RecyclerView.Adapter<SpeciesAdapter.VH> {

        private final List<Map.Entry<String, String>> entries;

        SpeciesAdapter() {
            entries = new ArrayList<>(
                    WaypointManager.SPECIES_COLORS.entrySet());
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Map.Entry<String, String> entry = entries.get(position);
            holder.title.setText(entry.getKey());
            holder.subtitle.setText(entry.getValue());
            holder.subtitle.setTextColor(
                    Color.parseColor(entry.getValue()));

            // Al tocar: selector de color básico
            holder.itemView.setOnClickListener(v -> {
                String[] colors = {"#F59E0B","#60A5FA","#10B981",
                        "#A78BFA","#FB923C","#F472B6","#FACC15","#EF4444"};
                String[] labels = {"Ámbar","Azul","Verde",
                        "Violeta","Naranja","Rosa","Amarillo","Rojo"};

                new androidx.appcompat.app.AlertDialog.Builder(SpeciesActivity.this)
                        .setTitle("Color para " + entry.getKey())
                        .setItems(labels, (d, idx) -> {
                            WaypointManager.SPECIES_COLORS.put(
                                    entry.getKey(), colors[idx]);
                            notifyItemChanged(position);
                        })
                        .show();
            });
        }

        @Override
        public int getItemCount() { return entries.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            VH(View v) {
                super(v);
                title    = v.findViewById(android.R.id.text1);
                subtitle = v.findViewById(android.R.id.text2);
                title.setTextColor(Color.WHITE);
            }
        }
    }
}