package org.dimdev.dimdoors.shared.world.limbodimension;

import net.minecraft.client.audio.MusicTicker;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.client.EnumHelperClient;
import net.minecraftforge.common.util.EnumHelper;
import org.dimdev.dimdoors.DimDoors;
import org.dimdev.ddutils.render.CloudRenderBlank;
import org.dimdev.dimdoors.shared.blocks.BlockFabric;
import org.dimdev.dimdoors.shared.blocks.ModBlocks;
import org.dimdev.ddutils.Location;
import org.dimdev.dimdoors.shared.sound.ModSounds;
import org.dimdev.dimdoors.shared.world.ModDimensions;
import org.dimdev.dimdoors.shared.world.ModBiomes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class WorldProviderLimbo extends WorldProvider {
    @SideOnly(Side.CLIENT)
    public static final MusicTicker.MusicType music = EnumHelper.addEnum(MusicTicker.MusicType.class, "limbo", new Class[] { SoundEvent.class, int.class, int.class }, ModSounds.CREEPY, 0,0);

    @Override
    public void init() {
        hasSkyLight = false;
        biomeProvider = new BiomeProviderSingle(ModBiomes.LIMBO);
        DimDoors.proxy.setCloudRenderer(this, new CloudRenderBlank());
        DimDoors.proxy.setSkyRenderer(this, new LimboSkyProvider());
    }

    @Override
    public boolean canRespawnHere() {
        return false; // TODO: properties.HardcoreLimboEnabled;
    }

    @Override
    protected void generateLightBrightnessTable() {
        // Brightness decreases with light level in limbo
        for (int i = 0; i <= 15; ++i) {
            float f1 = 1.0F - i / 15.0F;
            lightBrightnessTable[i] = f1 / (f1 * 3.0F + 1.0F) * 1.0F * 3;
        }
    }

    @Override
    public BlockPos getSpawnPoint() {
        return getRandomizedSpawnPoint();
    }

    @Override
    public float calculateCelestialAngle(long worldTime, float partialTicks) {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getMoonPhase(long worldTime) {
        return 4;
    }

    @Override
    public boolean canCoordinateBeSpawn(int x, int z) {
        BlockPos pos = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z));
        return world.getBlockState(pos).equals(ModBlocks.FABRIC.getDefaultState().withProperty(BlockFabric.TYPE, BlockFabric.EnumType.UNRAVELED));
    }

    @Override
    public double getHorizon() {
        return (double) world.getHeight() / 4 - 800;
    }

    @Override
    public int getRespawnDimension(EntityPlayerMP player) {
        return 0;
    }

    @Override
    public IChunkGenerator createChunkGenerator() {
        return new LimboGenerator(world, world.getSeed());
    }

    public static Location getLimboSkySpawn(Entity entity) { // TODO: move this into projectToLimbo
        int x = (int) entity.posX + MathHelper.clamp(entity.world.rand.nextInt(), -100, 100); // TODO: -properties.LimboEntryRange, properties.LimboEntryRange);
        int z = (int) entity.posZ + MathHelper.clamp(entity.world.rand.nextInt(), -100, 100); // TODO: -properties.LimboEntryRange, properties.LimboEntryRange);
        return new Location(ModDimensions.LIMBO.getId(), x, 700, z);
    }

    @Override
    public BlockPos getRandomizedSpawnPoint() {
        int x = MathHelper.clamp(world.rand.nextInt(), -500, 500);
        int z = MathHelper.clamp(world.rand.nextInt(), -500, 500);
        return new BlockPos(x, 700, z);
    }

    @Override
    public DimensionType getDimensionType() {
        return ModDimensions.LIMBO;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getSkyColor(Entity cameraEntity, float partialTicks) {
        return Vec3d.ZERO;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Vec3d getFogColor(float celestialAngle, float partialTicks) {
        return new Vec3d(.2, .2, .2);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean doesXZShowFog(int x, int z) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public MusicTicker.MusicType getMusicType() {
        System.out.println("Derp " + (music != null)
        );
        return music;
    }
}
