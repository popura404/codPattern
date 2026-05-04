package com.cdp.codpattern.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class ZombiesPowerSwitchBlock extends Block {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public ZombiesPowerSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, Boolean.FALSE));
    }

    public static boolean setPowered(Level level, BlockPos pos, boolean powered) {
        if (level == null || pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ZombiesPowerSwitchBlock) || !state.hasProperty(POWERED)) {
            return false;
        }
        if (state.getValue(POWERED) == powered) {
            return true;
        }

        BlockState updatedState = state.setValue(POWERED, powered);
        boolean changed = level.setBlock(pos, updatedState, Block.UPDATE_ALL);
        if (changed) {
            level.updateNeighborsAt(pos, updatedState.getBlock());
        }
        return changed;
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (!level.isClientSide && !state.getValue(POWERED)) {
            setPowered(level, pos, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
}
