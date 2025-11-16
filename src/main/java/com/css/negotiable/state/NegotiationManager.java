package com.css.negotiable.state;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.village.TradeOffer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NegotiationManager {

    private final Map<UUID, VillagerNegotiationData> villagerData = new HashMap<>();

    public static final int MAX_ATTEMPTS_PER_DAY = 3;
    public static final int TEMPER_THRESHOLD = 100;
    public static final long LOCKOUT_DURATION_TICKS = 24000L;
    public static final long HIT_LOCK_DURATION_TICKS = 6000L;

    public NegotiationManager() {
    }

    private VillagerNegotiationData getData(UUID villagerId) {
        return villagerData.computeIfAbsent(villagerId, id -> new VillagerNegotiationData());
    }

    public NegotiationResult negotiate(PlayerEntity player,
                                       LivingEntity merchantEntity,
                                       int offerIndex,
                                       TradeOffer offer,
                                       int offeredPrice) {
        UUID villagerId = merchantEntity.getUuid();
        UUID playerId = player.getUuid();
        long currentTime = merchantEntity.getWorld().getTime();

        VillagerNegotiationData data = getData(villagerId);
        OfferState offerState = data.offers.computeIfAbsent(offerIndex, i -> new OfferState());
        PlayerOfferState playerState = offerState.perPlayer.computeIfAbsent(playerId, p -> new PlayerOfferState());

        long currentDay = currentTime / 24000L;
        if (playerState.lastInteractionDay != currentDay) {
            playerState.lastInteractionDay = currentDay;
            playerState.attemptsToday = 0;
        }

        Long globalLockUntil = data.lockAllUntilByPlayer.get(playerId);
        if (globalLockUntil != null && globalLockUntil > currentTime) {
            return NegotiationResult.locked(playerState.temper);
        }

        if (playerState.lockoutUntil > currentTime) {
            return NegotiationResult.locked(playerState.temper);
        }

        if (playerState.attemptsToday >= MAX_ATTEMPTS_PER_DAY) {
            return NegotiationResult.locked(playerState.temper);
        }

        int Po = Math.max(1, offeredPrice);

        int adjustedPrice = offer.getAdjustedFirstBuyItem().getCount();
        int specialPrice = ((com.css.negotiable.mixin.TradeOfferAccessor) (Object) offer)
            .negotiable$getSpecialPrice();
        int vanillaPrice = adjustedPrice - specialPrice;

        if (Po > adjustedPrice) {
            return NegotiationResult.locked(playerState.temper);
        }

        int anchorDelta = 0;
        if (offerState.anchoredBasePrice != null) {
            anchorDelta = offerState.anchoredBasePrice - vanillaPrice;
        }

        int P0 = Math.max(1, vanillaPrice + anchorDelta);

        int Pmin = Math.max(1, P0 / 2);

        if (Po < Pmin) {
            increaseTemper(playerState, 20);
            playerState.attemptsToday++;
            checkLockout(playerState, currentTime);
            return NegotiationResult.rejected(playerState.temper);
        }

        float ratio = (float) (Po - Pmin) / (float) (P0 - Pmin);
        float chance = 0.05f + 0.90f * ratio;
        chance = Math.max(0.05f, Math.min(chance, 0.95f));

        float rand = merchantEntity.getWorld().getRandom().nextFloat();
        boolean accepted = rand < chance;

        playerState.attemptsToday++;

        if (accepted) {
            offerState.anchoredBasePrice = Po;
            decreaseTemper(playerState, 10);
            return NegotiationResult.accepted(Po, playerState.temper);
        } else {
            increaseTemper(playerState, 10);
            checkLockout(playerState, currentTime);
            return NegotiationResult.rejected(playerState.temper);
        }
    }

    public void registerVillagerHit(LivingEntity villager, PlayerEntity attacker) {
        UUID villagerId = villager.getUuid();
        UUID playerId = attacker.getUuid();
        long currentTime = villager.getWorld().getTime();
        VillagerNegotiationData data = getData(villagerId);
        data.lockAllUntilByPlayer.put(playerId, currentTime + HIT_LOCK_DURATION_TICKS);
    }

    public void applyAnchorDiff(PlayerEntity player,
                                LivingEntity merchant,
                                int offerIndex,
                                TradeOffer offer) {
        UUID villagerId = merchant.getUuid();
        UUID playerId = player.getUuid();
        VillagerNegotiationData data = villagerData.get(villagerId);
        if (data == null) {
            return;
        }
        OfferState offerState = data.offers.get(offerIndex);
        if (offerState == null) {
            return;
        }

        int adjustedPrice = offer.getAdjustedFirstBuyItem().getCount();
        com.css.negotiable.mixin.TradeOfferAccessor accessor =
                (com.css.negotiable.mixin.TradeOfferAccessor) (Object) offer;
        int specialPrice = accessor.negotiable$getSpecialPrice();
        int vanillaPrice = adjustedPrice - specialPrice;

        int anchorDelta = 0;
        if (offerState.anchoredBasePrice != null) {
            anchorDelta = offerState.anchoredBasePrice - vanillaPrice;
        }

        long currentTime = merchant.getWorld().getTime();
        Long hitLock = data.lockAllUntilByPlayer.get(playerId);
        int surcharge = 0;
        if (hitLock != null && hitLock > currentTime) {
            int base = vanillaPrice + anchorDelta;
            if (base > 0) {
                surcharge = Math.max(1, base / 20);
            }
        }

        int newSpecialPrice = anchorDelta + surcharge;
        if (newSpecialPrice != specialPrice) {
            accessor.negotiable$setSpecialPrice(newSpecialPrice);
        }
    }

    private void increaseTemper(PlayerOfferState playerState, int amount) {
        playerState.temper = Math.min(200, playerState.temper + amount);
    }

    private void decreaseTemper(PlayerOfferState playerState, int amount) {
        playerState.temper = Math.max(0, playerState.temper - amount);
    }

    private void checkLockout(PlayerOfferState playerState, long currentTime) {
        if (playerState.temper >= TEMPER_THRESHOLD
                || playerState.attemptsToday >= MAX_ATTEMPTS_PER_DAY) {
            playerState.lockoutUntil = currentTime + LOCKOUT_DURATION_TICKS;
            playerState.temper = Math.min(200, playerState.temper + 10);
        }
    }

    public static class VillagerNegotiationData {
        public final Map<Integer, OfferState> offers = new HashMap<>();
        public final Map<UUID, Long> lockAllUntilByPlayer = new HashMap<>();
    }

    public static class OfferState {
        public Integer anchoredBasePrice;
        public final Map<UUID, PlayerOfferState> perPlayer = new HashMap<>();
    }

    public static class PlayerOfferState {
        public int temper;
        public int attemptsToday;
        public long lastInteractionDay;
        public long lockoutUntil;
    }

    public static class NegotiationResult {
        public final boolean accepted;
        public final int price;
        public final int temper;

        private NegotiationResult(boolean accepted, int price, int temper) {
            this.accepted = accepted;
            this.price = price;
            this.temper = temper;
        }

        public static NegotiationResult accepted(int price, int temper) {
            return new NegotiationResult(true, price, temper);
        }

        public static NegotiationResult rejected(int temper) {
            return new NegotiationResult(false, -1, temper);
        }

        public static NegotiationResult locked(int temper) {
            return new NegotiationResult(false, -1, temper);
        }
    }
}