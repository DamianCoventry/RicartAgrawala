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
 *      TOKEN_REQUEST
 *          The sender is requesting possession of the token. You should record this fact so that when you receive the
 *          token, then shopp at the mini mart, you can choose one of the villagers requesting the token to send the
 *          token to.
 *
 *      TOKEN
 *          The sender has sent you the token. Good for you, go tell your mum. You may enter the mini mart and shop for
 *          goods. Once you're finished shopping, you can choose one of the villagers requesting the token and send it
 *          to them.
 *
 *      FINISHED_SHOPPING
 *          Each villager shops 3 times. Receipt of this message means the sender has completed all 3 shops. You should
 *          record the fact that this villager is finished. Never send the token to a villager that has finished
 *          shopping, they won't send it on to someone else.
 */
public class Payload {
    // These are public because of a Gson requirement
    public int _villagerIndex;
    public String _token;
    public int[] _grantedList;
    public int _requestCount;
    public enum Type { TOKEN_REQUEST, TOKEN, FINISHED_SHOPPING }
    public Type _type;

    /**
     * Builds a payload that informs the receiver of a request for the token
     * @param sender the villager whose details are packed into the payload
     * @param requestCount the count to write into the payload
     * @return a new payload object
     */
    public static Payload makeRequestForToken(IVillager sender, int requestCount) {
        return new Payload(sender.getMyId().getIndex(), requestCount, Type.TOKEN_REQUEST);
    }

    /**
     * Builds a payload that transmits the token to another villager
     * @param sender the villager whose details are packed into the payload
     * @param grantedList the list of grants to write into the payload
     * @return a new payload object
     */
    public static Payload makeTokenAndGrantedList(IVillager sender, int[] grantedList) {
        return new Payload(sender.getMyId().getIndex(), sender.getToken(), grantedList, Type.TOKEN);
    }

    /**
     * Builds a payload that informs the receiver that we've finished our 3 shops
     * @param sender the villager whose details are packed into the payload
     * @return a new payload object
     */
    public static Payload makeFinishedShopping(IVillager sender) {
        return new Payload(sender.getMyId().getIndex(), Type.FINISHED_SHOPPING);
    }

    /**
     * These are private to force usage of the above public static methods. Their names dictate my intentions, a
     * constructor does not.
     * @param villagerIndex the index of the villager
     * @param type the type of payload to construct
     */
    private Payload(int villagerIndex, Type type) {
        _villagerIndex = villagerIndex;
        _requestCount = 0;
        _type = type;
    }
    private Payload(int villagerIndex, int requestCount, Type type) {
        _villagerIndex = villagerIndex;
        _requestCount = requestCount;
        _type = type;
    }
    private Payload(int villagerIndex, String token, int[] grantedList, Type type) {
        _villagerIndex = villagerIndex;
        _token = token;
        _grantedList = grantedList.clone(); // copy the values, not the ref
        _requestCount = 0;
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
