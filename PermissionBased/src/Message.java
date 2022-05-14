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

public class Message {
    private final InetAddress _address;
    private final int _port;
    private final Payload _payload;

    public Message(InetAddress fromAddress, int port, Payload payload) {
        _address = fromAddress;
        _port = port;
        _payload = payload;
    }

    public InetAddress getAddress() {
        return _address;
    }
    
    public int getPort() {
        return _port;
    }

    public Payload getPayload() {
        return _payload;
    }

    public VillagerId replyTo() {
        return new VillagerId(_address, _port, _payload._id);
    }
}
