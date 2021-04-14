package me.steven.indrev.blockentities.crafters

import alexiil.mc.lib.attributes.fluid.amount.FluidAmount
import me.steven.indrev.api.machines.Tier
import me.steven.indrev.components.fluid.FluidComponent
import me.steven.indrev.inventories.inventory
import me.steven.indrev.items.upgrade.Upgrade
import me.steven.indrev.recipes.machines.CondenserRecipe
import me.steven.indrev.recipes.machines.IRRecipeType
import me.steven.indrev.registry.MachineRegistry

class CondenserBlockEntity(tier: Tier) :
    CraftingMachineBlockEntity<CondenserRecipe>(tier, MachineRegistry.CONDENSER_REGISTRY) {

    override val upgradeSlots: IntArray = intArrayOf(3, 4, 5, 6)
    override val availableUpgrades: Array<Upgrade> = Upgrade.DEFAULT

    init {
        this.inventoryComponent = inventory(this) {
            output { slot = 2 }
            coolerSlot = 1
        }
        this.fluidComponent = FluidComponent({ this }, FluidAmount.ofWhole(8))
    }

    override val type: IRRecipeType<CondenserRecipe> = CondenserRecipe.TYPE

    override fun getMaxUpgrade(upgrade: Upgrade): Int {
        return if (upgrade == Upgrade.SPEED) return 4 else super.getMaxUpgrade(upgrade)
    }
}