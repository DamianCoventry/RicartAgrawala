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

import com.google.gson.Gson;

/**
 * This class represents the data that are sent from villager to villager within a message. So that parsing of the data
 * is unambiguous, I'm formatting the data as JSON.
 *
 * To give the data some context, the following types were created:
 *      TICKET_NUMBER
 *          The sender is informing you of their ticket number. You should update your internal 'largest ticket' value
 *          if this value is larger than yours. You should also compare your ticket number to this one and let the
 *          sender know if they should enter the mini mart before you.
 *
 *      ACKNOWLEDGEMENT
 *          The sender is acknowledging that they received your message. You should record the fact this villager has
 *          replied to you. If it's the last villager to reply then you can enter the mini mart, otherwise you need to
 *          keep waiting.
 *
 *      FINISHED_SHOPPING
 *          Each villager shops 3 times. Receipt of this message means the sender has completed all 3 shops. You should
 *          record the fact that this villager is finished. If it's the last villager to send you this message type then
 *          you can end this process, otherwise you need to keep waiting.
 */
public class Payload {
    // These are public because of a Gson requirement
    public int _villagerIndex;
    public int _ticket;
    public enum Type { TICKET_NUMBER, ACKNOWLEDGEMENT, FINISHED_SHOPPING }
    public Type _type;

    /**
     * Builds a payload that informs the receiver of our ticket number
     * @param sender the villager whose details are packed into the payload
     * @return a new payload object
     */
    public static Payload makeTicketNumber(IVillager sender) {
        return new Payload(sender.getMyId().getIndex(), sender.getTicket(), Type.TICKET_NUMBER);
    }

    /**
     * Builds a payload that informs the receiver that we received their message
     * @param sender the villager whose details are packed into the payload
     * @return a new payload object
     */
    public static Payload makeAcknowledgement(IVillager sender) {
        return new Payload(sender.getMyId().getIndex(), sender.getTicket(), Type.ACKNOWLEDGEMENT);
    }

    /**
     * Builds a payload that informs the receiver that we've finished our 3 shops
     * @param sender the villager whose details are packed into the payload
     * @return a new payload object
     */
    public static Payload makeFinishedShopping(IVillager sender) {
        return new Payload(sender.getMyId().getIndex(), sender.getTicket(), Type.FINISHED_SHOPPING);
    }

    /**
     * This is private to force usage of the above public static methods. Their names dictate my intentions, a
     * constructor does not.
     * @param villagerIndex the index of the villager
     * @param ticket the villager's ticket
     * @param type the type of payload to construct
     */
    private Payload(int villagerIndex, int ticket, Type type) {
        _villagerIndex = villagerIndex;
        _ticket = ticket;
        _type = type;
    }

    /**
     * Converts the payload to minified JSON
     * @return a minified JSON string representing the payload
     */
    public String toJSON() {
        Gson jsonParser = new Gson();
        return jsonParser.toJson(this);
    }

    /**
     * Builds a payload object from text formatted as JSON
     * @param jsonText the text to parse as JSON
     * @return a new payload object
     */
    public static Payload fromJSON(String jsonText) {
        Gson jsonParser = new Gson();
        return jsonParser.fromJson(jsonText, Payload.class);
    }
}
