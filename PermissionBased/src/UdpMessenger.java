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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * A concrete implementation of the IMessenger interface.
 *
 * This class uses UDP to transfer messages between villagers. I chose UDP for its simplicity (compared to TCP), and so
 * that I can transfer messages between processes. Other choices considered were OS shared memory, and a regular file
 * whose access is controlled by a file lock.
 *
 * Due to UDP using datagrams there is no need to 'connect' villagers together. This simplifies things greatly. UDP
 * basically 'throws' packets at an IP address, regardless of the state of the IP connection between the source and the
 * destination, and regardless of the state of the software that's bound to that IP address.
 */
public class UdpMessenger implements IMessenger {
    private static final int NUM_RECEIVE_BUFFER_BYTES = 128;    // should be plenty
    private final DatagramSocket _socket;

    /**
     * Creates a UDP socket using the given address and port. After this call, the villager is 'bound' to this address.
     * Any attempt by other software to reuse the port will result in a 'port in use' error for them. This port is
     * released when the software ends, never before then.
     *
     * I deliberately left the choice of IP address and port up to the user so that any IP address on the local
     * machine could be used. It doesn't have to be the localhost.
     *
     * @param localAddress an address on the local machine to bind to
     * @param port a port on the local machine to bind to
     * @throws SocketException if a UDP socket cannot be created and bound to the IP address and port
     */
    public UdpMessenger(InetAddress localAddress, int port) throws SocketException {
        _socket = new DatagramSocket(port, localAddress);
    }

    /**
     * Copies bytes from the message to the UDP socket. The destination the bytes are sent to is contained within
     * the message.
     * @param message contains the destination address and the data to send
     * @throws IOException if the UDP socket suffers a communication issue
     */
    @Override
    public void send(Message message) throws IOException {
        byte[] bytes = message.getPayloadBytes();
        _socket.send(new DatagramPacket(bytes, bytes.length, message.getAddress(), message.getPort()));
    }

    /**
     * Copies bytes from the UDP socket to a new message object. The sender's address is also copied into the message.
     * @return a message object containing the received bytes and the sender's address
     * @throws IOException if the UDP socket suffers a communication issue
     */
    @Override
    public Message receive() throws IOException {
        // the packets I'm sending are small. 128 bytes will be more than enough.
        byte[] bytes = new byte[NUM_RECEIVE_BUFFER_BYTES];

        DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length);
        _socket.receive(datagramPacket);

        return Message.fromDatagramPacket(datagramPacket);
    }

    /**
     * The address the villager is bound to.
     * @return an address on the local machine
     */
    @Override
    public InetAddress getMyAddress() {
        return _socket.getLocalAddress();
    }

    /**
     * Returns a value unique to this villager. This value can be used to break ties when different villagers happen
     * to generate the same random number. I chose to use the port this villager is bound to.
     * @return a value unique to this villager
     */
    @Override
    public int getTieBreakerValue() {
        return _socket.getLocalPort();
    }
}
