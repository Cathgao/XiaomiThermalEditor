package com.xiaomi.thermaleditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class TierItemTouchHelperCallback extends ItemTouchHelper.Callback {

    private final TierAdapter adapter;

    public TierItemTouchHelperCallback(TierAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return true; // 支持长按任意空白位置直接拖拽
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        int swipeFlags = 0;
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        int fromPos = viewHolder.getAdapterPosition();
        int toPos = target.getAdapterPosition();
        if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
            return false;
        }
        adapter.onItemMove(fromPos, toPos);
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // 无滑动删除
    }

    @Override
    public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            viewHolder.itemView.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start();
            viewHolder.itemView.setElevation(28f);
            viewHolder.itemView.setAlpha(0.92f);
        }
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        viewHolder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        viewHolder.itemView.setElevation(4f);
        viewHolder.itemView.setAlpha(1.0f);

        // 拖拽完成松手后，统一刷新所有档位编号和回调
        adapter.onDragFinished();
    }
}
