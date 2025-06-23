package com.example.petroglyphcam;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.PetroglyphViewHolder>
        implements Filterable {

    private static final String TAG = "GalleryAdapter";
    private final Context context;
    private List<PetroglyphItem> originalItems;
    private List<PetroglyphItem> filteredItems;
    private final ItemFilter filter = new ItemFilter();

    public GalleryAdapter(Context context, List<PetroglyphItem> items) {
        this.context = context;
        this.originalItems = new ArrayList<>(items);
        this.filteredItems = new ArrayList<>(items);
    }

    public void updateData(List<PetroglyphItem> newItems) {
        this.originalItems = new ArrayList<>(newItems);
        this.filteredItems = new ArrayList<>(newItems);
        notifyDataSetChanged();
        Log.d(TAG, "Данные адаптера обновлены. Элементов: " + filteredItems.size());
    }

    @NonNull
    @Override
    public PetroglyphViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_gallery, parent, false);
        return new PetroglyphViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PetroglyphViewHolder holder, int position) {
        PetroglyphItem item = filteredItems.get(position);
        Log.d(TAG, "Отображение элемента #" + position + ": " + item.getDescription());

        holder.dateText.setText(item.getDate());
        holder.coordsText.setText(item.getCoordinates());
        holder.altitudeText.setText(item.getAltitude());
        holder.preservationText.setText(item.getPreservation());

        Glide.with(context)
                .load(Uri.parse(item.getImageUri()))
                .thumbnail(0.1f)
                .into(holder.imageView);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PreviewActivity.class);
            intent.setData(Uri.parse(item.getImageUri()));
            intent.putExtra("should_delete_on_cancel", false);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return filteredItems.size();
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    private class ItemFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            List<PetroglyphItem> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(originalItems);
                Log.d(TAG, "Фильтр: показываем все элементы (" + originalItems.size() + ")");
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (PetroglyphItem item : originalItems) {
                    if (item.getDescription().toLowerCase().contains(filterPattern) ||
                            item.getDate().toLowerCase().contains(filterPattern) ||
                            item.getPreservation().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
                Log.d(TAG, "Фильтр: найдено " + filteredList.size() + " элементов по запросу '" + constraint + "'");
            }

            results.values = filteredList;
            results.count = filteredList.size();
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredItems.clear();
            filteredItems.addAll((List<PetroglyphItem>) results.values);
            notifyDataSetChanged();
            Log.d(TAG, "Обновлено отображаемых элементов: " + filteredItems.size());
        }
    }

    static class PetroglyphViewHolder extends RecyclerView.ViewHolder {
        TextView dateText, coordsText, altitudeText, preservationText;
        ImageView imageView;

        public PetroglyphViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            coordsText = itemView.findViewById(R.id.coordsText);
            altitudeText = itemView.findViewById(R.id.altitudeText);
            preservationText = itemView.findViewById(R.id.preservationText);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }
}