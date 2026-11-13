package com.example.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.MemoryItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.MemoryViewHolder> {

    public interface OnDeleteMemoryListener {
        void onDeleteMemory(MemoryItem item);
    }

    private final List<MemoryItem> items = new ArrayList<>();
    private final OnDeleteMemoryListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault());

    public MemoryAdapter(OnDeleteMemoryListener listener) {
        this.listener = listener;
    }

    public void setItems(List<MemoryItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory, parent, false);
        return new MemoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemoryViewHolder holder, int position) {
        MemoryItem item = items.get(position);
        holder.tvCategory.setText(item.getCategory().toUpperCase(Locale.ROOT));
        holder.tvFact.setText(item.getFact());
        holder.tvDate.setText(dateFormat.format(new Date(item.getCreatedAt())));
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteMemory(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MemoryViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCategory;
        final TextView tvFact;
        final TextView tvDate;
        final ImageButton btnDelete;

        MemoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tv_memory_category);
            tvFact = itemView.findViewById(R.id.tv_memory_fact);
            tvDate = itemView.findViewById(R.id.tv_memory_date);
            btnDelete = itemView.findViewById(R.id.btn_delete_memory);
        }
    }
}
