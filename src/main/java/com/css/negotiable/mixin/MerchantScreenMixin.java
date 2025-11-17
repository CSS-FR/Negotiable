package com.css.negotiable.mixin;

import com.css.negotiable.MerchantScreenNegotiation;
import com.css.negotiable.Negotiable;
import com.css.negotiable.network.NegotiationPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.Merchant;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin
        extends HandledScreen<MerchantScreenHandler>
        implements MerchantScreenNegotiation {

    @Shadow
    private int selectedIndex;

    @Shadow
    protected abstract void syncRecipeIndex();

    @Unique
    private TextFieldWidget negotiable$priceField;
    @Unique
    private ButtonWidget negotiable$button;

    @Unique
    private int negotiable$selectedIndex = -1;
    @Unique
    private int negotiable$currentTemper = -1; // -1 = unknown

    @Unique
    private int negotiable$panelX;
    @Unique
    private int negotiable$panelY;
    @Unique
    private int negotiable$panelWidth;
    @Unique
    private int negotiable$panelHeight;

    @Unique
    private boolean negotiable$acceptedForCurrentTrade = false;

    protected MerchantScreenMixin(MerchantScreenHandler handler,
                                  PlayerInventory inventory,
                                  Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void negotiable$init(CallbackInfo ci) {
        this.negotiable$panelWidth = 100;
        this.negotiable$panelHeight = 160;

        int panelX = this.x + this.backgroundWidth + 4;
        int panelY = this.y + 8;

        if (panelX + this.negotiable$panelWidth > this.width - 4) {
            panelX = this.width - 4 - this.negotiable$panelWidth;
        }

        this.negotiable$panelX = panelX;
        this.negotiable$panelY = panelY;

        this.negotiable$priceField = new TextFieldWidget(
                this.textRenderer,
                panelX + 6,
                panelY + 56,
                70,
                18,
                Text.literal("Offer")
        );
        this.negotiable$priceField.setMaxLength(4);
        this.negotiable$priceField.setText("0");

        this.negotiable$button = ButtonWidget
                .builder(Text.literal("Negotiate"), button -> this.negotiable$onPress())
                .dimensions(panelX + 6, panelY + 82, 88, 20)
                .build();

        this.addDrawableChild(this.negotiable$priceField);
        this.addDrawableChild(this.negotiable$button);

        this.negotiable$selectedIndex = this.selectedIndex;
        this.negotiable$acceptedForCurrentTrade = false;
        this.negotiable$currentTemper = -1;

        this.negotiable$button.active = this.negotiable$selectedIndex >= 0;

        Negotiable.LOGGER.info("[CLIENT] MerchantScreen initialized; panel at {},{}", panelX, panelY);
    }

    @Inject(method = "syncRecipeIndex", at = @At("TAIL"))
    private void negotiable$onSyncRecipeIndex(CallbackInfo ci) {
        this.negotiable$selectedIndex = this.selectedIndex;
        this.negotiable$acceptedForCurrentTrade = false;

        if (this.negotiable$button != null) {
            this.negotiable$button.active = this.negotiable$selectedIndex >= 0;
        }

        Negotiable.LOGGER.info("[CLIENT] syncRecipeIndex -> selectedIndex={}", this.negotiable$selectedIndex);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void negotiable$render(DrawContext context,
                                   int mouseX,
                                   int mouseY,
                                   float delta,
                                   CallbackInfo ci) {
        MinecraftClient client = this.client;
        if (client == null) {
            return;
        }

        int panelX = this.negotiable$panelX;
        int panelY = this.negotiable$panelY;
        int panelWidth = this.negotiable$panelWidth;
        int panelHeight = this.negotiable$panelHeight;

        // Background panel
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xAA000000);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFFFFFFFF);

        // Title
        context.drawText(this.textRenderer, Text.literal("Negotiation"),
                panelX + 6, panelY + 4, 0xFFFFFF, false);

        // Determine current offer (if any)
        TradeOffer currentOffer = null;
        if (this.negotiable$selectedIndex >= 0) {
            List<TradeOffer> offers = this.handler.getRecipes();
            if (this.negotiable$selectedIndex < offers.size()) {
                currentOffer = offers.get(this.negotiable$selectedIndex);
            }
        }

        boolean nonNegotiable = false;

        // Item display or NON-NEGOTIABLE
        if (currentOffer != null) {
            if (currentOffer.getOriginalFirstBuyItem().getItem() != Items.EMERALD) {
                nonNegotiable = true;

                ItemStack barrier = new ItemStack(Items.BARRIER);
                context.drawItem(barrier, panelX + 6, panelY + 18);
                context.drawItemInSlot(this.textRenderer, barrier, panelX + 6, panelY + 18);

                context.drawText(
                        this.textRenderer,
                        Text.literal("NON-NEGOTIABLE"),
                        panelX + 26,
                        panelY + 22,
                        0xFF5555,
                        false
                );
            } else {
                ItemStack sellStack = currentOffer.getSellItem();
                if (sellStack.isEmpty()) {
                    sellStack = currentOffer.getOriginalFirstBuyItem();
                }

                Text itemName = this.negotiable$getDisplayNameForStack(sellStack);

                context.drawItem(sellStack, panelX + 6, panelY + 18);
                context.drawItemInSlot(this.textRenderer, sellStack, panelX + 6, panelY + 18);

                context.drawText(
                        this.textRenderer,
                        itemName,
                        panelX + 26,
                        panelY + 22,
                        0xFFFFFF,
                        false
                );
            }
        } else {
            context.drawText(this.textRenderer, Text.literal("No trade selected"),
                    panelX + 6, panelY + 22, 0xA0A0A0, false);
        }

        // Trade label
        String tradeLabel;
        if (this.negotiable$selectedIndex >= 0) {
            tradeLabel = "Trade #" + (this.negotiable$selectedIndex + 1);
        } else {
            tradeLabel = "Trade #–";
        }
        context.drawText(this.textRenderer, Text.literal(tradeLabel),
                panelX + 6, panelY + 38, 0xA0A0A0, false);

        // Offer box
        int slotX = panelX + 4;
        int slotY = panelY + 52;
        int slotW = 76;
        int slotH = 22;

        context.drawText(this.textRenderer, Text.literal("Offer (emeralds)"),
                panelX + 6, panelY + 44, 0xA0A0A0, false);

        context.fill(slotX, slotY, slotX + slotW, slotY + slotH, 0xFF303030);
        context.drawBorder(slotX, slotY, slotW, slotH, 0xFF808080);

        // Frustration bar
        int barX = panelX + 6;
        int barY = panelY + 130;
        int barWidth = panelWidth - 12;
        int barHeight = 8;

        context.drawText(this.textRenderer, Text.literal("Frustration"),
                barX, barY - 10, 0xA0A0A0, false);

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);

        int temper = this.negotiable$currentTemper;
        if (temper >= 0) {
            float pct = MathHelper.clamp(temper / 100.0F, 0.0F, 1.0F);
            int filled = (int) (barWidth * pct);

            int color;
            if (pct < 0.5f) {
                color = 0xFF00FF00; // green
            } else if (pct < 0.8f) {
                color = 0xFFFFFF00; // yellow
            } else {
                color = 0xFFFF0000; // red
            }

            context.fill(barX, barY, barX + filled, barY + barHeight, color);
        }

        // Button enable/disable logic
        if (this.negotiable$button != null && this.negotiable$priceField != null) {
            if (this.negotiable$acceptedForCurrentTrade) {
                this.negotiable$button.active = false;
                this.negotiable$priceField.setEditable(false);
            } else if (nonNegotiable || currentOffer == null) {
                this.negotiable$button.active = false;
                this.negotiable$priceField.setEditable(false);
            } else {
                this.negotiable$priceField.setEditable(true);

                boolean active = this.negotiable$selectedIndex >= 0;

                int offered = -1;
                if (active) {
                    try {
                        String s = this.negotiable$priceField.getText().trim();
                        if (!s.isEmpty()) {
                            offered = Integer.parseInt(s);
                        }
                    } catch (NumberFormatException ignored) {
                        offered = -1;
                    }
                    if (offered <= 0) {
                        active = false;
                    }
                }

                if (active) {
                    int currentPrice = currentOffer.getAdjustedFirstBuyItem().getCount();
                    if (offered > currentPrice) {
                        active = false;
                    }
                }

                this.negotiable$button.active = active;
            }
        }
    }

    @Unique
    private Text negotiable$getDisplayNameForStack(ItemStack stack) {
        if (stack.isOf(Items.ENCHANTED_BOOK)) {
            Map<Enchantment, Integer> enchants = EnchantmentHelper.get(stack);
            if (!enchants.isEmpty()) {
                Map.Entry<Enchantment, Integer> first = enchants.entrySet().iterator().next();
                Enchantment ench = first.getKey();
                int lvl = first.getValue();
                return ench.getName(lvl);
            }
        }
        return stack.getName();
    }

    @Unique
    private void negotiable$onPress() {
        if (this.client == null || this.client.player == null) {
            return;
        }
        if (this.negotiable$priceField == null || this.negotiable$selectedIndex < 0) {
            return;
        }
        if (this.negotiable$acceptedForCurrentTrade) {
            return;
        }

        String text = this.negotiable$priceField.getText().trim();
        int offeredPrice;
        try {
            offeredPrice = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return;
        }
        if (offeredPrice <= 0) {
            return;
        }

        List<TradeOffer> offers = this.handler.getRecipes();
        if (this.negotiable$selectedIndex < 0
                || this.negotiable$selectedIndex >= offers.size()) {
            return;
        }
        TradeOffer offer = offers.get(this.negotiable$selectedIndex);

        // Only allow when buying WITH emeralds
        if (offer.getOriginalFirstBuyItem().getItem() != Items.EMERALD) {
            return;
        }

        int currentPrice = offer.getAdjustedFirstBuyItem().getCount();
        if (offeredPrice > currentPrice) {
            return;
        }

        this.client.player.sendMessage(
                Text.literal("[Negotiable] click trade " + (this.negotiable$selectedIndex + 1) + " price " + offeredPrice),
                false
        );
        Negotiable.LOGGER.info(
                "[CLIENT] negotiate button pressed: tradeIndex={}, offeredPrice={}, currentPrice={}",
                this.negotiable$selectedIndex, offeredPrice, currentPrice
        );

        MerchantScreenHandler handler = this.handler;
        Merchant merchant = ((MerchantScreenHandlerAccessor) (Object) handler).negotiable$getMerchant();
        if (!(merchant instanceof LivingEntity living)) {
            return;
        }

        int entityId = living.getId();
        Negotiable.LOGGER.info(
                "[CLIENT] sending C2S negotiate: entityId={}, tradeIndex={}, offeredPrice={}",
                entityId, this.negotiable$selectedIndex, offeredPrice
        );

        NegotiationPackets.sendNegotiate(entityId, this.negotiable$selectedIndex, offeredPrice);
    }

    @Override
    public void negotiable$onNegotiationResult(boolean accepted, int price, int temper) {
        this.negotiable$currentTemper = temper;

        if (this.negotiable$priceField != null && price > 0) {
            this.negotiable$priceField.setText(Integer.toString(price));
        }

        if (accepted) {
            this.negotiable$acceptedForCurrentTrade = true;

            try {
                List<TradeOffer> offers = this.handler.getRecipes();
                int offerIndex = this.negotiable$selectedIndex;

                if (offerIndex >= 0 && offerIndex < offers.size()) {
                    TradeOffer offer = offers.get(offerIndex);

                    int oldSpecial = offer.getSpecialPrice();
                    int bestSpecial = oldSpecial;

                    for (int s = -64; s <= 64; s++) {
                        offer.setSpecialPrice(s);
                        int adjusted = offer.getAdjustedFirstBuyItem().getCount();
                        if (adjusted == price) {
                            bestSpecial = s;
                            break;
                        }
                    }

                    offer.setSpecialPrice(bestSpecial);

                    this.selectedIndex = offerIndex;
                    this.syncRecipeIndex();
                }
            } catch (Exception e) {
                Negotiable.LOGGER.error("[CLIENT] Failed to apply negotiated price client-side", e);
            }

            if (this.negotiable$button != null) {
                this.negotiable$button.active = false;
            }
            if (this.negotiable$priceField != null) {
                this.negotiable$priceField.setEditable(false);
            }
        }

        if (this.client != null && this.client.player != null) {
            if (accepted) {
                this.client.player.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0F, 1.0F);
            } else {
                this.client.player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            }
        }
    }
}
