/**
 * Designed and written by Damian Coventry
 * Copyright (c) 2022, all rights reserved
 *
 * Massey University
 * 159.355 Concurrent Systems
 * Assignment 3
 * 2022 Semester 1
 *
 */

import java.io.IOException;

/**
 * This interface represents the contract between a villager's main thread and its message receiving thread. The message
 * receiving thread has very little state for itself. It delegates almost all state reading/writing decisions through
 * this interface.
 *
 * The trade-off, therefore, is the implementation of this interface will need to use synchronised methods so that the
 * receiving thread can make a meaningful contribution to the Ricart-Agrawala algorithm.
 */
public interface IVillager {
    /**
     * Returns this villager's address. This is solely used by the Payload class to insert this villager's index into outgoing
     * messages. Other villagers need this number when recording received state via a message.
     * @return this villager's address
     */
    VillagerAddress getMyId();

    boolean hasToken();
    String getToken();

    /**
     * Returns whether the villager is currently requesting mini mart access. This is a core part of the Ricart-Agrawala
     * algorithm.
     * @see IRequestsMiniMartAccess
     * @see MiniMartAccess
     * @return whether the villager is currently requesting mini mart access
     */
    boolean isNotRequestingMiniMartAccess();

    /**
     * We need to record the fact that another villager has finished shopping. This affects when this node shuts down.
     * @param message a message received from another villager
     */
    void recordFinishedShopping(Message message);

    void recordRequestForToken(Message message);

    void recordTokenAndGrantedList(Message message);

    void sendTokenToAnotherVillager() throws IOException;
}
