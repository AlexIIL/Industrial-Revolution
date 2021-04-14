package me.steven.indrev.blocks.machine

import me.steven.indrev.api.machines.Tier
import me.steven.indrev.config.IConfig
import me.steven.indrev.registry.MachineRegistry
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemPlacementContext
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.Direction

open class FacingMachineBlock(
    registry: MachineRegistry,
    settings: Settings,
    tier: Tier,
    config: IConfig?,
    screenHandler: ((Int, PlayerInventory, ScreenHandlerContext) -> ScreenHandler)?,
) : MachineBlock(registry, settings, tier, config, screenHandler) {

    override fun getPlacementState(ctx: ItemPlacementContext?): BlockState? {
        super.getPlacementState(ctx)
        return this.defaultState.with(FACING, ctx?.playerLookDirection?.opposite)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>?) {
        super.appendProperties(builder)
        builder?.add(FACING)
    }

    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState {
        return state.with(FACING, HorizontalFacingMachineBlock.getRotated(state[FACING], rotation))
    }

    override fun getFacing(state: BlockState): Direction = state[FACING]

    companion object {
        val FACING: DirectionProperty = Properties.FACING
    }
}