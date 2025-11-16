package com.css.negotiable.mixin;

import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TradeOffer.class)
public interface TradeOfferAccessor {
    @Accessor("specialPrice")
    int negotiable$getSpecialPrice();

    @Accessor("specialPrice")
    void negotiable$setSpecialPrice(int value);
}
