package org.dimdev.dimdoors.rift.targets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.dimdev.dimdoors.DimensionalDoorsInitializer;
import org.dimdev.dimdoors.api.rift.target.Target;
import org.dimdev.dimdoors.block.ModBlocks;
import org.dimdev.dimdoors.block.entity.DetachedRiftBlockEntity;
import org.dimdev.dimdoors.block.entity.RiftBlockEntity;
import org.dimdev.dimdoors.pockets.PocketGenerator;
import org.dimdev.dimdoors.rift.registry.LinkProperties;
import org.dimdev.dimdoors.rift.registry.Rift;
import org.dimdev.dimdoors.api.util.Location;
import org.dimdev.dimdoors.api.util.math.MathUtil;
import org.dimdev.dimdoors.world.level.registry.DimensionalRegistry;
import org.dimdev.dimdoors.world.pocket.type.Pocket;
import org.dimdev.dimdoors.world.pocket.VirtualLocation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public class RandomTarget extends VirtualTarget { // TODO: Split into DungeonTarget subclass
	private final float newRiftWeight;
	private final double weightMaximum;
	private final double coordFactor;
	private final double positiveDepthFactor;
	private final double negativeDepthFactor;
	private final Set<Integer> acceptedGroups;
	private final boolean noLink;
	private final boolean noLinkBack;

	public RandomTarget(float newRiftWeight, double weightMaximum, double coordFactor, double positiveDepthFactor, double negativeDepthFactor, Set<Integer> acceptedGroups, boolean noLink, boolean noLinkBack) {
		this.newRiftWeight = newRiftWeight;
		this.weightMaximum = weightMaximum;
		this.coordFactor = coordFactor;
		this.positiveDepthFactor = positiveDepthFactor;
		this.negativeDepthFactor = negativeDepthFactor;
		this.acceptedGroups = acceptedGroups;
		this.noLink = noLink;
		this.noLinkBack = noLinkBack;
	}

	public static RandomTargetBuilder builder() {
		return new RandomTargetBuilder();
	}

	@Override
	public Target receiveOther() { // TODO: Wrap rather than replace
		VirtualLocation virtualLocationHere = VirtualLocation.fromLocation(this.location);

		Map<Location, Float> riftWeights = new HashMap<>();
		if (this.newRiftWeight > 0) riftWeights.put(null, this.newRiftWeight);

		for (Rift otherRift : DimensionalRegistry.getRiftRegistry().getRifts()) {
			VirtualLocation otherVirtualLocation = VirtualLocation.fromLocation(otherRift.getLocation());
			if (otherRift.getProperties() == null) continue;
			double otherWeight = otherRift.isDetached() ? otherRift.getProperties().floatingWeight : otherRift.getProperties().getEntranceWeight();
			if (otherWeight == 0 || Sets.intersection(this.acceptedGroups, otherRift.getProperties().getGroups()).isEmpty())
				continue;

			// Calculate the distance as sqrt((coordFactor * coordDistance)^2 + (depthFactor * depthDifference)^2)
			if (otherRift.getProperties().getLinksRemaining() == 0) continue;
			double depthDifference = otherVirtualLocation.getDepth() - virtualLocationHere.getDepth();
			double coordDistance = Math.sqrt(this.sq(otherVirtualLocation.getX() - virtualLocationHere.getX())
				+ this.sq(otherVirtualLocation.getZ() - virtualLocationHere.getZ()));
			double depthFactor = depthDifference > 0 ? this.positiveDepthFactor : this.negativeDepthFactor;
			double distance = Math.sqrt(this.sq(this.coordFactor * coordDistance) + this.sq(depthFactor * depthDifference));

			// Calculate the weight as 4m/pi w/(m^2/d + d)^2. This is similar to how gravitational/electromagnetic attraction
			// works in physics (G m1 m2/d^2 and k_e m1 m2/d^2). Even though we add a depth dimension to the world, we keep
			// the weight inversly proportionally to the area of a sphere (the square of the distance) rather than a
			// hypersphere (the cube of the area) because the y coordinate does not matter for now. We use m^2/d + d
			// rather than d such that the probability near 0 tends to 0 rather than infinity. f(m^2/d) is a special case
			// of f((m^(a+1)/a)/d^a). m is the location of f's maximum. The constant 4m/pi makes it such that a newRiftWeight
			// of 1 is equivalent to having a total link weight of 1 distributed equally across all layers.
			// TODO: We might want an a larger than 1 to make the function closer to 1/d^2
			double weight = 4 * this.weightMaximum / Math.PI * otherWeight / this.sq(this.sq(this.weightMaximum) / distance + distance);
			riftWeights.put(otherRift.getLocation(), (float) weight);
		}

		Location selectedLink;
		if (riftWeights.size() == 0) {
			if (this.newRiftWeight == -1) {
				selectedLink = null;
			} else {
				return null;
			}
		} else {
			selectedLink = MathUtil.weightedRandom(riftWeights);
		}

		// Check if we have to generate a new rift
		if (selectedLink == null) {
			double r = Math.random();
			double distance = this.weightMaximum * (2 * Math.tan(Math.PI / 2 * r) - 0.5578284481138029 * Math.sqrt(r) * Math.log(r));

			// Randomly split the vector into depth, x, and z components
			// TODO: Two random angles isn't a uniformly random direction! Use random vector, normalize, add depth offset, scale xz, scale depth.
			double theta = Math.random() * Math.PI; // Angle between vector and xz plane
			double phi = Math.random() * Math.PI;  // Angle of the vector on the xz plane relative to the x axis
			double depth = distance * Math.sin(theta);
			depth /= depth > 0 ? this.positiveDepthFactor : this.negativeDepthFactor;
			double x = Math.cos(theta) * Math.cos(phi) * distance / this.coordFactor;
			double z = Math.cos(theta) * Math.sin(phi) * distance / this.coordFactor;
			VirtualLocation virtualLocation = new VirtualLocation(virtualLocationHere.getWorld(),
				virtualLocationHere.getX() + (int) Math.round(x),
				virtualLocationHere.getZ() + (int) Math.round(z),
				virtualLocationHere.getDepth() + (int) Math.round(depth));

			if (virtualLocation.getDepth() <= 0) {
				// This will lead to the overworld
				ServerWorld world = DimensionalDoorsInitializer.getWorld(virtualLocation.getWorld());
				BlockPos pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(virtualLocation.getX(), 0, virtualLocation.getZ()));
				if (pos.getY() == -1) {
					// No blocks at that XZ (hole in bedrock)
					pos = new BlockPos(virtualLocation.getX(), 0, virtualLocation.getX());
				}
				world.setBlockState(pos, ModBlocks.DETACHED_RIFT.getDefaultState());

				RiftBlockEntity thisRift = (RiftBlockEntity) this.location.getBlockEntity();
				DetachedRiftBlockEntity riftEntity = (DetachedRiftBlockEntity) world.getBlockEntity(pos);

				// This scenario must have the rift be available
				assert riftEntity != null;
				assert thisRift != null;

				riftEntity.setProperties(thisRift.getProperties().toBuilder().linksRemaining(1).build());

				if (!this.noLinkBack && !riftEntity.getProperties().isOneWay())
					linkRifts(new Location(world, pos), this.location);
				if (!this.noLink) linkRifts(this.location, new Location(world, pos));
				return riftEntity.as(Targets.ENTITY);
			} else {
				// Make a new dungeon pocket
				RiftBlockEntity thisRift = (RiftBlockEntity) this.location.getBlockEntity();
				LinkProperties newLink = thisRift != null ? thisRift.getProperties() != null ? thisRift.getProperties().toBuilder().linksRemaining(0).build() : null : null;
				Pocket pocket = PocketGenerator.generateDungeonPocketV2(virtualLocation, new GlobalReference(!this.noLinkBack ? this.location : null), newLink); // TODO make the generated dungeon of the same type, but in the overworld

				// Link the rift if necessary and teleport the entity
				if (!this.noLink)
					linkRifts(this.location, DimensionalRegistry.getRiftRegistry().getPocketEntrance(pocket));
				return (Target) DimensionalRegistry.getRiftRegistry().getPocketEntrance(pocket).getBlockEntity();
			}
		} else {
			// An existing rift was selected
			RiftBlockEntity riftEntity = (RiftBlockEntity) selectedLink.getBlockEntity();

			// Link the rifts if necessary and teleport the entity
			if (!this.noLink) linkRifts(this.location, selectedLink);
			if (!this.noLinkBack && (riftEntity != null && !riftEntity.getProperties().isOneWay())) linkRifts(selectedLink, this.location);
			return riftEntity;
		}
	}

	protected Pocket generatePocket(VirtualLocation location, GlobalReference linkTo, LinkProperties props) {
		return PocketGenerator.generateDungeonPocketV2(location, linkTo, props);
	}

	private static void linkRifts(Location from, Location to) {
		RiftBlockEntity fromBe = (RiftBlockEntity) from.getBlockEntity();
		RiftBlockEntity toBe = (RiftBlockEntity) to.getBlockEntity();

		if (fromBe == null) {
			return;
		}

		fromBe.setDestination(RiftReference.tryMakeLocal(from, to));
		fromBe.markDirty();
		if (toBe != null && toBe.getProperties() != null) {
			toBe.setProperties(toBe.getProperties().withLinksRemaining(toBe.getProperties().getLinksRemaining() - 1));
			toBe.updateProperties();
			toBe.markDirty();
		}
	}

	private double sq(double a) {
		return a * a;
	}

	public float getNewRiftWeight() {
		return this.newRiftWeight;
	}

	public double getWeightMaximum() {
		return this.weightMaximum;
	}

	public double getCoordFactor() {
		return this.coordFactor;
	}

	public double getPositiveDepthFactor() {
		return this.positiveDepthFactor;
	}

	public double getNegativeDepthFactor() {
		return this.negativeDepthFactor;
	}

	public Set<Integer> getAcceptedGroups() {
		return this.acceptedGroups;
	}

	public boolean isNoLink() {
		return this.noLink;
	}

	public boolean isNoLinkBack() {
		return this.noLinkBack;
	}

	@Override
	public VirtualTargetType<? extends VirtualTarget> getType() {
		return VirtualTargetType.AVAILABLE_LINK;
	}

	public static NbtCompound toNbt(RandomTarget target) {
		NbtCompound nbt = new NbtCompound();
		nbt.putFloat("newRiftWeight", target.newRiftWeight);
		nbt.putDouble("weightMaximum", target.weightMaximum);
		nbt.putDouble("coordFactor", target.coordFactor);
		nbt.putDouble("positiveDepthFactor", target.positiveDepthFactor);
		nbt.putDouble("negativeDepthFactor", target.negativeDepthFactor);
		nbt.putIntArray("acceptedGroups", new ArrayList<>(target.acceptedGroups));
		nbt.putBoolean("noLink", target.noLink);
		nbt.putBoolean("noLinkBack", target.noLinkBack);

		return nbt;
	}

	public static RandomTarget fromNbt(NbtCompound nbt) {
		return new RandomTarget(
			nbt.getFloat("newRiftWeight"),
			nbt.getDouble("weightMaximum"),
			nbt.getDouble("coordFactor"),
			nbt.getDouble("positiveDepthFactor"),
			nbt.getDouble("negativeDepthFactor"),
			Arrays.stream(nbt.getIntArray("acceptedGroups")).boxed().collect(Collectors.toSet()),
			nbt.getBoolean("noLink"),
			nbt.getBoolean("noLinkBack")
		);
	}

	public static class RandomTargetBuilder {
		protected float newRiftWeight;
		protected double weightMaximum;
		protected double coordFactor;
		protected double positiveDepthFactor;
		protected double negativeDepthFactor;
		protected Set<Integer> acceptedGroups = Collections.emptySet();
		protected boolean noLink;
		protected boolean noLinkBack;

		RandomTargetBuilder() {
		}

		public RandomTarget.RandomTargetBuilder newRiftWeight(float newRiftWeight) {
			this.newRiftWeight = newRiftWeight;
			return this;
		}

		public RandomTarget.RandomTargetBuilder weightMaximum(double weightMaximum) {
			this.weightMaximum = weightMaximum;
			return this;
		}

		public RandomTarget.RandomTargetBuilder coordFactor(double coordFactor) {
			this.coordFactor = coordFactor;
			return this;
		}

		public RandomTarget.RandomTargetBuilder positiveDepthFactor(double positiveDepthFactor) {
			this.positiveDepthFactor = positiveDepthFactor;
			return this;
		}

		public RandomTarget.RandomTargetBuilder negativeDepthFactor(double negativeDepthFactor) {
			this.negativeDepthFactor = negativeDepthFactor;
			return this;
		}

		public RandomTarget.RandomTargetBuilder acceptedGroups(Set<Integer> acceptedGroups) {
			this.acceptedGroups = acceptedGroups;
			return this;
		}

		public RandomTarget.RandomTargetBuilder noLink(boolean noLink) {
			this.noLink = noLink;
			return this;
		}

		public RandomTarget.RandomTargetBuilder noLinkBack(boolean noLinkBack) {
			this.noLinkBack = noLinkBack;
			return this;
		}

		public RandomTarget build() {
			return new RandomTarget(this.newRiftWeight, this.weightMaximum, this.coordFactor, this.positiveDepthFactor, this.negativeDepthFactor, this.acceptedGroups, this.noLink, this.noLinkBack);
		}
	}
}
