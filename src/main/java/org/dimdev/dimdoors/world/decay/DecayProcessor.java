package org.dimdev.dimdoors.world.decay;

import com.mojang.serialization.Lifecycle;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.world.World;
import org.dimdev.dimdoors.DimensionalDoorsInitializer;
import org.dimdev.dimdoors.Util;
import org.dimdev.dimdoors.world.decay.processors.DoorDecayProccessor;
import org.dimdev.dimdoors.world.decay.processors.DoubleDecayProcessor;
import org.dimdev.dimdoors.world.decay.processors.SelfDecayProcessor;
import org.dimdev.dimdoors.world.decay.processors.SimpleDecayProcesor;

import java.util.function.Supplier;

public interface DecayProcessor {
    Registry<DecayProcessor.DecayProcessorType<? extends DecayProcessor>> REGISTRY = FabricRegistryBuilder.from(new SimpleRegistry<DecayProcessor.DecayProcessorType<? extends DecayProcessor>>(RegistryKey.ofRegistry(Util.id("decay_processor_type")), Lifecycle.stable(), null)).buildAndRegister();

    DecayProcessor NONE = new DecayProcessor() {
        @Override
        public DecayProcessor fromNbt(NbtCompound nbt) {
            return this;
        }

        @Override
        public DecayProcessorType<? extends DecayProcessor> getType() {
            return DecayProcessor.DecayProcessorType.NONE_PROCESSOR_TYPE;
        }

        @Override
        public String getKey() {
            return ID;
        }

        @Override
        public int process(World world, BlockPos pos, BlockState origin, BlockState target) {
            return 0;
        }

        private static final String ID = "none";
    };

    static DecayProcessor deserialize(NbtCompound nbt) {
        Identifier id = Identifier.tryParse(nbt.getString("type"));
        return REGISTRY.getOrEmpty(id).orElse(DecayProcessorType.NONE_PROCESSOR_TYPE).fromNbt(nbt);
    }

    static NbtCompound serialize(DecayProcessor modifier) {
        return modifier.toNbt(new NbtCompound());
    }


    DecayProcessor fromNbt(NbtCompound nbt);

    default NbtCompound toNbt(NbtCompound nbt) {
        return this.getType().toNbt(nbt);
    }

    DecayProcessor.DecayProcessorType<? extends DecayProcessor> getType();

    String getKey();

    int process(World world, BlockPos pos, BlockState origin, BlockState target);

    interface DecayProcessorType<T extends DecayProcessor> {
        DecayProcessorType<SimpleDecayProcesor> SIMPLE_PROCESSOR_TYPE = register(Util.id(SimpleDecayProcesor.KEY), SimpleDecayProcesor::new);
        DecayProcessorType<DecayProcessor> NONE_PROCESSOR_TYPE = register(Util.id("none"), () -> NONE);
        DecayProcessorType<SelfDecayProcessor> SELF = register(Util.id(SelfDecayProcessor.KEY), SelfDecayProcessor::instance);
		DecayProcessorType<? extends DecayProcessor> DOOR_PROCESSOR_TYPE = register(Util.id(DoorDecayProccessor.KEY), DoorDecayProccessor::new);
		DecayProcessorType<? extends DecayProcessor> DOUBLE_PROCESSOR_TYPE = register(Util.id(DoubleDecayProcessor.KEY), DoubleDecayProcessor::new);

		DecayProcessor fromNbt(NbtCompound nbt);

		NbtCompound toNbt(NbtCompound nbt);

        static void register() {
            DimensionalDoorsInitializer.apiSubscribers.forEach(d -> d.registerDecayProcessors(REGISTRY));
        }

        static <U extends DecayProcessor> DecayProcessorType<U> register(Identifier id, Supplier<U> factory) {
            return Registry.register(REGISTRY, id, new DecayProcessorType<U>() {
                @Override
                public DecayProcessor fromNbt(NbtCompound nbt) {
                    return factory.get().fromNbt(nbt);
                }

                @Override
                public NbtCompound toNbt(NbtCompound nbt) {
                    nbt.putString("type", id.toString());
                    return nbt;
                }
            });
        }
    }
}
