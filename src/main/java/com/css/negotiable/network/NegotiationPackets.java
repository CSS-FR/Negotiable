package com.css.negotiable.network;

import com.css.negotiable.MerchantScreenNegotiation;
import com.css.negotiable.Negotiable;
import com.css.negotiable.state.NegotiationManager.NegotiationResult;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.village.Merchant;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.World;

public final class NegotiationPackets {

    public static final Identifier NEGOTIATE_C2S =
            new Identifier(Negotiable.MOD_ID, "negotiate");
    public static final Identifier NEGOTIATE_RESULT_S2C =
            new Identifier(Negotiable.MOD_ID, "negotiate_result");

    private NegotiationPackets() {
    }

    public static void registerC2S() {
        ServerPlayNetworking.registerGlobalReceiver(
                NEGOTIATE_C2S,
                (server, player, handler, buf, responseSender) -> {
                    int entityId = buf.readInt();
                    int offerIndex = buf.readVarInt();
                    int offeredPrice = buf.readVarInt();

                    server.execute(() -> {
                        Negotiable.LOGGER.info(
                                "[Negotiable] C2S received: player={}, entityId={}, offerIndex={}, offeredPrice={}",
                                player.getName().getString(), entityId, offerIndex, offeredPrice
                        );
                        player.sendMessage(Text.literal("[Negotiable] server got negotiate"), false);

                        World world = player.getWorld();
                        Entity entity = world.getEntityById(entityId);
                        if (!(entity instanceof LivingEntity living) ||
                                !(entity instanceof Merchant merchant)) {
                            return;
                        }

                        java.util.List<TradeOffer> offers = merchant.getOffers();
                        if (offerIndex < 0 || offerIndex >= offers.size()) {
                            return;
                        }

                        TradeOffer offer = offers.get(offerIndex);
                        if (offer.getOriginalFirstBuyItem().getItem() != Items.EMERALD) {
                            return;
                        }

                        NegotiationResult result =
                                Negotiable.NEGOTIATION_MANAGER.negotiate(
                                        player,
                                        living,
                                        offerIndex,
                                        offer,
                                        offeredPrice
                                );

                        PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
                        out.writeBoolean(result.accepted);
                        out.writeInt(entityId);
                        out.writeVarInt(offerIndex);
                        out.writeVarInt(result.price);
                        out.writeVarInt(result.temper);

                        ServerPlayNetworking.send(
                                (ServerPlayerEntity) player,
                                NEGOTIATE_RESULT_S2C,
                                out
                        );

                        if (result.accepted) {
                            Negotiable.NEGOTIATION_MANAGER.applyAnchorDiff(
                                    player,
                                    living,
                                    offerIndex,
                                    offer
                            );

                            if (player.currentScreenHandler instanceof MerchantScreenHandler merchantHandler) {
                                merchantHandler.sendContentUpdates();
                            }
                        }
                    });
                }
        );
    }

    @Environment(EnvType.CLIENT)
    public static void registerS2C() {
        ClientPlayNetworking.registerGlobalReceiver(
                NEGOTIATE_RESULT_S2C,
                (client, handler, buf, responseSender) -> {
                    boolean accepted = buf.readBoolean();
                    int entityId = buf.readInt();
                    int offerIndex = buf.readVarInt();
                    int price = buf.readVarInt();
                    int temper = buf.readVarInt();

                    client.execute(() -> {
                        Negotiable.LOGGER.info(
                                "[Negotiable] S2C result: accepted={}, entityId={}, offerIndex={}, price={}, temper={}",
                                accepted, entityId, offerIndex, price, temper
                        );

                        if (client.player != null) {
                            client.player.sendMessage(
                                    Text.literal(
                                            "[Negotiable] result: " + (accepted ? "accepted" : "rejected")
                                                    + " price=" + price + " temper=" + temper
                                    ),
                                    false
                            );
                        }

                        Screen current = MinecraftClient.getInstance().currentScreen;
                        if (current instanceof MerchantScreen screen &&
                                screen instanceof MerchantScreenNegotiation negotiation) {
                            negotiation.negotiable$onNegotiationResult(
                                    accepted,
                                    price,
                                    temper
                            );
                        }
                    });
                }
        );
    }

    @Environment(EnvType.CLIENT)
    public static void sendNegotiate(int entityId, int offerIndex, int offeredPrice) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entityId);
        buf.writeVarInt(offerIndex);
        buf.writeVarInt(offeredPrice);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal(
                            "[Negotiable] client sending negotiate: entity=" + entityId
                                    + " trade=" + (offerIndex + 1)
                                    + " price=" + offeredPrice
                    ),
                    false
            );
        }
        Negotiable.LOGGER.info(
                "[Negotiable] Client sending: entityId={}, offerIndex={}, offeredPrice={}",
                entityId, offerIndex, offeredPrice
        );

        ClientPlayNetworking.send(NEGOTIATE_C2S, buf);
    }
}
