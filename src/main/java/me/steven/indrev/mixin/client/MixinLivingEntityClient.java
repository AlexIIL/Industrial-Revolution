package me.steven.indrev.mixin.client;

import me.steven.indrev.api.IRPlayerEntityExtension;
import me.steven.indrev.tools.modular.ArmorModule;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityClient {
    @Inject(method = "hasStatusEffect", at = @At("INVOKE"), cancellable = true)
    private void indrev_nightVision(StatusEffect effect, CallbackInfoReturnable<Boolean> cir) {
        if (
                effect.equals(StatusEffects.NIGHT_VISION)
                && this instanceof IRPlayerEntityExtension
                && ((IRPlayerEntityExtension) this).isApplied(ArmorModule.NIGHT_VISION)
        ) {
            cir.setReturnValue(true);
        }
    }
}
