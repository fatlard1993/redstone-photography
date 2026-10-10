package justfatlard.redstone_photography;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** A developed photograph, hung on the wall it is used against. */
public class PhotographItem extends Item {
	public PhotographItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Prints.Print print = Prints.read(context.getItemInHand());
		if (print == null) return InteractionResult.PASS;
		return Prints.hang(context, print);
	}
}
