package com.codelearninapp.pythmaster;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

    private final List<HistoryEntry> data;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public HistoryAdapter(List<HistoryEntry> data) {
        this.data = data;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_entry, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        HistoryEntry e = data.get(position);

        h.tvTitle.setText(e.title != null ? e.title : "");
        h.tvTimestamp.setText(e.completedAt > 0 ? ("Completed on: " + fmt.format(e.completedAt)) : "");
        h.tvScore.setText(String.format(Locale.getDefault(), "Score: %d/%d", e.correct, e.total));

        // Track tag e.g. "Basic", "Mid", "Advance"
        String tag = e.track != null ? e.track : "";
        if ("python_basic".equalsIgnoreCase(tag) || "basic".equalsIgnoreCase(tag)) tag = "Basic";
        else if ("python_mid".equalsIgnoreCase(tag) || "mid".equalsIgnoreCase(tag)) tag = "Mid";
        else if ("python_advance".equalsIgnoreCase(tag) || "advance".equalsIgnoreCase(tag)) tag = "Advance";
        h.tvTrackTag.setText(tag);

        h.tvPointsPill.setText(String.format(Locale.getDefault(), "+%d pts", e.pointsEarned));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTimestamp, tvScore, tvTrackTag, tvPointsPill;
        Holder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvScore = itemView.findViewById(R.id.tvScore);
            tvTrackTag = itemView.findViewById(R.id.tvTrackTag);
            tvPointsPill = itemView.findViewById(R.id.tvPointsPill);
        }
    }
}