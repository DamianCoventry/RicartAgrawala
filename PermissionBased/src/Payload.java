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

public class Payload {
    public int _id;
    public int _ticket;
    public enum Command { TICKET, ACK, ENDED }
    public Command _command;

    public Payload(int id, int ticket, Command command) {
        _id = id;
        _ticket = ticket;
        _command = command;
    }

    public String toJSON() {
        Gson jsonParser = new Gson();
        return jsonParser.toJson(this);
    }

    public static Payload fromJSON(String jsonText) {
        Gson jsonParser = new Gson();
        return jsonParser.fromJson(jsonText, Payload.class);
    }
}
