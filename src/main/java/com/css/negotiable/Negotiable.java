package com.css.negotiable;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.css.negotiable.state.NegotiationManager;
import com.css.negotiable.network.NegotiationPackets;

public class Negotiable implements ModInitializer {
    public static final String MOD_ID = "negotiable";

    public static final NegotiationManager NEGOTIATION_MANAGER = new NegotiationManager();

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        NegotiationPackets.registerC2S();
        LOGGER.info("Negotiable mod initialised.");
    }
}
