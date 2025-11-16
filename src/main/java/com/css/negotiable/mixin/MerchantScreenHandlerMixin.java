package com.css.negotiable.mixin;

import com.css.negotiable.Negotiable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.village.Merchant;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(MerchantScreenHandler.class)
public class MerchantScreenHandlerMixin {

    @Inject(
        method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/village/Merchant;)V",
        at = @At("RETURN")
    )
    private void negotiable$applyAnchors(
        int syncId,
        PlayerInventory inventory,
        Merchant merchant,
        CallbackInfo ci
    ) {
        PlayerEntity player = inventory.player;
        if (!(merchant instanceof LivingEntity living)) {
            return;
        }

        List<TradeOffer> offers = merchant.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            TradeOffer offer = offers.get(i);
            Negotiable.NEGOTIATION_MANAGER.applyAnchorDiff(player, living, i, offer);
        }
    }
}
