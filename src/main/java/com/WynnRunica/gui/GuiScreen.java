package com.WynnRunica.gui;

import com.WynnRunica.Config;
import com.WynnRunica.GuiTranslator;
import com.WynnRunica.TranslationManager;
import com.WynnRunica.WynnRunicaClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class GuiScreen extends Screen {
    private static final int TEXT = 0xFFF3ECDD;
    private static final int MUTED = 0xFFA79E88;
    private static final int FAINT = 0xFF6F6753;
    private static final int LIME = 0xFFC4DD5F;
    private static final int ERROR = 0xFFFF8F8F;
    private static final int PANEL = 0xF5141210;
    private static final int PANEL_EDGE = 0xFF2E2A20;
    private static final int SIDEBAR = 0x38000000;
    private static final int CARD = 0x0CFFF4D6;
    private static final int CARD_HI = 0x17FFF4D6;
    private static final int EDGE = 0x1AFFECBE;
    private static final int ROW = 32;
    private static final int ROW_GAP = 4;
    private static final Identifier ROUND = texture("settings/round");
    private static final Identifier GLOW = texture("settings/glow");
    private static final Identifier SIDEBAR_GLOW = texture("settings/sidebar_glow");
    private static final Identifier FRAME = texture("settings/frame");
    private static final Identifier FIELD = texture("settings/field");
    private static final Identifier OUTER_FRAME = texture("settings/outer_frame");
    private static final Identifier BRAND = texture("brand_mark");
    private static final Identifier WORDMARK = texture("wordmark");
    private static final String VERSION = "v" + FabricLoader.getInstance().getModContainer("wynn_runica")
            .get().getMetadata().getVersion().getFriendlyString();

    private enum Section {
        TRANSLATION("Перевод", "Выберите, что мод переводит в игре", "translation", Feature.Category.TRANSLATION),
        TOOLS("Инструменты", "Дополнительные функции мода", "tools", Feature.Category.TOOLS),
        KEYS("Клавиши", "Нажмите на строку, чтобы изменить клавишу", "keys", null);

        private final String title;
        private final String subtitle;
        private final Identifier icon;
        private final Feature.Category category;

        Section(String title, String subtitle, String icon, Feature.Category category) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = texture("settings/icon_" + icon);
            this.category = category;
        }
    }

    private static Section section = Section.TRANSLATION;
    private SettingsLayout layout;
    private int scroll;
    private final List<ListRow> rows = new ArrayList<>();

    private final Screen parent;

    public GuiScreen(Screen parent) {
        super(Text.literal("Настройки WynnRunica"));
        this.parent = parent;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    protected void init() {
        rows.clear();
        List<ListRow> fresh = new ArrayList<>();
        if (section == Section.KEYS) {
            for (KeyBinding binding : WynnRunicaClient.keyBindings()) fresh.add(new KeyRow(binding, fresh.size()));
        } else {
            for (Feature feature : Config.features) {
                if (feature.getCategory() == section.category) fresh.add(new FeatureRow(feature, fresh.size()));
            }
        }
        layout = SettingsLayout.fit(width, height, Math.max(0, fresh.size() * (ROW + ROW_GAP) - ROW_GAP));
        scroll = layout.clampScroll(scroll);
        for (Section candidate : Section.values()) addDrawableChild(new NavButton(candidate));
        addDrawableChild(new MasterSwitch());
        for (ListRow row : fresh) rows.add(addDrawableChild(row));
        positionRows();
    }

    private void positionRows() {
        for (ListRow row : rows) {
            row.setDimensionsAndPosition(layout.right() - layout.left(), ROW,
                    layout.left(), layout.top() + row.index * (ROW + ROW_GAP) - scroll);
        }
    }

    private static void refreshTranslations() {
        TranslationManager.refreshNpcs();
        GuiTranslator.refreshOpenScreen();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x90000000);
        int x = layout.x();
        int y = layout.y();
        int w = layout.width();
        int h = layout.height();
        int side = layout.sidebarRight();
        round(ctx, x, y, w, h, 3, PANEL_EDGE);
        round(ctx, x + 1, y + 1, w - 2, h - 2, 2, PANEL);
        ctx.enableScissor(x, y, side, y + h);
        round(ctx, x + 1, y + 1, side - x + 16, h - 2, 2, SIDEBAR);
        ctx.disableScissor();
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, SIDEBAR_GLOW, x + 1, y + 1, 0, 0, side - x - 1, Math.min(90, h - 2),
                298, 180, 298, 180, tint(LIME, 0x29));
        ctx.fill(side, y + 1, side + 1, y + h - 1, EDGE);
        decorate(ctx, x, y, w, h, side);

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, BRAND, x + 11, y + 11, 0, 0, 24, 24, 320, 320, 320, 320);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, WORDMARK, x + 40, y + 13, 0, 0, 91, 13, 273, 39, 273, 39);
        if (Config.didSaveFail()) {
            ctx.drawText(textRenderer, "Не удалось сохранить", x + 40, y + 28, ERROR, false);
        } else {
            ctx.drawText(textRenderer, VERSION, x + 40, y + 28, FAINT, false);
        }

        ctx.drawText(textRenderer, section.title, layout.left(), y + 16, TEXT, false);
        ctx.drawText(textRenderer, section.subtitle, layout.left(), y + 29, MUTED, false);

        if (layout.maxScroll() > 0) {
            int track = layout.viewportHeight();
            int thumb = Math.max(12, track * track / layout.contentHeight());
            int thumbY = layout.top() + (track - thumb) * scroll / layout.maxScroll();
            round(ctx, layout.right() + 5, thumbY, 3, thumb, 1, 0x40FFF4D6);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (layout.containsContent(mouseX, mouseY) && layout.maxScroll() > 0 && vertical != 0) {
            scroll = layout.clampScroll(scroll - (int) Math.round(vertical * 24));
            positionRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int page = Math.max(1, layout.viewportHeight() - 16);
        if (input.key() == GLFW.GLFW_KEY_PAGE_DOWN || input.key() == GLFW.GLFW_KEY_PAGE_UP) {
            if (input.key() == GLFW.GLFW_KEY_PAGE_UP) page = -page;
            scroll = layout.clampScroll(scroll + page);
            positionRows();
            return true;
        }
        boolean handled = super.keyPressed(input);
        if (getFocused() instanceof ListRow row) {
            int offset = row.index * (ROW + ROW_GAP);
            if (row.getY() < layout.top()) scroll = layout.clampScroll(offset);
            else if (row.getBottom() > layout.bottom()) scroll = layout.clampScroll(offset + ROW - layout.viewportHeight());
            positionRows();
        }
        return handled;
    }

    @Override
    public boolean shouldPause() { return false; }

    private void decorate(DrawContext ctx, int x, int y, int w, int h, int side) {
        int fieldX = side + 9;
        int fieldY = y + 45;
        nine(ctx, FIELD, fieldX, fieldY, x + w - 9 - fieldX, y + h - 10 - fieldY);

        int fx = x - 7;
        int fy = y - 3;
        int fw = w + 14;
        int fh = h + 6;
        int[] xs = {fx, fx + 24, fx + fw - 24};
        int[] ys = {fy, fy + 24, fy + fh - 24};
        int[] ws = {24, fw - 48, 24};
        int[] hs = {24, fh - 48, 24};
        int[] from = {0, 24, 122};
        int[] size = {24, 98, 24};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (row == 1 && column == 1) continue;
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, OUTER_FRAME, xs[column], ys[row], from[column], from[row],
                        ws[column], hs[row], size[column], size[row], 146, 146);
            }
        }
    }

    private static void nine(DrawContext ctx, Identifier texture, int x, int y, int w, int h) {
        int c = 12;
        if (w < 2 * c || h < 2 * c) return;
        int[] xs = {x, x + c, x + w - c};
        int[] ys = {y, y + c, y + h - c};
        int[] ws = {c, w - 2 * c, c};
        int[] hs = {c, h - 2 * c, c};
        int[] from = {0, 48, 80};
        int[] size = {48, 32, 48};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (ws[column] <= 0 || hs[row] <= 0) continue;
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, xs[column], ys[row], from[column], from[row],
                        ws[column], hs[row], size[column], size[row], 128, 128);
            }
        }
    }

    private static Identifier texture(String name) {
        return Identifier.of("wynnrunica", "textures/gui/" + name + ".png");
    }

    private static void round(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int c = 24;
        int m = 64 - 2 * c;
        slice(ctx, x, y, r, r, 0, 0, c, c, color);
        slice(ctx, x + w - r, y, r, r, 64 - c, 0, c, c, color);
        slice(ctx, x, y + h - r, r, r, 0, 64 - c, c, c, color);
        slice(ctx, x + w - r, y + h - r, r, r, 64 - c, 64 - c, c, c, color);
        slice(ctx, x + r, y, w - 2 * r, r, c, 0, m, c, color);
        slice(ctx, x + r, y + h - r, w - 2 * r, r, c, 64 - c, m, c, color);
        slice(ctx, x, y + r, r, h - 2 * r, 0, c, c, m, color);
        slice(ctx, x + w - r, y + r, r, h - 2 * r, 64 - c, c, c, m, color);
        slice(ctx, x + r, y + r, w - 2 * r, h - 2 * r, c, c, m, m, color);
    }

    private static void slice(DrawContext ctx, int x, int y, int w, int h, int u, int v, int uw, int vh, int color) {
        slice(ctx, ROUND, x, y, w, h, u, v, uw, vh, color);
    }

    private static void slice(DrawContext ctx, Identifier texture, int x, int y, int w, int h, int u, int v,
                              int uw, int vh, int color) {
        if (w > 0 && h > 0)
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, uw, vh, 64, 64, color);
    }

    private static void glow(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        slice(ctx, GLOW, x, y, r, r, 0, 0, 24, 24, color);
        slice(ctx, GLOW, x, y + r, r, h - 2 * r, 0, 24, 24, 16, color);
        slice(ctx, GLOW, x, y + h - r, r, r, 0, 40, 24, 24, color);
        slice(ctx, GLOW, x + r, y, w - r, h, 24, 0, 40, 64, color);
    }

    private static int tint(int color, int alpha) {
        return alpha << 24 | color & 0xFFFFFF;
    }

    private static void icon(DrawContext ctx, Identifier icon, int x, int y, int size, int color) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, size, size, 64, 64, 64, 64, color);
    }

    private static final class Switch {
        private float position;
        private long last = System.nanoTime();

        Switch(boolean on) { position = on ? 1 : 0; }

        void draw(DrawContext ctx, int x, int y, int w, int h, boolean on, boolean dim, int color) {
            long now = System.nanoTime();
            position += ((on ? 1 : 0) - position) * Math.min(1f, (now - last) / 1_000_000_000f * 14f);
            last = now;
            int track = 0x24FFF4D6;
            int knobColor = 0xFF8C8470;
            if (on && dim) {
                track = tint(color, 0x1C);
                knobColor = tint(color, 0x80);
            } else if (on) {
                track = tint(color, 0x40);
                knobColor = color;
            }
            round(ctx, x, y, w, h, h / 2, track);
            int knob = h - 4;
            round(ctx, x + 2 + Math.round((w - 4 - knob) * position), y + 2, knob, knob, knob / 2, knobColor);
        }
    }

    private abstract class MenuWidget extends ClickableWidget {
        MenuWidget(int x, int y, int width, int height, Text message) {
            super(x, y, width, height, message);
        }

        protected abstract void activate();

        @Override
        public void onClick(Click click, boolean doubled) { activate(); }

        @Override
        public boolean keyPressed(KeyInput input) {
            if (active && isFocused() && (input.key() == GLFW.GLFW_KEY_ENTER
                    || input.key() == GLFW.GLFW_KEY_KP_ENTER || input.key() == GLFW.GLFW_KEY_SPACE)) {
                playDownSound(client.getSoundManager());
                activate();
                return true;
            }
            return false;
        }

        boolean keyboardFocus() {
            return isFocused() && client.getNavigationType().isKeyboard();
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private final class NavButton extends MenuWidget {
        private final Section target;

        NavButton(Section target) {
            super(layout.x() + 8, layout.y() + 46 + target.ordinal() * 25,
                    layout.sidebarRight() - layout.x() - 16, 22, Text.literal(target.title));
            this.target = target;
        }

        @Override
        protected void activate() {
            if (section == target) return;
            section = target;
            scroll = 0;
            clearAndInit();
        }

        private String count() {
            if (target.category == null) return "";
            if (target.category == Feature.Category.TRANSLATION && !Config.isTranslationOn()) return "выкл";
            int total = 0;
            int on = 0;
            for (Feature feature : Config.features) {
                if (feature.getCategory() != target.category) continue;
                total++;
                if (feature.isEnabled()) on++;
            }
            return on + "/" + total;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            boolean chosen = section == target;
            if (chosen) {
                round(ctx, getX(), getY(), getWidth(), getHeight(), 5, CARD_HI);
                round(ctx, getX() - 6, getY() + 6, 2, getHeight() - 12, 1, LIME);
            }
            else if (isHovered()) round(ctx, getX(), getY(), getWidth(), getHeight(), 5, CARD);
            if (keyboardFocus()) ctx.drawStrokedRectangle(getX(), getY(), getWidth(), getHeight(), LIME);
            icon(ctx, target.icon, getX() + 7, getY() + 5, 12, chosen ? LIME : MUTED);
            ctx.drawText(textRenderer, target.title, getX() + 25, getY() + 7, chosen ? TEXT : MUTED, false);
            String count = count();
            ctx.drawText(textRenderer, count, getRight() - 7 - textRenderer.getWidth(count), getY() + 7,
                    count.equals("выкл") ? ERROR : FAINT, false);
        }
    }

    private final class MasterSwitch extends MenuWidget {
        private final Switch toggle = new Switch(Config.isTranslationOn());

        MasterSwitch() {
            super(layout.x() + 8, layout.y() + layout.height() - 8 - 32,
                    layout.sidebarRight() - layout.x() - 16, 32, Text.literal("Весь перевод"));
        }

        @Override
        protected void activate() {
            Config.setTranslationEnabled(!Config.isTranslationOn());
            Config.saveConfig();
            refreshTranslations();
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            boolean on = Config.isTranslationOn();
            round(ctx, getX(), getY(), getWidth(), getHeight(), 6, isHovered() ? CARD_HI : CARD);
            if (keyboardFocus()) ctx.drawStrokedRectangle(getX(), getY(), getWidth(), getHeight(), LIME);
            ctx.drawText(textRenderer, "Весь перевод", getX() + 8, getY() + 7, on ? TEXT : MUTED, false);
            ctx.drawText(textRenderer, "клавиша " + WynnRunicaClient.translationToggleKey().getString(),
                    getX() + 8, getY() + 18, FAINT, false);
            toggle.draw(ctx, getRight() - 8 - 24, getY() + (getHeight() - 13) / 2, 24, 13, on, false, LIME);
        }

        @Override
        protected net.minecraft.text.MutableText getNarrationMessage() {
            return Text.literal("Весь перевод: " + (Config.isTranslationOn() ? "включён" : "выключен"));
        }
    }

    private abstract class ListRow extends MenuWidget {
        final int index;

        ListRow(String title, int index) {
            super(0, 0, 1, ROW, Text.literal(title));
            this.index = index;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return layout.containsContent(mouseX, mouseY) && super.isMouseOver(mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            return layout.containsContent(click.x(), click.y()) && super.mouseClicked(click, doubled);
        }

        void drawCard(DrawContext ctx, Identifier icon, int color, boolean lit, boolean live, String title,
                      String description, int rightReserve) {
            round(ctx, getX(), getY(), getWidth(), getHeight(), 6, isHovered() ? CARD_HI : CARD);
            if (lit) {
                glow(ctx, getX(), getY(), getWidth() * 3 / 5, getHeight(), 6, tint(color, 0x24));
                round(ctx, getX(), getY() + 8, 2, getHeight() - 16, 1, color);
            }
            if (keyboardFocus()) ctx.drawStrokedRectangle(getX(), getY(), getWidth(), getHeight(), LIME);
            round(ctx, getX() + 6, getY() + 6, 20, 20, 5, lit ? tint(color, 0x29) : 0x0FFFF4D6);
            GuiScreen.icon(ctx, icon, getX() + 10, getY() + 10, 12, lit ? color : FAINT);
            int textWidth = getWidth() - 34 - rightReserve;
            ctx.drawText(textRenderer, textRenderer.trimToWidth(title, textWidth), getX() + 34, getY() + 6,
                    live ? TEXT : MUTED, false);
            ctx.drawText(textRenderer, textRenderer.trimToWidth(description, textWidth), getX() + 34, getY() + 18,
                    live ? MUTED : FAINT, false);
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            hovered = isMouseOver(mouseX, mouseY);
            ctx.enableScissor(layout.left(), layout.top(), layout.right(), layout.bottom());
            renderRow(ctx);
            ctx.disableScissor();
        }

        abstract void renderRow(DrawContext ctx);
    }

    private final class FeatureRow extends ListRow {
        private final Feature feature;
        private final Identifier icon;
        private final Switch toggle;

        FeatureRow(Feature feature, int index) {
            super(feature.getTitle(), index);
            this.feature = feature;
            this.icon = texture("settings/icon_" + feature.getIcon());
            this.toggle = new Switch(feature.isEnabled());
        }

        @Override
        protected void activate() {
            feature.toggle();
            if (feature.getCategory() == Feature.Category.TRANSLATION) refreshTranslations();
            Config.saveConfig();
        }

        @Override
        void renderRow(DrawContext ctx) {
            boolean live = feature.getCategory() != Feature.Category.TRANSLATION || Config.isTranslationOn();
            boolean on = feature.isEnabled();
            drawCard(ctx, icon, feature.getColor(), on && live, live, feature.getTitle(), feature.getDescription(), 40);
            toggle.draw(ctx, getRight() - 10 - 24, getY() + (getHeight() - 13) / 2, 24, 13, on, !live, feature.getColor());
        }

        @Override
        protected net.minecraft.text.MutableText getNarrationMessage() {
            return Text.literal(feature.getTitle() + ": " + (feature.isEnabled() ? "включено" : "выключено"));
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            super.appendClickableNarrations(builder);
            builder.put(NarrationPart.HINT, feature.getDescription());
        }
    }

    private final class KeyRow extends ListRow {
        private final KeyBinding binding;

        KeyRow(KeyBinding binding, int index) {
            super(binding.getId(), index);
            this.binding = binding;
        }

        @Override
        protected void activate() {
            client.setScreen(new KeybindsScreen(GuiScreen.this, client.options));
        }

        @Override
        void renderRow(DrawContext ctx) {
            String key = binding.isUnbound() ? "не задана" : binding.getBoundKeyLocalizedText().getString();
            int chip = textRenderer.getWidth(key) + 12;
            drawCard(ctx, Section.KEYS.icon, LIME, false, true, binding.getId(), "Меняется в управлении Minecraft", chip + 16);
            int cx = getRight() - 10 - chip;
            round(ctx, cx, getY() + 9, chip, 14, 4, EDGE);
            ctx.drawText(textRenderer, key, cx + 6, getY() + 12, binding.isUnbound() ? FAINT : TEXT, false);
        }
    }
}
