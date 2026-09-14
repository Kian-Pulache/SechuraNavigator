// archivo: app/src/main/java/com/sechuranavigator/app/ui/RouteAdapter.java
package com.sechuranavigator.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.data.local.entities.TrackEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.ViewHolder> {

    public interface OnItemClickListener   { void onItemClick(TrackEntity track); }
    public interface OnDeleteClickListener { void onDeleteClick(TrackEntity track); }

    private List<TrackEntity>    tracks = new ArrayList<>();
    private OnItemClickListener  itemClickListener;
    private OnDeleteClickListener deleteClickListener;

    public void setOnItemClickListener(OnItemClickListener l)    { itemClickListener   = l; }
    public void setOnDeleteClickListener(OnDeleteClickListener l) { deleteClickListener = l; }

    public void submitList(List<TrackEntity> newList) {
        tracks = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_route, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrackEntity track = tracks.get(position);

        holder.tvRouteName.setText(
                track.name != null ? track.name : "Ruta sin nombre");
        holder.tvRouteDistance.setText(formatDistance(track.distanceMeters));
        holder.tvRouteDuration.setText(formatDuration(track.durationMs));
        holder.tvRouteDate.setText(formatDate(track.startedAt));

        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) itemClickListener.onItemClick(track);
        });
        holder.btnDeleteRoute.setOnClickListener(v -> {
            if (deleteClickListener != null) deleteClickListener.onDeleteClick(track);
        });
    }

    @Override
    public int getItemCount() { return tracks.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRouteName, tvRouteDistance, tvRouteDuration,
                tvRouteDate, btnDeleteRoute;

        ViewHolder(View view) {
            super(view);
            tvRouteName     = view.findViewById(R.id.tvRouteName);
            tvRouteDistance = view.findViewById(R.id.tvRouteDistance);
            tvRouteDuration = view.findViewById(R.id.tvRouteDuration);
            tvRouteDate     = view.findViewById(R.id.tvRouteDate);
            btnDeleteRoute  = view.findViewById(R.id.btnDeleteRoute);
        }
    }

    private String formatDistance(double meters) {
        if (meters < 1000)
            return String.format(Locale.US, "%.0f m", meters);
        return String.format(Locale.US, "%.1f km", meters / 1000);
    }

    private String formatDuration(long ms) {
        long hours   = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        if (hours > 0)
            return String.format(Locale.US, "%dh %02dm", hours, minutes);
        return String.format(Locale.US, "%d min", minutes);
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "dd MMM yyyy", new Locale("es", "PE"));
        return sdf.format(new Date(timestamp));
    }
}