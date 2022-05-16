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

import java.net.InetAddress;

/**
 * This class represents the unique address of a villager. Code that has an instance of this class is able to send the
 * villager a message, there are no other requirements for message sending.
 */
public class VillagerAddress {
    private final InetAddress _address;
    private final int _port;
    private final int _index;

    /**
     * Constructs a villager address. The 3 parameters are stored for later retrieval.
     * @param address an IP address
     * @param port an IP port
     * @param index a unique index value
     */
    public VillagerAddress(InetAddress address, int port, int index) {
        _address = address;
        _port = port;
        _index = index;
    }

    /**
     * Returns the IP address that this villager can receive message upon
     * @return an IP address
     */
    public InetAddress getAddress() {
        return _address;
    }

    /**
     * Returns the IP port that this villager can receive message upon
     * @return an IP port
     */
    public int getPort() {
        return _port;
    }

    /**
     * This value represents an easy way to index into internal data, instead of looping through an array and comparing
     * addresses
     * @return a villager's unique index value
     */
    public int getIndex() {
        return _index;
    }

    /**
     * Produces a string that describes the villager in a useful way for program output
     * @return a string that describes this villager
     */
    public String getDisplayString() {
        return "[Villager" + _index + "] ";
    }
}
