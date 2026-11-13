package com.example;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.model.MemoryItem;
import com.example.ui.MemoryAdapter;

import java.util.List;

/**
 * Activity for viewing, adding, and deleting locally stored SUBH AI memories.
 */
public class MemoryActivity extends AppCompatActivity implements MemoryAdapter.OnDeleteMemoryListener {

    private MemoryManager memoryManager;
    private MemoryAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory);

        memoryManager = AppController.getInstance().getMemoryManager();

        View root = findViewById(R.id.root_memory);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        tvEmpty = findViewById(R.id.tv_memory_empty);
        RecyclerView rvMemories = findViewById(R.id.rv_memories);
        adapter = new MemoryAdapter(this);
        rvMemories.setLayoutManager(new LinearLayoutManager(this));
        rvMemories.setAdapter(adapter);

        findViewById(R.id.btn_memory_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_memory).setOnClickListener(v -> showAddMemoryDialog());
        findViewById(R.id.btn_clear_memories).setOnClickListener(v -> confirmClearAll());

        loadMemories();
    }

    private void loadMemories() {
        List<MemoryItem> list = memoryManager.getAllMemories();
        adapter.setItems(list);
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddMemoryDialog() {
        if (!memoryManager.isMemoryEnabled()) {
            Toast.makeText(this, "Memory is currently OFF in Settings.", Toast.LENGTH_SHORT).show();
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_memory, null);
        EditText etFact = view.findViewById(R.id.et_memory_fact);

        new AlertDialog.Builder(this)
                .setTitle("Add New Memory")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    String fact = etFact.getText() != null ? etFact.getText().toString().trim() : "";
                    if (!fact.isEmpty()) {
                        memoryManager.saveMemory(fact, "manual");
                        loadMemories();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Memories?")
                .setMessage("Delete every saved personal preference and memory?")
                .setPositiveButton("Delete All", (d, w) -> {
                    memoryManager.clearAllMemories();
                    loadMemories();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDeleteMemory(MemoryItem item) {
        memoryManager.deleteMemory(item.getId());
        loadMemories();
    }
}
