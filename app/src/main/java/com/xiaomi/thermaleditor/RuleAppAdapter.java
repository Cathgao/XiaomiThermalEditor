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

/**
 * 规则内应用列表适配器：使用真实图标与显示名展示规则中的应用
 */
public class RuleAppAdapter extends RecyclerView.Adapter<RuleAppAdapter.ViewHolder> {

    public static class RuleAppItem {
        public final AppEntry entry;
        public final String subRuleTag;

        public RuleAppItem(AppEntry entry, String subRuleTag) {
            this.entry = entry;
            this.subRuleTag = subRuleTag;
        }
    }

    public interface OnRemoveClickListener {
        void onRemove(AppEntry entry);
    }

    private final List<RuleAppItem> items = new ArrayList<>();
    private final OnRemoveClickListener removeListener;

    public RuleAppAdapter(OnRemoveClickListener removeListener) {
        this.removeListener = removeListener;
    }

    public void submitList(List<RuleAppItem> newItems) {
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
        RuleAppItem item = items.get(position);
        AppEntry entry = item.entry;
        holder.ivIcon.setImageDrawable(entry.icon);
        holder.tvName.setText(entry.displayName);

        StringBuilder tagSb = new StringBuilder();
        if (!entry.installed) {
            tagSb.append("本机未安装");
            holder.tvTag.setTextColor(Color.parseColor("#6B7280"));
            holder.ivIcon.setAlpha(0.45f);
        } else {
            holder.ivIcon.setAlpha(1.0f);
            if (entry.isSystem) {
                tagSb.append("系统应用");
                holder.tvTag.setTextColor(Color.parseColor("#60A5FA"));
            } else {
                holder.tvTag.setTextColor(Color.parseColor("#9CA3AF"));
            }
        }

        if (item.subRuleTag != null && !item.subRuleTag.isEmpty()) {
            if (tagSb.length() > 0) tagSb.append(" · ");
            tagSb.append("分组: ").append(item.subRuleTag);
        }

        if (tagSb.length() > 0) {
            holder.tvTag.setVisibility(View.VISIBLE);
            holder.tvTag.setText(tagSb.toString());
        } else {
            holder.tvTag.setVisibility(View.GONE);
        }

        holder.btnRemove.setOnClickListener(v -> {
            if (removeListener != null) {
                removeListener.onRemove(entry);
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
