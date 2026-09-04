package com.mohistmc.academy.api.event;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/** Modern counterpart of 1.0.7's TutorialActivatedEvent. */
public final class TutorialEvents {
    private TutorialEvents() {}

    public static final class Activated extends Event {
        public final Player player;
        public final String tutorialId;

        public Activated(Player player, String tutorialId) {
            this.player = player;
            this.tutorialId = tutorialId;
        }
    }
}
