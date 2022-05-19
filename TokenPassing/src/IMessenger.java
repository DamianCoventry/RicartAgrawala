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
import java.net.InetAddress;

/**
 * This interface represents the message passing contract between the villagers. It enables them to communicate
 * without knowing HOW the messages actually get to their destination.
 *
 * Before any message can be sent, we need to know WHERE to send it. This is why I've exposed the getMyAddress
 * method -- it's used to construct other villager addresses from this villager's address. See ReadMe.md for more info.
 */
public interface IMessenger {
    /**
     * Sends the message to its destination. The payload within the message is the data that are transferred to a
     * villager. The address contained within the message is the destination. Blocks until all bytes are sent.
     * @param message contains the destination address and the data to send
     * @throws IOException if the underlying IP transport suffers a communication issue
     */
    void send(Message message) throws IOException;

    /**
     * Checks to see if there's a message waiting to be read. If there is then the bytes are read and copied into the
     * returned message object. If there isn't then the calling thread is blocked until a message arrives.
     * @return a message object containing the read bytes, and the address of the sender
     * @throws IOException if the underlying IP transport suffers a communication issue
     */
    Message receive() throws IOException;

    /**
     * Returns the address of this villager. This method is only here so that a villager can construct the address of
     * all other villagers. This is because all villagers share the same IP address, but differ by their port. See
     * ReadMe.md for more info.
     * @return this villager's address
     */
    InetAddress getMyAddress();
}
