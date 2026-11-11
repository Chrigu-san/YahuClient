package net.yahuclient.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

/** Reads whether a style asks for a font of its own (Style.getFont() answers the default when it doesn't). */
@Mixin(Style.class)
public interface StyleAccessor {
	@Accessor("font")
	FontDescription yahu$font();
}
