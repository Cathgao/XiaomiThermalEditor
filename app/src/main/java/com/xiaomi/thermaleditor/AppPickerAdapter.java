package com.xiaomi.thermaleditor;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 添加应用弹窗列表适配器：展示所有已安装应用的图标、显示名及当前所属规则标记
 */
public class AppPickerAdapter extends RecyclerView.Adapter<AppPickerAdapter.ViewHolder> {

    public interface OnAppSelectedListener {
        void onAppSelected(AppEntry entry);
    }

    private final List<AppEntry> items = new ArrayList<>();
    private final Map<String, String> existingRuleBadgeMap;
    private final OnAppSelectedListener listener;

    public AppPickerAdapter(Map<String, String> existingRuleBadgeMap, OnAppSelectedListener listener) {
        this.existingRuleBadgeMap = existingRuleBadgeMap;
        this.listener = listener;
    }

    public void submitList(List<AppEntry> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rule_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppEntry entry = items.get(position);
        holder.ivIcon.setImageDrawable(entry.icon);
        holder.tvName.setText(entry.displayName);
        holder.btnRemove.setVisibility(View.GONE);

        String ruleBadge = (existingRuleBadgeMap != null) ? existingRuleBadgeMap.get(entry.packageName) : null;
        if (ruleBadge != null && !ruleBadge.isEmpty()) {
            holder.tvTag.setVisibility(View.VISIBLE);
            holder.tvTag.setText(ruleBadge);
            holder.tvTag.setTextColor(Color.parseColor("#F59E0B"));
        } else if (entry.isSystem) {
            holder.tvTag.setVisibility(View.VISIBLE);
            holder.tvTag.setText("系统应用");
            holder.tvTag.setTextColor(Color.parseColor("#60A5FA"));
        } else {
            holder.tvTag.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppSelected(entry);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvName;
        final TextView tvTag;
        final ImageView btnRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_app_icon);
            tvName = itemView.findViewById(R.id.tv_app_name);
            tvTag = itemView.findViewById(R.id.tv_app_tag);
            btnRemove = itemView.findViewById(R.id.btn_remove_app);
        }
    }
}
