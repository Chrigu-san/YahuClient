package net.yahuclient.mod.mixin;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.network.chat.Component;
import net.yahuclient.mod.ui.BlockWidgets;
import net.yahuclient.mod.ui.GlassFx;
import net.yahuclient.mod.ui.Theme;
import net.yahuclient.mod.ui.TitleLayout;

/**
 * Every button, in every menu, is a glass button: it leans to the cursor, sinks while pressed and rain runs down it.
 * Its label is drawn here too, on the glass, so it leans with it. Icon buttons keep the warp open until their icon is
 * drawn (SpriteIconButtonMixin), so the icon leans as well.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget.WithInactiveMessage {
	/** Buttons whose label we drew ourselves. */
	private static final Set<Object> LABELLED = Collections.newSetFromMap(new WeakHashMap<>());

	private AbstractButtonMixin(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void yahu$glassButton(GuiGraphicsExtractor g, CallbackInfo ci) {
		String icon = TitleLayout.ICON.get(this);
		if (icon != null) {
			TitleLayout.drawIcon(g, this, icon, isHoveredOrFocused(), alpha);
		} else if (TitleLayout.NAV.contains(this)) {
			TitleLayout.drawEntry(g, this, isActive(), isHoveredOrFocused(), BlockWidgets.HIGHLIGHTED.contains(this), alpha);
		} else {
			int x = getX(), y = getY(), w = width, h = height;
			boolean hot = isActive() && isHoveredOrFocused();
			boolean pressed = hot && Minecraft.getInstance().mouseHandler.isLeftPressed();
			GlassFx.State s = GlassFx.begin(g, this, x, y, w, h, hot, pressed, w > 60 ? 7 : 18);
			GlassFx.button(g, s, x, y, w, h, BlockWidgets.HIGHLIGHTED.contains(this), !isActive(), alpha);
			if ((Object) this instanceof SpriteIconButton) {
				// its icon comes next: the warp stays open until it is drawn (SpriteIconButtonMixin ends it)
				GlassFx.iconPending = true;
			} else {
				label(g, s, x, y, w, h);
				LABELLED.add(this);
				GlassFx.end();
			}
		}
		ci.cancel();
	}

	/** The label on the glass: centered, cut short with an ellipsis if it doesn't fit, grey when the button is off. */
	private void label(GuiGraphicsExtractor g, GlassFx.State s, int x, int y, int w, int h) {
		var font = Minecraft.getInstance().font;
		String text = getMessage().getString();
		if (font.width(Theme.ui(text)) > w - 8) {
			while (text.length() > 1 && font.width(Theme.ui(text + "…")) > w - 8) {
				text = text.substring(0, text.length() - 1);
			}
			text = text.stripTrailing() + "…";
		}
		var line = Theme.ui(text);
		int color = isActive() ? Theme.mix(0xFFDFE2E9, 0xFFF2F4F8, s.hover()) : 0x80C8CCD4;
		int tx = Math.round(x + (w - font.width(line)) / 2f), ty = y + (h - 8) / 2;
		GlassFx.label(g, x + w / 2f, y + h / 2f, () -> g.text(font, line, tx, ty, Theme.alpha(color, alpha), false));
	}

	// our own label replaces Minecraft's
	@Inject(method = "extractDefaultLabel", at = @At("HEAD"), cancellable = true)
	private void yahu$label(net.minecraft.client.gui.ActiveTextCollector output, CallbackInfo ci) {
		if (TitleLayout.NAV.contains(this) || TitleLayout.ICON.containsKey(this) || LABELLED.contains(this)) {
			ci.cancel();
		}
	}
}
