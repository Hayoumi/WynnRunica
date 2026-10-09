package com.WynnRunica;

import com.WynnRunica.gui.SettingsLayout;

public final class SettingsLayoutTest {
    public static void main(String[] args) {
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {960, 540}, {1920, 1080}}) {
            for (int content : new int[] {32, 104, 212, 800}) {
                SettingsLayout layout = SettingsLayout.fit(size[0], size[1], content);
                require(layout.x() >= 12 && layout.y() >= 12, "window margin");
                require(layout.x() + layout.width() <= size[0] - 12, "right edge");
                require(layout.y() + layout.height() <= size[1] - 12, "bottom edge");
                require(layout.left() > layout.sidebarRight(), "list starts right of the sidebar");
                require(layout.right() > layout.left(), "list has width");
                require(layout.viewportHeight() > 0, "visible content");
                require(layout.clampScroll(-100) == 0, "top clamp");
                require(layout.clampScroll(10000) == layout.maxScroll(), "bottom clamp");
                require(layout.contentHeight() - layout.maxScroll() <= layout.viewportHeight(), "last row reachable");
                require(!layout.containsContent(layout.left(), layout.top() - 1), "title rejects row clicks");
                require(!layout.containsContent(layout.left(), layout.bottom()), "footer rejects row clicks");
                require(!layout.containsContent(layout.sidebarRight(), layout.top()), "sidebar rejects row clicks");
                require(layout.containsContent(layout.left(), layout.top()), "first row clickable");
            }
        }
        require(SettingsLayout.fit(960, 540, 212).maxScroll() == 0, "six rows fit without scrolling");
        require(SettingsLayout.fit(960, 540, 248).maxScroll() > 0, "longer lists scroll instead of growing");
        require(SettingsLayout.fit(427, 240, 212).maxScroll() > 0, "small screens scroll");
        System.out.println("Settings layout checks passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
