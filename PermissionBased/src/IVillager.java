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

/**
 * This interface represents the contract between a villager's receiving thread and another object that stores the state
 * required by a villager who's implementing the Ricart-Agrawala algorithm. I've chosen that object to be the main
 * Villager object, hence the Villager class implements this interface.
 *
 * Another class that cares about the IVillager interface is the Receiver class. It uses a reference to this interface
 * when it receives a message. The problem this interface solves is that the Receiver does not own the state required to
 * drive the algorithm, instead, it hands off to another object that does own the state. This interface provides that
 * hand off mechanism. That other object is surely modifying the state in another thread, hence implementation of the
 * methods below must be synchronised.
 */
public interface IVillager {
    /**
     * This villager's address. This is solely used by the Payload class to insert this villager's index into outgoing
     * messages. Other villagers need this number when recording received state via a message.
     * @return this villager's address
     */
    VillagerAddress getMyId();

    /**
     * This villager's current ticket number. This is solely used by the Payload class to insert this villager's ticket
     * number into outgoing messages. Other villagers need this number when calling updateLargestTicket().
     * @return this villager's current ticket number
     */
    int getTicket();

    /**
     * This method is how villagers keep on choosing unique ticket numbers. When we receive a message from another
     * villager we take note of the ticket number they're using. We subsequently increment this number when we send our
     * next message.
     * @param message a message received from another villager
     */
    void updateLargestTicket(Message message);

    /**
     * We need to record the address of other villagers that will enter the mini mart AFTER us. We will send a message
     * to these villagers when we exit the mini mart.
     * @param villagerAddress the address of another villager
     */
    void recordVillagersAddress(VillagerAddress villagerAddress);

    /**
     * We need to record the fact that another villager has acknowledged receipt of our message. This affects when we
     * enter the mini mart.
     * @param message a message received from another villager
     */
    void recordAcknowledgement(Message message);

    /**
     * We need to record the fact that another villager has finished shopping. This affects when this node shuts down.
     * @param message a message received from another villager
     */
    void recordFinishedShopping(Message message);

    /**
     * Returns whether the villager is currently requesting mini mart access. This is a core part of the Ricart-Agrawala
     * algorithm.
     * @see IRequestsMiniMartAccess
     * @see MiniMartAccess
     * @return whether the villager is currently requesting mini mart access
     */
    boolean isRequestingMiniMartAccess();

    /**
     * Returns whether the message represents a villager that must shop before this villager. This is achieved by
     * comparing ticket numbers.
     * @return whether the message represents a villager that must shop before this villager
     */
    boolean doesVillagerShopBeforeMe(Message message);

    /**
     * Returns whether the villager has finished shopping. A villager will shop 3 times, therefore, false will be
     * returned until this count is reached.
     * @return whether the villager has finished shopping
     */
    boolean hasNotFinishedShopping();
}
