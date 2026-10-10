package justfatlard.redstone_photography.mixin;

import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemFrame.class)
public interface ItemFrameAccessor {
	/** Vanilla only sets it from saved data. */
	@Accessor("fixed")
	void setPrintFixed(boolean fixed);
}
