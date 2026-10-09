package com.WynnRunica.gui;

public record SettingsLayout(int x, int y, int width, int height, int contentHeight) {
    public static final int SIDEBAR = 150;
    private static final int HEADER = 50;
    private static final int PADDING = 16;
    private static final int MARGIN = 12;

    public static SettingsLayout fit(int screenWidth, int screenHeight, int contentHeight) {
        int width = Math.min(520, Math.max(1, screenWidth - MARGIN * 2));
        int height = Math.min(300, Math.max(1, screenHeight - MARGIN * 2));
        return new SettingsLayout((screenWidth - width) / 2, (screenHeight - height) / 2,
                width, height, contentHeight);
    }

    public int sidebarRight() { return x + Math.min(SIDEBAR, width / 3); }
    public int left() { return sidebarRight() + PADDING; }
    public int right() { return x + width - PADDING; }
    public int top() { return y + HEADER; }
    public int bottom() { return y + height - PADDING; }
    public int viewportHeight() { return Math.max(0, bottom() - top()); }
    public int maxScroll() { return Math.max(0, contentHeight - viewportHeight()); }
    public int clampScroll(int scroll) { return Math.max(0, Math.min(maxScroll(), scroll)); }

    public boolean containsContent(double mouseX, double mouseY) {
        return mouseX >= left() && mouseX < right() && mouseY >= top() && mouseY < bottom();
    }
}
