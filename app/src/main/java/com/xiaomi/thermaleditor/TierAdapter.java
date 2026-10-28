package com.xiaomi.thermaleditor;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import java.util.List;

public class TierAdapter extends RecyclerView.Adapter<TierAdapter.TierViewHolder> {

    public interface OnTierChangeListener {
        void onTierChanged();
    }

    private final ThermalSection section;
    private final List<ThermalTier> tiers;
    private ItemTouchHelper itemTouchHelper;
    private OnTierChangeListener changeListener;

    public TierAdapter(ThermalSection section) {
        this.section = section;
        this.tiers = section.getTiers();
    }

    public void setItemTouchHelper(ItemTouchHelper itemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper;
    }

    public void setOnTierChangeListener(OnTierChangeListener listener) {
        this.changeListener = listener;
    }

    @NonNull
    @Override
    public TierViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tier, parent, false);
        return new TierViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull TierViewHolder holder, int position) {
        ThermalTier tier = tiers.get(position);
        holder.bind(section, tier, position + 1);

        // 触摸拖动手柄立即启动拖拽
        holder.ivDragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(holder);
                }
            }
            return false;
        });

        // 目标频率列表选择点击
        holder.btnTargetSelect.setOnClickListener(v -> {
            showTargetSelectDialog(holder.itemView.getContext(), tier, holder);
        });

        // 删除该档位
        holder.btnDelete.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION && currentPos < tiers.size()) {
                tiers.remove(currentPos);
                notifyItemRemoved(currentPos);
                notifyItemRangeChanged(currentPos, tiers.size() - currentPos);
                if (changeListener != null) {
                    changeListener.onTierChanged();
                }
            }
        });
    }

    private void showTargetSelectDialog(Context context, ThermalTier tier, TierViewHolder holder) {
        List<FrequencyManager.FreqOption> options = FrequencyManager.getOptionsForSection(section);

        // 如果是 CPU 或 GPU 区域，展示格式化的频率列表
        if (!options.isEmpty()) {
            int selectedIdx = -1;
            String curTarget = tier.getTarget().trim();
            String[] displayItems = new String[options.size() + 1];

            for (int i = 0; i < options.size(); i++) {
                FrequencyManager.FreqOption opt = options.get(i);
                displayItems[i] = opt.getDialogDisplayText();
                if (opt.valueToSave.equals(curTarget)) {
                    selectedIdx = i;
                }
            }
            displayItems[options.size()] = "自定义手动输入...";

            String title = FrequencyManager.isGpuSection(section)
                    ? "选择 GPU 限制档位 (自动转频率)"
                    : "选择 CPU 限制频率";

            new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setSingleChoiceItems(displayItems, selectedIdx, (dialog, which) -> {
                        dialog.dismiss();
                        if (which < options.size()) {
                            FrequencyManager.FreqOption chosen = options.get(which);
                            tier.setTarget(chosen.valueToSave);
                            holder.updateTargetDisplay(section, chosen.valueToSave);
                            if (changeListener != null) {
                                changeListener.onTierChanged();
                            }
                        } else {
                            // 自定义手动输入
                            showCustomTargetInputDialog(context, tier, holder);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            // 普通参数区域 (如无线充、充电电流等)，直接弹出数值输入框
            showCustomTargetInputDialog(context, tier, holder);
        }
    }

    private void showCustomTargetInputDialog(Context context, ThermalTier tier, TierViewHolder holder) {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(tier.getTarget());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(context)
                .setTitle("输入目标限制数值 (target)")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String val = input.getText().toString().trim();
                    tier.setTarget(val);
                    holder.updateTargetDisplay(section, val);
                    if (changeListener != null) {
                        changeListener.onTierChanged();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return tiers.size();
    }

    /**
     * 当手势拖动项改变位置时调用
     */
    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= tiers.size() || toPosition >= tiers.size()) {
            return;
        }

        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(tiers, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(tiers, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    /**
     * 拖拽手势彻底松手放开后调用，刷新档位编号
     */
    public void onDragFinished() {
        notifyDataSetChanged();
        if (changeListener != null) {
            changeListener.onTierChanged();
        }
    }

    public static class TierViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvTierIndex;
        public final ImageView ivDragHandle;
        public final View btnDelete;
        public final EditText etTrig;
        public final TextView tvTrigHint;
        public final EditText etClr;
        public final TextView tvClrHint;

        public final View btnTargetSelect;
        public final TextView tvTargetDisplay;
        public final TextView tvTargetHint;

        private TextWatcher trigWatcher;
        private TextWatcher clrWatcher;

        public TierViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTierIndex = itemView.findViewById(R.id.tv_tier_index);
            ivDragHandle = itemView.findViewById(R.id.iv_drag_handle);
            btnDelete = itemView.findViewById(R.id.btn_delete_tier);
            etTrig = itemView.findViewById(R.id.et_trig);
            tvTrigHint = itemView.findViewById(R.id.tv_trig_hint);
            etClr = itemView.findViewById(R.id.et_clr);
            tvClrHint = itemView.findViewById(R.id.tv_clr_hint);

            btnTargetSelect = itemView.findViewById(R.id.btn_target_select);
            tvTargetDisplay = itemView.findViewById(R.id.tv_target_display);
            tvTargetHint = itemView.findViewById(R.id.tv_target_hint);
        }

        public void bind(ThermalSection section, ThermalTier tier, int index) {
            tvTierIndex.setText("档位 #" + index);

            if (trigWatcher != null) etTrig.removeTextChangedListener(trigWatcher);
            if (clrWatcher != null) etClr.removeTextChangedListener(clrWatcher);

            etTrig.setText(tier.getTrig());
            etClr.setText(tier.getClr());

            tvTrigHint.setText(ThermalTier.formatTempHint(tier.getTrig()));
            tvClrHint.setText(ThermalTier.formatTempHint(tier.getClr()));

            updateTargetDisplay(section, tier.getTarget());

            trigWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    tier.setTrig(s.toString());
                    tvTrigHint.setText(ThermalTier.formatTempHint(s.toString()));
                }
            };
            clrWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    tier.setClr(s.toString());
                    tvClrHint.setText(ThermalTier.formatTempHint(s.toString()));
                }
            };

            etTrig.addTextChangedListener(trigWatcher);
            etClr.addTextChangedListener(clrWatcher);
        }

        public void updateTargetDisplay(ThermalSection section, String targetVal) {
            String main = FrequencyManager.formatTargetDisplay(section, targetVal);
            String hint = FrequencyManager.formatTargetHint(section, targetVal);
            tvTargetDisplay.setText(main);
            tvTargetHint.setText(hint);
        }
    }

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
