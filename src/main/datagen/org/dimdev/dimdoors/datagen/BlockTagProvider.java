package org.dimdev.dimdoors.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.block.Blocks;
import net.minecraft.tag.BlockTags;
import org.dimdev.dimdoors.block.ModBlocks;
import org.dimdev.dimdoors.tag.ModBlockTags;

public class BlockTagProvider extends FabricTagProvider.BlockTagProvider {
	public BlockTagProvider(FabricDataGenerator dataGenerator) {
		super(dataGenerator);
	}

	@Override
	protected void generateTags() {
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_AIR).add(
				Blocks.COBWEB,
				ModBlocks.DRIFTWOOD_LEAVES,
				ModBlocks.DRIFTWOOD_SAPLING,
				Blocks.GLASS_PANE,
				Blocks.MOSS_CARPET,
				ModBlocks.DRIFTWOOD_TRAPDOOR,
				Blocks.RAIL,
				ModBlocks.RUST,
				ModBlocks.UNRAVELED_SPIKE);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_RAIL).add(
				Blocks.ACTIVATOR_RAIL,
				Blocks.DETECTOR_RAIL,
				Blocks.POWERED_RAIL);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_GRITTY_STONE).add(
				Blocks.INFESTED_STONE,
				Blocks.INFESTED_COBBLESTONE,
				Blocks.INFESTED_STONE_BRICKS,
				Blocks.INFESTED_MOSSY_STONE_BRICKS,
				Blocks.INFESTED_CRACKED_STONE_BRICKS,
				Blocks.INFESTED_CHISELED_STONE_BRICKS);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_SOLID_STATIC).add(
				Blocks.END_PORTAL_FRAME,
				Blocks.COMMAND_BLOCK,
				Blocks.CHAIN_COMMAND_BLOCK,
				Blocks.REPEATING_COMMAND_BLOCK
		);
		getOrCreateTagBuilder(ModBlockTags.DECAY_UNRAVELED_FENCE).add(
				ModBlocks.CLAY_FENCE,
				ModBlocks.DARK_SAND_FENCE
		);
		getOrCreateTagBuilder(ModBlockTags.DECAY_UNRAVELED_GATE).add(
				ModBlocks.CLAY_GATE,
				ModBlocks.DARK_SAND_GATE
		);
		getOrCreateTagBuilder(ModBlockTags.DECAY_UNRAVELED_BUTTON).add(
				ModBlocks.CLAY_BUTTON,
				ModBlocks.DARK_SAND_BUTTON
		);
		getOrCreateTagBuilder(ModBlockTags.DECAY_UNRAVELED_SLAB).add(
				ModBlocks.CLAY_SLAB,
				ModBlocks.DARK_SAND_SLAB
		);
		getOrCreateTagBuilder(ModBlockTags.DECAY_UNRAVELED_STAIRS).add(
				ModBlocks.CLAY_STAIRS,
				ModBlocks.DARK_SAND_STAIRS
		);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_GLASS_PANE).add(
				Blocks.GRAY_STAINED_GLASS_PANE,
				Blocks.BLACK_STAINED_GLASS_PANE,
				Blocks.ORANGE_STAINED_GLASS_PANE,
				Blocks.BLUE_STAINED_GLASS_PANE,
				Blocks.BROWN_STAINED_GLASS_PANE,
				Blocks.CYAN_STAINED_GLASS_PANE,
				Blocks.GREEN_STAINED_GLASS_PANE,
				Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
				Blocks.LIGHT_GRAY_STAINED_GLASS_PANE,
				Blocks.LIME_STAINED_GLASS_PANE,
				Blocks.MAGENTA_STAINED_GLASS_PANE,
				Blocks.PINK_STAINED_GLASS_PANE,
				Blocks.PURPLE_STAINED_GLASS_PANE,
				Blocks.RED_STAINED_GLASS_PANE,
				Blocks.WHITE_STAINED_GLASS_PANE,
				Blocks.YELLOW_STAINED_GLASS_PANE
		);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_RUST).add(
				//REDSTONE VARIANTS
				Blocks.LIGHTNING_ROD,
				Blocks.LANTERN,
				Blocks.IRON_BARS,
				Blocks.HOPPER,
				Blocks.CHAIN,
				Blocks.CAULDRON
		);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_UNRAVELED_SPIKE).add(
				Blocks.END_ROD,
				Blocks.POINTED_DRIPSTONE
		).addTag(BlockTags.FLOWER_POTS).addTag(BlockTags.CANDLES);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_WITHER_ROSE).addTag(BlockTags.SMALL_FLOWERS).addTag(BlockTags.TALL_FLOWERS);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_CLAY);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_CLAY);
		getOrCreateTagBuilder(ModBlockTags.DECAY_CLAY_FENCE);
		getOrCreateTagBuilder(ModBlockTags.DECAY_CLAY_GATE);
		getOrCreateTagBuilder(ModBlockTags.DECAY_CLAY_BUTTON);
		getOrCreateTagBuilder(ModBlockTags.DECAY_CLAY_SLAB);
		getOrCreateTagBuilder(ModBlockTags.DECAY_CLAY_STAIRS);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_DARK_SAND);
		getOrCreateTagBuilder(ModBlockTags.DECAY_DARK_SAND_FENCE);
		getOrCreateTagBuilder(ModBlockTags.DECAY_DARK_SAND_GATE);
		getOrCreateTagBuilder(ModBlockTags.DECAY_DARK_SAND_BUTTON);
		getOrCreateTagBuilder(ModBlockTags.DECAY_DARK_SAND_SLAB);
		getOrCreateTagBuilder(ModBlockTags.DECAY_DARK_SAND_STAIRS);
		getOrCreateTagBuilder(ModBlockTags.DECAY_TO_AMALGAM);
	}
}
