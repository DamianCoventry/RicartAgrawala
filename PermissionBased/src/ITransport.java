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
import java.net.UnknownHostException;

public interface ITransport {
    String getAddressString();
    InetAddress getAddress() throws UnknownHostException;
    int getPort();
    void send(Message message) throws IOException;
    Message receive() throws IOException;
}
