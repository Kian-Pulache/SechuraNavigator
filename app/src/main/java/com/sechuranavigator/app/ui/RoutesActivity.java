// archivo: app/src/main/java/com/sechuranavigator/app/ui/RoutesActivity.java
package com.sechuranavigator.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.data.TrackRepository;

public class RoutesActivity extends AppCompatActivity {

    private TextView     tvTitle;
    private RecyclerView recyclerView;
    private TextView     tvEmpty;
    private RouteAdapter adapter;
    private TrackRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_routes);

        tvTitle      = findViewById(R.id.tvTitle);
        recyclerView = findViewById(R.id.recyclerRoutes);
        tvEmpty      = findViewById(R.id.tvEmpty);
        repository   = new TrackRepository(this);

        setupRecyclerView();
        loadRoutes();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new RouteAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(track -> {
            Intent intent = new Intent(this, RouteDetailActivity.class);
            intent.putExtra("track_id", track.id);
            startActivity(intent);
        });

        adapter.setOnDeleteClickListener(track ->
                new AlertDialog.Builder(this)
                        .setTitle("Eliminar ruta")
                        .setMessage("¿Eliminar \"" + track.name + "\"? No se puede deshacer.")
                        .setPositiveButton("Eliminar", (d, w) -> {
                            repository.deleteTrack(track.id);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show()
        );
    }

    private void loadRoutes() {
        repository.getAllTracksLive().observe(this, tracks -> {
            adapter.submitList(tracks);
            boolean empty = tracks == null || tracks.isEmpty();
            recyclerView.setVisibility(empty ? View.GONE  : View.VISIBLE);
            tvEmpty.setVisibility(     empty ? View.VISIBLE : View.GONE);
            if (!empty)
                tvTitle.setText("Mis rutas (" + tracks.size() + ")");
        });
    }
}