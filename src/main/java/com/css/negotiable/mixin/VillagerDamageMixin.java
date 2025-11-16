package com.css.negotiable.mixin;

import com.css.negotiable.Negotiable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class VillagerDamageMixin {
    @Inject(method = "damage", at = @At("HEAD"))
    private void negotiable$onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof LivingEntity victim)) {
            return;
        }
        if (!(victim instanceof VillagerEntity) && !(victim instanceof WanderingTraderEntity)) {
            return;
        }
        if (source.getAttacker() instanceof PlayerEntity attacker) {
            Negotiable.NEGOTIATION_MANAGER.registerVillagerHit(victim, attacker);
        }
    }
}