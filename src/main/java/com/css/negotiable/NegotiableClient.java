package com.css.negotiable;

import com.css.negotiable.network.NegotiationPackets;
import net.fabricmc.api.ClientModInitializer;

public class NegotiableClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Negotiable.LOGGER.info("[CLIENT] NegotiableClient initialized; S2C packets registered.");
        NegotiationPackets.registerS2C();
    }
}
