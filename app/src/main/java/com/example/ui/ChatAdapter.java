package com.example.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 1;
    private static final int TYPE_ASSISTANT = 2;
    private static final int TYPE_SYSTEM = 3;

    public interface OnMessageActionListener {
        void onReplayVoice(ChatMessage message);
        void onSaveToMemory(ChatMessage message);
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private final OnMessageActionListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public ChatAdapter(OnMessageActionListener listener) {
        this.listener = listener;
    }

    public void setMessages(List<ChatMessage> items) {
        messages.clear();
        if (items != null) {
            messages.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage item) {
        if (item == null) return;
        messages.add(item);
        notifyItemInserted(messages.size() - 1);
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        String role = messages.get(position).getRole();
        if (ChatMessage.ROLE_USER.equals(role)) return TYPE_USER;
        if (ChatMessage.ROLE_SYSTEM.equals(role)) return TYPE_SYSTEM;
        return TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false));
        } else if (viewType == TYPE_SYSTEM) {
            return new SystemViewHolder(inflater.inflate(R.layout.item_chat_system, parent, false));
        } else {
            return new AssistantViewHolder(inflater.inflate(R.layout.item_chat_assistant, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        String formattedTime = timeFormat.format(new Date(msg.getTimestamp()));

        if (holder instanceof UserViewHolder) {
            UserViewHolder vh = (UserViewHolder) holder;
            vh.tvBody.setText(msg.getContent());
            vh.tvTime.setText(formattedTime);
        } else if (holder instanceof SystemViewHolder) {
            SystemViewHolder vh = (SystemViewHolder) holder;
            vh.tvBody.setText(msg.getContent());
        } else if (holder instanceof AssistantViewHolder) {
            AssistantViewHolder vh = (AssistantViewHolder) holder;
            vh.tvBody.setText(msg.getContent());
            vh.tvTime.setText(formattedTime);
            vh.btnReplay.setOnClickListener(v -> {
                if (listener != null) listener.onReplayVoice(msg);
            });
            vh.btnSaveMemory.setOnClickListener(v -> {
                if (listener != null) listener.onSaveToMemory(msg);
            });
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class AssistantViewHolder extends RecyclerView.ViewHolder {
        final TextView tvBody;
        final TextView tvTime;
        final ImageButton btnReplay;
        final ImageButton btnSaveMemory;

        AssistantViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBody = itemView.findViewById(R.id.tv_message_body);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            btnReplay = itemView.findViewById(R.id.btn_replay_message);
            btnSaveMemory = itemView.findViewById(R.id.btn_save_memory);
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView tvBody;
        final TextView tvTime;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBody = itemView.findViewById(R.id.tv_message_body);
            tvTime = itemView.findViewById(R.id.tv_message_time);
        }
    }

    static class SystemViewHolder extends RecyclerView.ViewHolder {
        final TextView tvBody;

        SystemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBody = itemView.findViewById(R.id.tv_message_body);
        }
    }
}
