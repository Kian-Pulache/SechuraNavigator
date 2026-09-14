// archivo: app/src/main/java/com/sechuranavigator/app/ui/WaypointListActivity.java
package com.sechuranavigator.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.managers.WaypointManager;

public class WaypointListActivity extends AppCompatActivity {

    private TextView       tvTitle;
    private EditText       etSearch;
    private RecyclerView   recyclerView;
    private TextView       tvEmpty;
    private WaypointAdapter adapter;
    private WaypointManager waypointManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waypoint_list);

        tvTitle      = findViewById(R.id.tvTitle);
        etSearch     = findViewById(R.id.etSearch);
        recyclerView = findViewById(R.id.recyclerWaypoints);
        tvEmpty      = findViewById(R.id.tvEmpty);

        waypointManager = new WaypointManager(this);

        setupRecyclerView();
        setupSearch();
        loadWaypoints();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new WaypointAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(waypoint -> {
            // Mostrar opciones: Navegar o Ver detalle
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(waypoint.name != null ? waypoint.name : "Sin nombre")
                    .setItems(new String[]{"🧭 Navegar a este punto", "📍 Ver detalle"},
                            (d, which) -> {
                                if (which == 0) {
                                    Intent intent = new Intent(this,
                                            NavigationActivity.class);
                                    intent.putExtra(
                                            NavigationActivity.EXTRA_WAYPOINT_ID,
                                            waypoint.id);
                                    startActivity(intent);
                                } else {
                                    Intent intent = new Intent(this, WaypointDetailActivity.class);
                                    intent.putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_ID,
                                            waypoint.id);
                                    startActivity(intent);
                                }
                            })
                    .show();
        });

        adapter.setOnDeleteClickListener(waypoint ->
                new AlertDialog.Builder(this)
                        .setTitle("Eliminar waypoint")
                        .setMessage("¿Eliminar \"" + waypoint.name + "\"? Esta acción no se puede deshacer.")
                        .setPositiveButton("Eliminar", (d, w) -> {
                            waypointManager.delete(waypoint.id);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show()
        );
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence query, int st, int b, int c) {
                if (query.length() == 0) {
                    loadWaypoints();
                } else {
                    waypointManager.searchLive(query.toString())
                            .observe(WaypointListActivity.this, list -> {
                                adapter.submitList(list);
                                updateEmptyState(list == null || list.isEmpty());
                                updateTitle(list != null ? list.size() : 0);
                            });
                }
            }
        });
    }

    private void loadWaypoints() {
        waypointManager.getAllLive().observe(this, list -> {
            adapter.submitList(list);
            updateEmptyState(list == null || list.isEmpty());
            updateTitle(list != null ? list.size() : 0);
        });
    }

    private void updateEmptyState(boolean isEmpty) {
        recyclerView.setVisibility(isEmpty ? View.GONE  : View.VISIBLE);
        tvEmpty.setVisibility(     isEmpty ? View.VISIBLE : View.GONE);
    }

    private void updateTitle(int count) {
        tvTitle.setText("Mis puntos (" + count + ")");
    }
}