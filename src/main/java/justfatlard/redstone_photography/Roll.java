package justfatlard.redstone_photography;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The film loaded in a camera, in hand or placed: blank film waiting to be shot, and the exposures
 * already taken, which stay in the camera until they are taken out. Both share its sixteen spaces.
 */
public record Roll(int blank, List<Film.Exposure> exposed) {
	public static final int CAPACITY = 16;
	public static final Roll EMPTY = new Roll(0, List.of());

	public Roll {
		exposed = List.copyOf(exposed);
	}

	public int room() {
		return CAPACITY - blank - exposed.size();
	}

	public Roll load(int film) {
		return new Roll(blank + film, exposed);
	}

	/** One blank film exposed; with none, as a creative player shoots, only the exposure added. */
	public Roll shoot(Film.Exposure exposure) {
		List<Film.Exposure> taken = new ArrayList<>(exposed);
		taken.add(exposure);
		return new Roll(Math.max(0, blank - 1), taken);
	}

	/** Without the exposure taken last. */
	public Roll withoutLast() {
		return new Roll(blank, exposed.subList(0, exposed.size() - 1));
	}

	public Film.Exposure last() {
		return exposed.getLast();
	}

	public Component describe() {
		return Component.translatableWithFallback("redstone-photography.roll.loaded",
			"Film: %s unexposed, %s exposed", blank, exposed.size());
	}

	// --- Kept as a tag, on a camera item or a placed camera ---

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("film", blank);
		ListTag list = new ListTag();
		for (Film.Exposure exposure : exposed) {
			CompoundTag entry = new CompoundTag();
			entry.putString("photo", exposure.photo().toString());
			entry.putString("by", exposure.by());
			list.add(entry);
		}
		tag.put("exposed", list);
		return tag;
	}

	public static Roll fromTag(CompoundTag tag) {
		List<Film.Exposure> exposed = new ArrayList<>();
		for (CompoundTag entry : tag.getListOrEmpty("exposed").compoundStream().toList()) {
			UUID photo = Film.uuid(entry.getStringOr("photo", ""));
			if (photo != null && exposed.size() < CAPACITY) exposed.add(new Film.Exposure(photo, entry.getStringOr("by", "")));
		}
		int blank = Math.clamp(tag.getIntOr("film", 0), 0, CAPACITY - exposed.size());
		return new Roll(blank, exposed);
	}

	// --- On a camera item ---

	public static Roll read(ItemStack camera) {
		return fromTag(camera.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(Main.MOD_ID));
	}

	public void write(ItemStack camera) {
		Film.write(camera, toTag());
		camera.set(DataComponents.LORE, new ItemLore(List.of(Film.grey(describe()))));
	}
}
