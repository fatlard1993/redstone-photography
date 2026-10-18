package justfatlard.redstone_photography;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class Main implements ModInitializer {
	public static final String MOD_ID = "redstone-photography";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier CAMERA_ID = Identifier.fromNamespaceAndPath(MOD_ID, "camera");
	public static final Identifier FILM_ID = Identifier.fromNamespaceAndPath(MOD_ID, "film");
	public static final Identifier EXPOSED_FILM_ID = Identifier.fromNamespaceAndPath(MOD_ID, "exposed_film");
	public static final Identifier PHOTOGRAPH_ID = Identifier.fromNamespaceAndPath(MOD_ID, "photograph");

	public static final CameraBlock CAMERA_BLOCK = new CameraBlock(BlockBehaviour.Properties.of()
		.setId(ResourceKey.create(Registries.BLOCK, CAMERA_ID))
		.mapColor(MapColor.COLOR_BLACK)
		.strength(1.5F)
		.sound(SoundType.COPPER)
		.noOcclusion());

	public static final BlockEntityType<CameraBlockEntity> CAMERA_BLOCK_ENTITY =
		new BlockEntityType<>(CameraBlockEntity::new, Set.of(CAMERA_BLOCK));

	public static final Item CAMERA = new CameraItem(new Item.Properties()
		.setId(ResourceKey.create(Registries.ITEM, CAMERA_ID))
		.stacksTo(1));

	public static final Item FILM = new Item(new Item.Properties()
		.setId(ResourceKey.create(Registries.ITEM, FILM_ID))
		.stacksTo(16));

	public static final Item EXPOSED_FILM = new Item(new Item.Properties()
		.setId(ResourceKey.create(Registries.ITEM, EXPOSED_FILM_ID))
		.stacksTo(1));

	public static final Item PHOTOGRAPH = new PhotographItem(new Item.Properties()
		.setId(ResourceKey.create(Registries.ITEM, PHOTOGRAPH_ID))
		.stacksTo(1));

	private static final Set<ItemFrame> FRAMES = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Set<Display.ItemDisplay> DEVELOPING = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final int CHECK_EVERY_TICKS = 20;

	@Override
	public void onInitialize() {
		PandoricalApi.content().registerBlock(CAMERA_ID.toString(), new BlockRegistration()
			.baseBlock("minecraft:copper_block")
			.interactive()
			.strength(1.5F)
			.requiresCorrectTool(false)
			.model(MOD_ID + ":block/camera"));
		PandoricalApi.content().registerItem(CAMERA_ID.toString(), new ItemRegistration()
			.model(MOD_ID + ":item/camera").maxStackSize(1));
		PandoricalApi.content().registerItem(FILM_ID.toString(), new ItemRegistration()
			.model(MOD_ID + ":item/film").maxStackSize(16));
		PandoricalApi.content().registerItem(EXPOSED_FILM_ID.toString(), new ItemRegistration()
			.model(MOD_ID + ":item/exposed_film").maxStackSize(1));
		PandoricalApi.content().registerItem(PHOTOGRAPH_ID.toString(), new ItemRegistration()
			.model(MOD_ID + ":item/photograph").maxStackSize(1));
		PandoricalApi.content().registerModAssets(MOD_ID);

		Registry.register(BuiltInRegistries.BLOCK, CAMERA_ID, CAMERA_BLOCK);
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, CAMERA_ID, CAMERA_BLOCK_ENTITY);
		Registry.register(BuiltInRegistries.ITEM, CAMERA_ID, CAMERA);
		Registry.register(BuiltInRegistries.ITEM, FILM_ID, FILM);
		Registry.register(BuiltInRegistries.ITEM, EXPOSED_FILM_ID, EXPOSED_FILM);
		Registry.register(BuiltInRegistries.ITEM, PHOTOGRAPH_ID, PHOTOGRAPH);

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, CAMERA_ID),
			FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.redstone-photography.redstone-photography"))
				.icon(() -> new ItemStack(CAMERA))
				.displayItems((context, entries) -> {
					entries.accept(new ItemStack(CAMERA));
					entries.accept(new ItemStack(FILM));
				})
				.build());

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server)) return InteractionResult.PASS;
			return Developing.use(server, hit.getBlockPos(), player, hand);
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!Prints.isPrint(entity)) return InteractionResult.PASS;
			if (!player.mayBuild() || !level.mayInteract(player, ((ItemFrame) entity).getPos())) return InteractionResult.FAIL;
			if (level instanceof ServerLevel server) Prints.takeDown(server, (ItemFrame) entity, player);
			return InteractionResult.SUCCESS;
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (Prints.isPrint(entity)) FRAMES.add((ItemFrame) entity);
			else if (Developing.isTray(entity)) DEVELOPING.add((Display.ItemDisplay) entity);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ItemFrame frame) FRAMES.remove(frame);
			else if (entity instanceof Display.ItemDisplay display) DEVELOPING.remove(display);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_EVERY_TICKS != 0) return;
			for (ItemFrame frame : new ArrayList<>(FRAMES)) {
				if (frame.level() instanceof ServerLevel level) Prints.check(level, frame);
			}
			for (Display.ItemDisplay tray : new ArrayList<>(DEVELOPING)) {
				if (tray.level() instanceof ServerLevel level) Developing.check(level, tray);
			}
		});

		LOGGER.info("Loaded redstone-photography (server-side with Pandorical)");
	}
}
