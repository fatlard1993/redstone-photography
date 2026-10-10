package justfatlard.redstone_photography;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.UUID;

/** Exposed film as an item: which picture on the server it holds, and who took it. */
public final class Film {
	private Film() {}

	public record Exposure(UUID photo, String by) {}

	public static ItemStack exposed(Exposure exposure) {
		ItemStack stack = new ItemStack(Main.EXPOSED_FILM);
		CompoundTag tag = new CompoundTag();
		tag.putString("photo", exposure.photo().toString());
		tag.putString("by", exposure.by());
		write(stack, tag);
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			grey(Component.translatableWithFallback("redstone-photography.film.taken_by", "Taken by %s", exposure.by())),
			grey(Component.translatableWithFallback("redstone-photography.film.develop_hint",
				"Develop in a water cauldron, in a room lit only by redstone")))));
		return stack;
	}

	public static Exposure read(ItemStack stack) {
		if (!stack.is(Main.EXPOSED_FILM)) return null;
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(Main.MOD_ID);
		UUID photo = uuid(tag.getStringOr("photo", ""));
		return photo == null ? null : new Exposure(photo, tag.getStringOr("by", ""));
	}

	static UUID uuid(String text) {
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static Component grey(Component line) {
		return line.copy().withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY));
	}

	static void write(ItemStack stack, CompoundTag tag) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(Main.MOD_ID, tag));
	}
}
