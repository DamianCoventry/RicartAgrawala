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

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * This class represents a message received from another villager, or it represents a message we want to send to another
 * villager.
 *
 * A message object is simply an address and a payload. The payload is utf8 text formatted as JSON. The address is an
 * IP address and a port.
 */
public class Message {
    /**
     * Builds a message from a villager address and a payload.
     * @param to the address of a villager to send this message to
     * @param payload the data to pack into the payload
     * @return a new message object
     */
    public static Message makeMessage(VillagerAddress to, Payload payload) {
        return new Message(to.getAddress(), to.getPort(), payload);
    }

    /**
     * Builds a message that's intended to be a reply to another message.
     * @param replyToMe the message to reply to
     * @param payloadData the villager data to pack into the payload
     * @return a new message object
     */
    public static Message makeReplyMessage(Message replyToMe, IVillager payloadData) {
        // a reply message is always a command type of ACKNOWLEDGEMENT
        return new Message(replyToMe.getAddress(), replyToMe.getPort(), Payload.makeAcknowledgement(payloadData));
    }

    /**
     * Builds a message from a datagram packet received via the underlying IP transport
     * @param datagramPacket a received packet of data
     * @return a new message object
     */
    public static Message fromDatagramPacket(DatagramPacket datagramPacket) {
        // the datagram packet will have a large buffer of bytes, copy only the bytes we need
        byte[] receivedBytes = Arrays.copyOfRange(datagramPacket.getData(), 0, datagramPacket.getLength());

        // JSON must be utf8 text, therefore convert the bytes to utf8 text, then build a payload from it
        Payload payload = Payload.fromJSON(new String(receivedBytes, StandardCharsets.UTF_8));

        return new Message(datagramPacket.getAddress(), datagramPacket.getPort(), payload);
    }

    private final InetAddress _address;
    private final int _port;
    private final Payload _payload;

    /**
     * This is private to force usage of the above public static methods. Their names dictate my intentions, a
     * constructor does not.
     * @param address an IP address
     * @param port an IP port
     * @param payload the payload data
     */
    private Message(InetAddress address, int port, Payload payload) {
        _address = address;
        _port = port;
        _payload = payload;
    }

    /**
     * The address of either the villager that sent this message, or the villager we intend to send this message to
     * @return an IP address
     */
    public InetAddress getAddress() {
        return _address;
    }

    /**
     * The port of either the villager that sent this message, or the villager we intend to send this message to
     * @return a port number
     */
    public int getPort() {
        return _port;
    }

    /**
     * Converts the payload to a form suitable for sending over a UDP socket
     * @return an array of bytes representing the message payload
     */
    public byte[] getPayloadBytes() {
        return _payload.toJSON().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Gets the unique index value for the villager whose data are in the payload. This value represents an easy way
     * to index into internal data, instead of looping through an array and comparing addresses.
     * @return a villager's unique index value
     */
    public int getVillagerIndex() {
        return _payload._villagerIndex;
    }

    /**
     * Returns the larger value of two tickets, either the passed in ticket, or the ticket within the payload data
     * @param ticket a ticket to compare with the ticket within the payload data
     * @return the larger value of two tickets
     */
    public int getLargerTicket(int ticket) {
        return Math.max(ticket, _payload._ticket);
    }

    /**
     * Builds a villager address from the data within this message. The built address is suitable to use to reply to
     * this message.
     * @return a new villager address object
     */
    public VillagerAddress makeReplyToAddress() {
        return new VillagerAddress(_address, _port, _payload._villagerIndex);
    }

    /**
     * Determines whether the passed in ticket's value is fewer than the value of the ticket within the payload data.
     * For the case where they're the same value, the tieBreakerValue is compared against this message's port number
     * because we know that value will be unique amongst the villagers in this simulation.
     * @param ticket a ticket to compare with the ticket within the payload data
     * @param tieBreakerValue a value to compare with the port value of this message
     * @return true if this message has the smaller value, false otherwise
     */
    public boolean isFewerThan(int ticket, int tieBreakerValue) {
        return _payload._ticket < ticket || (_payload._ticket == ticket && _port < tieBreakerValue);
    }

    /**
     * Determines if this message is acknowledging a previously sent message
     * @return true if this message is an acknowledgement, false otherwise
     */
    public boolean isAcknowledgement() {
        return _payload._type == Payload.Type.ACKNOWLEDGEMENT;
    }

    /**
     * Determines if this message is communicating a villager's ticket number
     * @return true if this message is communicating a villager's ticket number, false otherwise
     */
    public boolean isTicketNumber() {
        return _payload._type == Payload.Type.TICKET_NUMBER;
    }

    /**
     * Determines if this message is communicating that a villager has finished shopping
     * @return true if this message is communicating that a villager has finished shopping, false otherwise
     */
    public boolean isFinishedShopping() {
        return _payload._type == Payload.Type.FINISHED_SHOPPING;
    }
}
