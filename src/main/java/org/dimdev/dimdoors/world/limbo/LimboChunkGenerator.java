package org.dimdev.dimdoors.world.limbo;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;

import java.util.Iterator;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.dimdev.dimdoors.block.ModBlocks;
import org.jetbrains.annotations.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.structure.JigsawJunction;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.NoiseSampler;
import net.minecraft.util.math.noise.OctavePerlinNoiseSampler;
import net.minecraft.util.math.noise.OctaveSimplexNoiseSampler;
import net.minecraft.util.math.noise.PerlinNoiseSampler;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.BlockView;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.TheEndBiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.feature.StructureFeature;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

public class LimboChunkGenerator extends ChunkGenerator {
    public static final Codec<LimboChunkGenerator> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter((limboChunkGenerator) -> {
            return limboChunkGenerator.biomeSource;
        }), ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter((limboChunkGenerator) -> {
            return limboChunkGenerator.settings;
        })).apply(instance, instance.stable(LimboChunkGenerator::new));
    });

    private static final float[] NOISE_WEIGHT_TABLE = Util.make(new float[13824], (array) -> {
        for (int i = 0; i < 24; ++i) {
            for (int j = 0; j < 24; ++j) {
                for (int k = 0; k < 24; ++k) {
                    array[i * 24 * 24 + j * 24 + k] = (float) calculateNoiseWeight(j - 12, k - 12, i - 12);
                }
            }
        }
    });

    private static final float[] BIOME_WEIGHT_TABLE = Util.make(new float[25], (fs) -> {
        for (int i = -2; i <= 2; ++i) {
            for (int j = -2; j <= 2; ++j) {
                float f = 10.0F / MathHelper.sqrt((float) (i * i + j * j) + 0.2F);
                fs[i + 2 + (j + 2) * 5] = f;
            }
        }
    });

    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    ;
    private final int verticalNoiseResolution;
    private final int horizontalNoiseResolution;
    private final int noiseSizeX;
    private final int noiseSizeY;
    private final int noiseSizeZ;
    protected final ChunkRandom random;
    private final OctavePerlinNoiseSampler lowerInterpolatedNoise;
    private final OctavePerlinNoiseSampler upperInterpolatedNoise;
    private final OctavePerlinNoiseSampler interpolationNoise;
    private final NoiseSampler surfaceDepthNoise;
    private final OctavePerlinNoiseSampler densityNoise;
    @Nullable
    private final SimplexNoiseSampler islandNoise;
    protected final BlockState defaultBlock;
    protected final BlockState defaultFluid;
    private final long worldSeed;
    protected final Supplier<ChunkGeneratorSettings> settings;
    private final int worldHeight;

    public LimboChunkGenerator(BiomeSource biomeSource, Supplier<ChunkGeneratorSettings> supplier) {
        this(biomeSource, biomeSource, supplier);
    }

    private LimboChunkGenerator(BiomeSource biomeSource, BiomeSource biomeSource2, Supplier<ChunkGeneratorSettings> supplier) {
        super(biomeSource, biomeSource2, supplier.get().getStructuresConfig(), new Random().nextLong());
        this.worldSeed = new Random().nextLong();
        ChunkGeneratorSettings chunkGeneratorSettings = supplier.get();
        this.settings = supplier;
        GenerationShapeConfig generationShapeConfig = chunkGeneratorSettings.getGenerationShapeConfig();
        this.worldHeight = generationShapeConfig.getHeight();
        this.verticalNoiseResolution = generationShapeConfig.getSizeVertical() * 4;
        this.horizontalNoiseResolution = generationShapeConfig.getSizeHorizontal() * 4;
        this.defaultBlock = chunkGeneratorSettings.getDefaultBlock();
        this.defaultFluid = chunkGeneratorSettings.getDefaultFluid();
        this.noiseSizeX = 16 / this.horizontalNoiseResolution;
        this.noiseSizeY = generationShapeConfig.getHeight() / this.verticalNoiseResolution;
        this.noiseSizeZ = 16 / this.horizontalNoiseResolution;
        this.random = new ChunkRandom(this.worldSeed);
        this.lowerInterpolatedNoise = new OctavePerlinNoiseSampler(this.random, IntStream.rangeClosed(-15, 0));
        this.upperInterpolatedNoise = new OctavePerlinNoiseSampler(this.random, IntStream.rangeClosed(-15, 0));
        this.interpolationNoise = new OctavePerlinNoiseSampler(this.random, IntStream.rangeClosed(-7, 0));
        this.surfaceDepthNoise = generationShapeConfig.hasSimplexSurfaceNoise() ? new OctaveSimplexNoiseSampler(this.random, IntStream.rangeClosed(-3, 0)) : new OctavePerlinNoiseSampler(this.random, IntStream.rangeClosed(-3, 0));
        this.random.consume(2620);
        this.densityNoise = new OctavePerlinNoiseSampler(this.random, IntStream.rangeClosed(-15, 0));
        if (generationShapeConfig.hasIslandNoiseOverride()) {
            ChunkRandom chunkRandom = new ChunkRandom(this.worldSeed);
            chunkRandom.consume(17292);
            this.islandNoise = new SimplexNoiseSampler(chunkRandom);
        } else {
            this.islandNoise = null;
        }

    }

    public final Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Environment(EnvType.CLIENT)
    public ChunkGenerator withSeed(long seed) {
        return new LimboChunkGenerator(this.biomeSource.withSeed(seed), this.settings);
    }


    public boolean equals(long l, RegistryKey<ChunkGeneratorSettings> registryKey) {
        return this.worldSeed == l && this.settings.get().equals(registryKey);
    }

    private double sampleNoise(int x, int y, int z, double horizontalScale, double verticalScale, double horizontalStretch, double verticalStretch) {
        double d = 0.0D;
        double e = 0.0D;
        double f = 0.0D;
        double g = 1.0D;

        for (int i = 0; i < 16; ++i) {
            double h = OctavePerlinNoiseSampler.maintainPrecision((double) x * horizontalScale * g);
            double j = OctavePerlinNoiseSampler.maintainPrecision((double) y * verticalScale * g);
            double k = OctavePerlinNoiseSampler.maintainPrecision((double) z * horizontalScale * g);
            double l = verticalScale * g;
            PerlinNoiseSampler perlinNoiseSampler = this.lowerInterpolatedNoise.getOctave(i);
            if (perlinNoiseSampler != null) {
                d += perlinNoiseSampler.sample(h, j, k, l, (double) y * l) / g;
            }

            PerlinNoiseSampler perlinNoiseSampler2 = this.upperInterpolatedNoise.getOctave(i);
            if (perlinNoiseSampler2 != null) {
                e += perlinNoiseSampler2.sample(h, j, k, l, (double) y * l) / g;
            }

            if (i < 8) {
                PerlinNoiseSampler perlinNoiseSampler3 = this.interpolationNoise.getOctave(i);
                if (perlinNoiseSampler3 != null) {
                    f += perlinNoiseSampler3.sample(OctavePerlinNoiseSampler.maintainPrecision((double) x * horizontalStretch * g), OctavePerlinNoiseSampler.maintainPrecision((double) y * verticalStretch * g), OctavePerlinNoiseSampler.maintainPrecision((double) z * horizontalStretch * g), verticalStretch * g, (double) y * verticalStretch * g) / g;
                }
            }

            g /= 2.0D;
        }

        return MathHelper.clampedLerp(d / 512.0D, e / 512.0D, (f / 10.0D + 1.0D) / 2.0D);
    }

    private double[] sampleNoiseColumn(int x, int z) {
        double[] ds = new double[this.noiseSizeY + 1];
        this.sampleNoiseColumn(ds, x, z);
        return ds;
    }

    private void sampleNoiseColumn(double[] buffer, int x, int z) {
        GenerationShapeConfig generationShapeConfig = this.settings.get().getGenerationShapeConfig();
        double ac;
        double ad;
        double ai;
        double aj;
        if (this.islandNoise != null) {
            ac = TheEndBiomeSource.getNoiseAt(this.islandNoise, x, z) - 8.0F;
            if (ac > 0.0D) {
                ad = 0.25D;
            } else {
                ad = 1.0D;
            }
        } else {
            float g = 0.0F;
            float h = 0.0F;
            float i = 0.0F;
            int k = this.getSeaLevel();
            float l = this.biomeSource.getBiomeForNoiseGen(x, k, z).getDepth();

            for (int m = -2; m <= 2; ++m) {
                for (int n = -2; n <= 2; ++n) {
                    Biome biome = this.biomeSource.getBiomeForNoiseGen(x + m, k, z + n);
                    float o = biome.getDepth();
                    float p = biome.getScale();
                    float s;
                    float t;
                    if (generationShapeConfig.isAmplified() && o > 0.0F) {
                        s = 1.0F + o * 2.0F;
                        t = 1.0F + p * 4.0F;
                    } else {
                        s = o;
                        t = p;
                    }

                    float u = o > l ? 0.5F : 1.0F;
                    float v = u * BIOME_WEIGHT_TABLE[m + 2 + (n + 2) * 5] / (s + 2.0F);
                    g += t * v;
                    h += s * v;
                    i += v;
                }
            }

            float w = h / i;
            float y = g / i;
            ai = w * 0.5F - 0.125F;
            aj = y * 0.9F + 0.1F;
            ac = ai * 0.265625D;
            ad = 96.0D / aj;
        }

        double ae = 684.412D * generationShapeConfig.getSampling().getXZScale();
        double af = 684.412D * generationShapeConfig.getSampling().getYScale();
        double ag = ae / generationShapeConfig.getSampling().getXZFactor();
        double ah = af / generationShapeConfig.getSampling().getYFactor();
        ai = generationShapeConfig.getTopSlide().getTarget();
        aj = generationShapeConfig.getTopSlide().getSize();
        double ak = generationShapeConfig.getTopSlide().getOffset();
        double al = generationShapeConfig.getBottomSlide().getTarget();
        double am = generationShapeConfig.getBottomSlide().getSize();
        double an = generationShapeConfig.getBottomSlide().getOffset();
        double ao = generationShapeConfig.hasRandomDensityOffset() ? this.getRandomDensityAt(x, z) : 0.0D;
        double ap = generationShapeConfig.getDensityFactor();
        double aq = generationShapeConfig.getDensityOffset();

        for (int ar = 0; ar <= this.noiseSizeY; ++ar) {
            double as = this.sampleNoise(x, ar, z, ae, af, ag, ah);
            double at = 1.0D - (double) ar * 2.0D / (double) this.noiseSizeY + ao;
            double au = at * ap + aq;
            double av = (au + ac) * ad;
            if (av > 0.0D) {
                as += av * 4.0D;
            } else {
                as += av;
            }

            double ax;
            if (aj > 0.0D) {
                ax = ((double) (this.noiseSizeY - ar) - ak) / aj;
                as = MathHelper.clampedLerp(ai, as, ax);
            }

            if (am > 0.0D) {
                ax = ((double) ar - an) / am;
                as = MathHelper.clampedLerp(al, as, ax);
            }

            buffer[ar] = as;
        }

    }

    private double getRandomDensityAt(int x, int z) {
        double d = this.densityNoise.sample(x * 200, 10.0D, z * 200, 1.0D, 0.0D, true);
        double f;
        if (d < 0.0D) {
            f = -d * 0.3D;
        } else {
            f = d;
        }

        double g = f * 24.575625D - 2.0D;
        return g < 0.0D ? g * 0.009486607142857142D : Math.min(g, 1.0D) * 0.006640625D;
    }

    public int getHeight(int x, int z, Heightmap.Type heightmapType) {
        return this.sampleHeightmap(x, z, null, heightmapType.getBlockPredicate());
    }

    public BlockView getColumnSample(int x, int z) {
        BlockState[] blockStates = new BlockState[this.noiseSizeY * this.verticalNoiseResolution];
        this.sampleHeightmap(x, z, blockStates, null);
        return new VerticalBlockSample(blockStates);
    }

    private int sampleHeightmap(int x, int z, @Nullable BlockState[] states, @Nullable Predicate<BlockState> predicate) {
        int i = Math.floorDiv(x, this.horizontalNoiseResolution);
        int j = Math.floorDiv(z, this.horizontalNoiseResolution);
        int k = Math.floorMod(x, this.horizontalNoiseResolution);
        int l = Math.floorMod(z, this.horizontalNoiseResolution);
        double d = (double) k / (double) this.horizontalNoiseResolution;
        double e = (double) l / (double) this.horizontalNoiseResolution;
        double[][] ds = new double[][]{this.sampleNoiseColumn(i, j), this.sampleNoiseColumn(i, j + 1), this.sampleNoiseColumn(i + 1, j), this.sampleNoiseColumn(i + 1, j + 1)};

        for (int m = this.noiseSizeY - 1; m >= 0; --m) {
            double f = ds[0][m];
            double g = ds[1][m];
            double h = ds[2][m];
            double n = ds[3][m];
            double o = ds[0][m + 1];
            double p = ds[1][m + 1];
            double q = ds[2][m + 1];
            double r = ds[3][m + 1];

            for (int s = this.verticalNoiseResolution - 1; s >= 0; --s) {
                double t = (double) s / (double) this.verticalNoiseResolution;
                double u = MathHelper.lerp3(t, d, e, f, o, h, q, g, p, n, r);
                int v = m * this.verticalNoiseResolution + s;
                BlockState blockState = this.getBlockState(u, v);
                if (states != null) {
                    states[v] = blockState;
                }

                if (predicate != null && predicate.test(blockState)) {
                    return v + 1;
                }
            }
        }

        return 0;
    }

    protected BlockState getBlockState(double density, int y) {
        BlockState blockState3;
        if (density > 0.0D) {
            blockState3 = this.defaultBlock;
        } else if (y < this.getSeaLevel()) {
            blockState3 = this.defaultFluid;
        } else {
            blockState3 = AIR;
        }

        return blockState3;
    }

    public void buildSurface(ChunkRegion region, Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int i = chunkPos.x;
        int j = chunkPos.z;
        ChunkRandom chunkRandom = new ChunkRandom();
        chunkRandom.setTerrainSeed(i, j);
        ChunkPos chunkPos2 = chunk.getPos();
        int k = chunkPos2.getStartX();
        int l = chunkPos2.getStartZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int m = 0; m < 16; ++m) {
            for (int n = 0; n < 16; ++n) {
                int o = k + m;
                int p = l + n;
                int q = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, m, n) + 1;
                double e = this.surfaceDepthNoise.sample((double) o * 0.0625D, (double) p * 0.0625D, 0.0625D, (double) m * 0.0625D) * 15.0D;
                region.getBiome(mutable.set(k + m, q, l + n)).buildSurface(chunkRandom, chunk, o, p, q, e, this.defaultBlock, this.defaultFluid, this.getSeaLevel(), region.getSeed());
            }
        }

        this.buildEternalFluidFloor(chunk, chunkRandom);
    }

    private void buildEternalFluidFloor(Chunk chunk, Random random) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int i = chunk.getPos().getStartX();
        int j = chunk.getPos().getStartZ();
        ChunkGeneratorSettings chunkGeneratorSettings = this.settings.get();
        int k = chunkGeneratorSettings.getBedrockFloorY();
        int l = this.worldHeight - 1 - chunkGeneratorSettings.getBedrockCeilingY();
        boolean bl = l + 4 >= 0 && l < this.worldHeight;
        boolean bl2 = k + 4 >= 0 && k < this.worldHeight;
        if (bl || bl2) {
            Iterator<BlockPos> iterator = BlockPos.iterate(i, 0, j, i + 15, 0, j + 15).iterator();

            while (true) {
                BlockPos blockPos;
                int o;
                do {
                    if (!iterator.hasNext()) {
                        return;
                    }

                    blockPos = iterator.next();
                    if (bl) {
                        for (o = 0; o < 5; ++o) {
                            if (o <= random.nextInt(5)) {
                                chunk.setBlockState(mutable.set(blockPos.getX(), l - o, blockPos.getZ()), ModBlocks.ETERNAL_FLUID.getDefaultState(), false);
                            }
                        }
                    }
                } while (!bl2);

                for (o = 4; o >= 0; --o) {
                    if (o <= random.nextInt(5)) {
                        chunk.setBlockState(mutable.set(blockPos.getX(), k + o, blockPos.getZ()), ModBlocks.ETERNAL_FLUID.getDefaultState(), false);
                    }
                }
            }
        }
    }

    public void populateNoise(WorldAccess world, StructureAccessor accessor, Chunk chunk) {
        ObjectList<StructurePiece> objectList = new ObjectArrayList<>(10);
        ObjectList<JigsawJunction> objectList2 = new ObjectArrayList<>(32);
        ChunkPos chunkPos = chunk.getPos();
        int i = chunkPos.x;
        int j = chunkPos.z;
        int k = i << 4;
        int l = j << 4;

        for (StructureFeature<?> structureFeature : StructureFeature.JIGSAW_STRUCTURES) {
            accessor.getStructuresWithChildren(ChunkSectionPos.from(chunkPos, 0), structureFeature).forEach((start) -> {
                Iterator<StructurePiece> iterator = start.getChildren().iterator();

                while (true) {
                    StructurePiece structurePiece;
                    do {
                        if (!iterator.hasNext()) {
                            return;
                        }

                        structurePiece = iterator.next();
                    } while (!structurePiece.intersectsChunk(chunkPos, 12));

                    if (structurePiece instanceof PoolStructurePiece) {
                        PoolStructurePiece poolStructurePiece = (PoolStructurePiece) structurePiece;
                        StructurePool.Projection projection = poolStructurePiece.getPoolElement().getProjection();
                        if (projection == StructurePool.Projection.RIGID) {
                            objectList.add(poolStructurePiece);
                        }

                        for (JigsawJunction jigsawJunction : poolStructurePiece.getJunctions()) {
                            int kx = jigsawJunction.getSourceX();
                            int lx = jigsawJunction.getSourceZ();
                            if (kx > k - 12 && lx > l - 12 && kx < k + 15 + 12 && lx < l + 15 + 12) {
                                objectList2.add(jigsawJunction);
                            }
                        }
                    } else {
                        objectList.add(structurePiece);
                    }
                }
            });
        }

        double[][][] ds = new double[2][this.noiseSizeZ + 1][this.noiseSizeY + 1];

        for (int m = 0; m < this.noiseSizeZ + 1; ++m) {
            ds[0][m] = new double[this.noiseSizeY + 1];
            this.sampleNoiseColumn(ds[0][m], i * this.noiseSizeX, j * this.noiseSizeZ + m);
            ds[1][m] = new double[this.noiseSizeY + 1];
        }

        ProtoChunk protoChunk = (ProtoChunk) chunk;
        Heightmap heightmap = protoChunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap heightmap2 = protoChunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        ObjectListIterator<StructurePiece> objectListIterator = objectList.iterator();
        ObjectListIterator<JigsawJunction> objectListIterator2 = objectList2.iterator();

        for (int n = 0; n < this.noiseSizeX; ++n) {
            int p;
            for (p = 0; p < this.noiseSizeZ + 1; ++p) {
                this.sampleNoiseColumn(ds[1][p], i * this.noiseSizeX + n + 1, j * this.noiseSizeZ + p);
            }

            for (p = 0; p < this.noiseSizeZ; ++p) {
                ChunkSection chunkSection = protoChunk.getSection(15);
                chunkSection.lock();

                for (int q = this.noiseSizeY - 1; q >= 0; --q) {
                    double d = ds[0][p][q];
                    double e = ds[0][p + 1][q];
                    double f = ds[1][p][q];
                    double g = ds[1][p + 1][q];
                    double h = ds[0][p][q + 1];
                    double r = ds[0][p + 1][q + 1];
                    double s = ds[1][p][q + 1];
                    double t = ds[1][p + 1][q + 1];

                    for (int u = this.verticalNoiseResolution - 1; u >= 0; --u) {
                        int v = q * this.verticalNoiseResolution + u;
                        int w = v & 15;
                        int x = v >> 4;
                        if (chunkSection.getYOffset() >> 4 != x) {
                            chunkSection.unlock();
                            chunkSection = protoChunk.getSection(x);
                            chunkSection.lock();
                        }

                        double y = (double) u / (double) this.verticalNoiseResolution;
                        double z = MathHelper.lerp(y, d, h);
                        double aa = MathHelper.lerp(y, f, s);
                        double ab = MathHelper.lerp(y, e, r);
                        double ac = MathHelper.lerp(y, g, t);

                        for (int ad = 0; ad < this.horizontalNoiseResolution; ++ad) {
                            int ae = k + n * this.horizontalNoiseResolution + ad;
                            int af = ae & 15;
                            double ag = (double) ad / (double) this.horizontalNoiseResolution;
                            double ah = MathHelper.lerp(ag, z, aa);
                            double ai = MathHelper.lerp(ag, ab, ac);

                            for (int aj = 0; aj < this.horizontalNoiseResolution; ++aj) {
                                int ak = l + p * this.horizontalNoiseResolution + aj;
                                int al = ak & 15;
                                double am = (double) aj / (double) this.horizontalNoiseResolution;
                                double an = MathHelper.lerp(am, ah, ai);
                                double ao = MathHelper.clamp(an / 200.0D, -1.0D, 1.0D);

                                int at;
                                int au;
                                int ar;
                                for (ao = ao / 2.0D - ao * ao * ao / 24.0D; objectListIterator.hasNext(); ao += getNoiseWeight(at, au, ar) * 0.8D) {
                                    StructurePiece structurePiece = objectListIterator.next();
                                    BlockBox blockBox = structurePiece.getBoundingBox();
                                    at = Math.max(0, Math.max(blockBox.minX - ae, ae - blockBox.maxX));
                                    au = v - (blockBox.minY + (structurePiece instanceof PoolStructurePiece ? ((PoolStructurePiece) structurePiece).getGroundLevelDelta() : 0));
                                    ar = Math.max(0, Math.max(blockBox.minZ - ak, ak - blockBox.maxZ));
                                }

                                objectListIterator.back(objectList.size());

                                while (objectListIterator2.hasNext()) {
                                    JigsawJunction jigsawJunction = objectListIterator2.next();
                                    int as = ae - jigsawJunction.getSourceX();
                                    at = v - jigsawJunction.getSourceGroundY();
                                    au = ak - jigsawJunction.getSourceZ();
                                    ao += getNoiseWeight(as, at, au) * 0.4D;
                                }

                                objectListIterator2.back(objectList2.size());
                                BlockState blockState = this.getBlockState(ao, v);
                                if (blockState != AIR) {
                                    if (blockState.getLuminance() != 0) {
                                        mutable.set(ae, v, ak);
                                        protoChunk.addLightSource(mutable);
                                    }

                                    chunkSection.setBlockState(af, w, al, blockState, false);
                                    heightmap.trackUpdate(af, v, al, blockState);
                                    heightmap2.trackUpdate(af, v, al, blockState);
                                }
                            }
                        }
                    }
                }

                chunkSection.unlock();
            }

            double[][] es = ds[0];
            ds[0] = ds[1];
            ds[1] = es;
        }

    }

    private static double getNoiseWeight(int x, int y, int z) {
        int i = x + 12;
        int j = y + 12;
        int k = z + 12;
        if (i >= 0 && i < 24) {
            if (j >= 0 && j < 24) {
                return k >= 0 && k < 24 ? (double) NOISE_WEIGHT_TABLE[k * 24 * 24 + i * 24 + j] : 0.0D;
            } else {
                return 0.0D;
            }
        } else {
            return 0.0D;
        }
    }

    private static double calculateNoiseWeight(int x, int y, int z) {
        double d = x * x + z * z;
        double e = (double) y + 0.5D;
        double f = e * e;
        double g = Math.pow(2.718281828459045D, -(f / 16.0D + d / 16.0D));
        double h = -e * MathHelper.fastInverseSqrt(f / 2.0D + d / 2.0D) / 2.0D;
        return h * g;
    }

    public int getMaxY() {
        return this.worldHeight;
    }

    public int getSeaLevel() {
        return this.settings.get().getSeaLevel();
    }

    public void populateEntities(ChunkRegion region) {
        int i = region.getCenterChunkX();
        int j = region.getCenterChunkZ();
        Biome biome = region.getBiome((new ChunkPos(i, j)).getCenterBlockPos());
        ChunkRandom chunkRandom = new ChunkRandom();
        chunkRandom.setPopulationSeed(region.getSeed(), i << 4, j << 4);
        SpawnHelper.populateEntities(region, biome, i, j, chunkRandom);
    }
}
