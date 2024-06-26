package org.dimdev.dimdoors.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.dimdev.dimdoors.api.block.ExplosionConvertibleBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.stream.Collectors;

@Mixin(Explosion.class)
public class ExplosionMixin {
	@Mutable
	@Shadow
	@Final
	private ObjectArrayList<BlockPos> affectedBlocks;

	@Shadow
	@Final
	private World world;

	@Inject(method = "affectWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;shuffle(Lit/unimi/dsi/fastutil/objects/ObjectArrayList;Lnet/minecraft/util/math/random/Random;)V", ordinal = 0, shift = At.Shift.AFTER))
	private void handleExplosionConvertibleBlocks(boolean b1, CallbackInfo ci) {
		this.affectedBlocks = this.affectedBlocks.stream().filter(blockPos -> {
			BlockState state = this.world.getBlockState(blockPos);
			Block block = state.getBlock();
			if (!(block instanceof ExplosionConvertibleBlock)) {
				return true;
			}
			ActionResult result = ((ExplosionConvertibleBlock) block).explode(this.world, blockPos, state, state.hasBlockEntity() ? this.world.getBlockEntity(blockPos) : null);
			return result == ActionResult.PASS;
		}).collect(Collectors.toCollection(ObjectArrayList::new));
	}
}
