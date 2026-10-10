package com.WynnRunica.mixin;

import com.WynnRunica.Config;
import com.WynnRunica.NameplateStyler;
import com.WynnRunica.NpcNameResolver;
import com.WynnRunica.NpcNameplateCapture;
import com.WynnRunica.TextEmojiUtils;
import com.WynnRunica.TranslationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.Box;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DisplayEntity.TextDisplayEntity.class)
public abstract class NpcNameRendererMixin implements TranslationManager.NpcRefreshable {

    @org.spongepowered.asm.mixin.Unique
    private Text wr$lastCapture;
    @org.spongepowered.asm.mixin.Unique
    private Text wr$translationSource;
    @org.spongepowered.asm.mixin.Unique
    private Text wr$translationResult;

    @org.spongepowered.asm.mixin.Shadow
    protected abstract void refreshData(boolean interpolate, float lerpProgress);

    @Override
    public void wr$refresh() {
        wr$translationSource = null;
        wr$translationResult = null;
        this.refreshData(false, 0.0f);
    }

    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void onGetText(CallbackInfoReturnable<Text> cir) {
        Text text = cir.getReturnValue();
        if (text == null) return;
        if (Config.isEnabled("Отправка строк") && text != wr$lastCapture) {
            wr$lastCapture = text;
            Text[] parts = NameplateStyler.splitTail(text);
            if (parts == null || NpcNameResolver.resolveNameplate(parts[0].getString()) == null) {
                NpcNameplateCapture.record(text);
            }
        }
        if (!Config.isEnabled(NameplateStyler.isMob(text) || wr$aboveCreature() ? "Имена NPC" : "Надписи в мире")) return;
        if (text != wr$translationSource) {
            wr$translationSource = text;
            wr$translationResult = translateText(text);
        }
        cir.setReturnValue(wr$translationResult);
    }

    @org.spongepowered.asm.mixin.Unique
    private long wr$kindCheckedAt;
    @org.spongepowered.asm.mixin.Unique
    private boolean wr$aboveCreature;

    @org.spongepowered.asm.mixin.Unique
    private boolean wr$aboveCreature() {
        long now = System.currentTimeMillis();
        if (now - wr$kindCheckedAt < 1000) return wr$aboveCreature;
        wr$kindCheckedAt = now;
        Entity self = (Entity) (Object) this;
        Entity localPlayer = MinecraftClient.getInstance().player;
        Box below = new Box(self.getX() - 0.6, self.getY() - 3.2, self.getZ() - 0.6,
                self.getX() + 0.6, self.getY() + 0.2, self.getZ() + 0.6);
        wr$aboveCreature = self.hasVehicle() || !self.getEntityWorld().getOtherEntities(self, below,
                entity -> entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity)
                        && entity != localPlayer).isEmpty();
        return wr$aboveCreature;
    }

    private Text translateText(Text text) {
        if (text == null) return null;
        Text whole = translateWhole(text);
        if (whole != text) return whole;
        Text[] parts = NameplateStyler.splitTail(text);
        if (parts == null) return text;
        Text name = translateWhole(parts[0]);
        if (name == parts[0]) return text;
        return Text.empty().append(name).append(parts[1]);
    }

    private Text translateWhole(Text text) {
        var extracted = TextEmojiUtils.extract(text);
        String complete = NpcNameResolver.resolveNameplate(text.getString());
        Text shaped = complete == null ? null : NameplateStyler.apply(text, complete);
        if (shaped != null) return shaped;
        if (complete != null && (extracted.icons.isEmpty()
                || (complete.length() - complete.replace("<em>", "").length()) / 4 == extracted.icons.size())) {
            if (extracted.key.startsWith("<em>\n") && complete.startsWith("<em>")
                    && !complete.startsWith("<em>\n")) {
                complete = "<em>\n" + complete.substring(4).stripLeading();
            }
            return TextEmojiUtils.rebuild(complete, extracted.icons, extracted.contentStyle);
        }
        boolean[] modified = {false};
        Text translated = translateNode(text, modified);
        return modified[0] ? translated : text;
    }

    private Text translateNode(Text text, boolean[] modified) {
        if (text == null) return null;
        TextContent content = text.getContent();
        TextContent newContent = content;
        if (content instanceof PlainTextContent plain) {
            String str = plain.string();
            String replaced = NpcNameResolver.resolve(str);
            if (replaced != null && !replaced.equals(str)) {
                newContent = PlainTextContent.of(replaced.replaceAll("§#[0-9a-fA-F]{6}", ""));
                modified[0] = true;
            }
        }
        MutableText result = MutableText.of(newContent).setStyle(text.getStyle());
        for (Text sibling : text.getSiblings()) {
            result.append(translateNode(sibling, modified));
        }
        return result;
    }
}
