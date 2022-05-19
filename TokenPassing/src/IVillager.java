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

    /**
     * Determines whether a villager possesses the token or not
     * @return true if the villager has the token, false if not
     */
    boolean hasToken();

    /**
     * Returns the token. To avoid receiving a null string, use the hasToken() method first.
     * @return null, or the token
     */
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
     * We need to record the fact that another villager has finished shopping. This prevents one villager sending the
     * token to a villager that has already finished shopping and shut down.
     * @param message a message received from another villager
     */
    void recordFinishedShopping(Message message);

    /**
     * Records the fact that another village has asked for the token. When this villager has exited the mini mart they
     * will randomly choose another village to send the token to.
     * @param message a message received from another villager
     */
    void recordRequestForToken(Message message);

    /**
     * Saves the token and the granted list into internal storage. After this method completes this villager may enter
     * the mini mart because there is only one token, and if a villager possesses it then they have mutual exclusivity.
     * @param message a message received from another villager
     */
    void recordTokenAndGrantedList(Message message);

    /**
     * If this villager has the token, then they will send it to another villager. The other villager is randomly
     * chosen from the set of villagers still in the simulation.
     * @throws IOException if the token cannot be sent
     */
    void sendTokenToAnotherVillager() throws IOException;
}
