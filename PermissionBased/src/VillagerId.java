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

public class VillagerId {
    private final InetAddress _address;
    private final int _port;
    private final int _id;

    public VillagerId(InetAddress address, int port, int id) {
        _address = address;
        _port = port;
        _id = id;
    }

    public InetAddress getAddress() {
        return _address;
    }

    public int getPort() {
        return _port;
    }

    public int getId() {
        return _id;
    }

    public String getDisplayString() {
        return "[Villager" + _id + "] ";
    }
}
