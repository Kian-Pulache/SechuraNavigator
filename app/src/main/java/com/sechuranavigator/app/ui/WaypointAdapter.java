// archivo: app/src/main/java/com/sechuranavigator/app/ui/WaypointAdapter.java
package com.sechuranavigator.app.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;
import com.sechuranavigator.app.managers.WaypointManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WaypointAdapter extends RecyclerView.Adapter<WaypointAdapter.ViewHolder> {

    // ── Interfaces de callback ─────────────────────────────────────────────
    public interface OnItemClickListener {
        void onItemClick(WaypointEntity waypoint);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(WaypointEntity waypoint);
    }

    // ── Datos y listeners ──────────────────────────────────────────────────
    private List<WaypointEntity> waypoints = new ArrayList<>();
    private OnItemClickListener  itemClickListener;
    private OnDeleteClickListener deleteClickListener;

    public void setOnItemClickListener(OnItemClickListener l)  { itemClickListener  = l; }
    public void setOnDeleteClickListener(OnDeleteClickListener l) { deleteClickListener = l; }

    // ── Actualizar datos ───────────────────────────────────────────────────
    public void submitList(List<WaypointEntity> newList) {
        waypoints = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    // ── RecyclerView.Adapter ───────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_waypoint, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WaypointEntity wp = waypoints.get(position);

        // Nombre
        holder.tvName.setText(wp.name != null ? wp.name : "Sin nombre");

        // Ícono y color según especie
        holder.tvSpeciesIcon.setText(iconForSpecies(wp.species));
        String color = WaypointManager.getColorForSpecies(wp.species);
        holder.tvSpeciesIcon.setBackgroundColor(
                Color.parseColor(color.length() == 7 ? color + "33" : color));

        // Subtítulo: especie · fecha
        String species = wp.species != null ? wp.species : "Sin especie";
        String date    = formatDate(wp.createdAt);
        holder.tvSubtitle.setText(species + " · " + date);

        // Click sobre la fila
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) itemClickListener.onItemClick(wp);
        });

        // Click sobre eliminar
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteClickListener != null) deleteClickListener.onDeleteClick(wp);
        });
    }

    @Override
    public int getItemCount() {
        return waypoints.size();
    }

    // ── ViewHolder ─────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSpeciesIcon, tvName, tvSubtitle, btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            tvSpeciesIcon = itemView.findViewById(R.id.tvSpeciesIcon);
            tvName        = itemView.findViewById(R.id.tvName);
            tvSubtitle    = itemView.findViewById(R.id.tvSubtitle);
            btnDelete     = itemView.findViewById(R.id.btnDelete);
        }
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private String iconForSpecies(String species) {
        if (species == null) return "📍";
        switch (species) {
            case "Concha de abanico": return "🐚";
            case "Pulpo":             return "🐙";
            case "Pescado":           return "🐟";
            case "Langosta":          return "🦞";
            case "Caracol rosado":    return "🐌";
            case "Almejas":           return "🦪";
            case "Caracol bola":      return "🐚";
            default:                  return "📍";
        }
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM · HH:mm", new Locale("es", "PE"));
        return sdf.format(new Date(timestamp));
    }
}