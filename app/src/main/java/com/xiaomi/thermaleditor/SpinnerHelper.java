package com.xiaomi.thermaleditor;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

/**
 * 统一配置 Spinner 的样式与下拉弹窗宽度，避免长文本在窄下拉框中被截断
 */
public final class SpinnerHelper {

    private SpinnerHelper() {}

    /**
     * 为 Spinner 绑定统一样式并将下拉列表宽度对齐到所属卡片容器
     */
    public static void configure(Spinner spinner, View anchorCard, ArrayAdapter<String> adapter) {
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinner.setAdapter(adapter);

        if (anchorCard != null) {
            anchorCard.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                  oldLeft, oldTop, oldRight, oldBottom) -> {
                int cardWidth = right - left;
                if (cardWidth <= 0) return;
                anchorCard.post(() -> {
                    int w = anchorCard.getWidth();
                    if (w <= 0) return;
                    int[] cardLoc = new int[2];
                    int[] spLoc = new int[2];
                    anchorCard.getLocationOnScreen(cardLoc);
                    spinner.getLocationOnScreen(spLoc);
                    int offset = cardLoc[0] - spLoc[0];
                    if (spinner.getDropDownWidth() != w) {
                        spinner.setDropDownWidth(w);
                    }
                    if (spinner.getDropDownHorizontalOffset() != offset) {
                        spinner.setDropDownHorizontalOffset(offset);
                    }
                });
            });
        }
    }
}
