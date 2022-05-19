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
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * This class provides the ability for a villager to enter the mini mart mutually exclusively.
 *
 * Roughly half of the Ricart-Agrawala algorithm is implemented here. The other half is within the Receiver class.
 *
 * The logic within this class runs in its own thread so that this node can run multiple villagers concurrently. From a
 * villager's point of view, it doesn't matter where it's run, nor where the other villagers are run. Each villager can
 * be reached via an IP address and port, that's the important part.
 *
 * The thread can end as soon as it's finished shopping 3 times. As long as it notifies all other villagers that it's
 * shutting down there will be no side effects to the RA algorithm.
 */
public class Villager extends Thread implements IVillager, IRequestsMiniMartAccess {
    public static final int NUM_VILLAGERS_PER_NODE = 5;
    public static final int MAX_NUM_TIMES_SHOPPED = 3;
    private static final int MIN_SHOPPING_TIME = 1000; // just to keep it interesting
    private static final int MAX_SHOPPING_TIME = 2750;
    private static final int MIN_SHOPPING_MSGS = 2; // just to keep it interesting
    private static final int MAX_SHOPPING_MSGS = 5;
    private static final String MAGICAL_TOKEN_VALUE = "159.355 Concurrent Systems";

    private final CountDownLatch _done;
    private final IMessenger _messenger;
    private final int _portStart;
    private final int _totalVillagers;
    private final VillagerAddress _myId;
    private final Random _random;
    private final int[] _villagerRequestList;
    private final boolean[] _villagerHasFinishedShopping;
    private int[] _villagerGrantedList;

    private int _numTimesShopped;
    private boolean _requestingMiniMartAccess; // essentially it means 'are we in the critical section?'
    private String _token;
    private final Receiver _receiver;

    /**
     * Constructs an instance of a villager. Villager objects within a node don't share any data via memory. They're
     * intentionally self-contained.
     * @param done an object to signal when this villager is finished
     * @param ipAddress an address on the local machine to bind to
     * @param portStart the first value in a contiguous range of port values
     * @param totalVillagers how many villagers are part of the simulation
     * @param id the unique index of this villager
     * @throws IOException if the passed in IP address is unable to be bound to
     */
    public Villager(CountDownLatch done, String ipAddress, int portStart, int totalVillagers, int id) throws IOException {
        _done = done;
        _portStart = portStart;
        _random = new Random();
        _numTimesShopped = 0;
        _totalVillagers = totalVillagers;
        _requestingMiniMartAccess = false;

        _villagerRequestList = new int[totalVillagers];
        _villagerGrantedList = new int[totalVillagers];
        _villagerHasFinishedShopping = new boolean[totalVillagers];
        Arrays.fill(_villagerRequestList, 0);
        Arrays.fill(_villagerGrantedList, 0);
        Arrays.fill(_villagerHasFinishedShopping, false);

        _myId = new VillagerAddress(InetAddress.getByName(ipAddress), portStart + id, id);
        _messenger = new UdpMessenger(InetAddress.getByName(ipAddress), portStart + id);

        // I chose to make villager 0 possess the token first
        if (id == 0) {
            _token = MAGICAL_TOKEN_VALUE;
            _villagerRequestList[id] = 1;
            System.out.println(_myId.getDisplayString() + "has the token.");
        }

        setName("Villager" + id);

        _receiver = new Receiver(_messenger, this);
        _receiver.start();
    }

    /**
     * This method is the core loop of the Villager. It only loops 3 times then ends. Each pass through the loop
     * represents the villager shopping one time. After the loop is ended, this thread can end straight away because the
     * incrementShoppingCount() method will have told all other villagers that this villager has finished. This will
     * prevent those villagers from sending the token to a villager that has ended.
     *
     * The loop uses the MiniMartAccess class within a try () {} statement to indicate, and control, the entering and
     * exiting of the critical section of the Ricart-Agrawala algorithm. Structuring the code this way provides a strong
     * guarantee that the JVM will call the Villager class's stopRequestingMiniMartAccess() implementation, therefore
     * assisting in a correct implementation of the RA algorithm.
     */
    @Override
    public void run() {
        try {
            // the core loop. this only loops thrice.
            while (hasNotFinishedShopping()) {
                // if this villager doesn't have the token then they must ask the other villagers for it. Then this
                // villager must sit and wait for one of them to send the token here.
                if (!hasToken()) {
                    incrementMyRequestCount();
                    requestTheTokenFromOtherVillagers();
                    waitUntilGrantedTheToken(); // implements the Monitor pattern inside
                }

                // this try block contains the villager's request to access the mini mart. by implementing this we
                // provide a way for the Receiver thread to know this thread is currently requesting mini mart access.
                // this is achieved by the constructor + close methods of the MiniMartAccess class calling back into the
                // Villager class, which in turn sets the value of the _requestingMiniMartAccess variable.
                try (MiniMartAccess ignored = new MiniMartAccess(this)) {
                    // _requestingMiniMartAccess is true at this point
                    updateGrantedCount();
                    enterMiniMart();
                    incrementShoppingCount(); // causes hasNotFinishedShopping() to return false eventually
                }
                // _requestingMiniMartAccess is strongly guaranteed to be false at this point

                sendTokenToAnotherVillager(); // the other villager is randomly chosen
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                // the receiver sends a message to shut down, hence the need for the try/catch
                _receiver.shutdown();
            } catch (IOException ignore) {}
            _done.countDown();
        }
    }

    /**
     * Retrieves the address of this villager.
     *
     * Used by the Payload class and Receiver class. The Payload class is used by the Receiver thread and the Villager
     * thread, hence this method is synchronised.
     * @return the address of this villager
     */
    @Override
    public synchronized VillagerAddress getMyId() {
        return _myId;
    }

    /**
     * Determines whether this villager has the token or not
     *
     * Used by both the Villager thread and the Receiver thread, hence this method is synchronised.
     * @return true if this villager has the token, false otherwise
     */
    @Override
    public synchronized boolean hasToken() {
        // Objects.equals() checks for null before calling .equals()
        return Objects.equals(getToken(), MAGICAL_TOKEN_VALUE);
    }

    /**
     * Retrieves the token if it's possessed by this villager
     *
     * Used by both the Villager thread and the Receiver thread, hence this method is synchronised.
     * @return the token as a string, or null if the token is not possessed
     */
    @Override
    public synchronized String getToken() {
        return _token;
    }

    /**
     * Updates internal storage to indicate that a villager has finished shopping.
     *
     * Only called by the Receiver thread, but the Villager thread reads the values of _villagerHasFinishedShopping,
     * hence this method is synchronised.
     * @param message a message received from another villager
     */
    @Override
    public synchronized void recordFinishedShopping(Message message) {
        if (message.getVillagerIndex() >= 0 && message.getVillagerIndex() < _totalVillagers) {
            _villagerHasFinishedShopping[message.getVillagerIndex()] = true;
            notifyAll();        // Unblock waiting threads
        }
    }

    /**
     * Updates internal storage to indicate that a villager has requested the token
     *
     * Only called by the Receiver thread, but the Villager thread reads the values of _villagerRequestList, hence this
     * method is synchronised.
     * @param message a message received from another villager
     */
    @Override
    public synchronized void recordRequestForToken(Message message) {
        int i = message.getVillagerIndex();
        if (i >= 0 && i < _totalVillagers) {
            _villagerRequestList[i] = Math.max(_villagerRequestList[i], message.getRequestCount());
        }
    }

    /**
     * Updates internal storage to indicate that a villager has received the token, and updates the granted list. This
     * method also nudges the monitor that's implemented in the waitUntilGrantedTheToken() method.
     *
     * Only called by the Receiver thread, but the Villager thread reads the values of _token and _villagerGrantedList,
     * hence this method is synchronised.
     * @param message a message received from another villager
     */
    @Override
    public synchronized void recordTokenAndGrantedList(Message message) {
        if (message.getVillagerIndex() < 0 || message.getVillagerIndex() >= _totalVillagers) {
            return;
        }
        if (Objects.equals(message.getToken(), MAGICAL_TOKEN_VALUE)) {
            _token = message.getToken();
            _villagerGrantedList = message.getGrantedList().clone(); // copy the values, not the ref

            VillagerAddress from = new VillagerAddress(_messenger.getMyAddress(),
                    _portStart + message.getVillagerIndex(), message.getVillagerIndex());
            System.out.println(_myId.getDisplayString() + "received the token from " + from.getDisplayString());

            notifyAll();        // Unblock waiting threads
        }
    }

    /**
     * Transmits the token to another villager, chosen at random, then clears this villager's knowledge of the token.
     * @throws IOException if the token cannot be sent to another villager
     */
    @Override
    public synchronized void sendTokenToAnotherVillager() throws IOException {
        if (!hasToken()) { // don't bother if we don't have the token
            return;
        }

        // if this is only villager instance left, then -1 will be returned from this method because we can't choose
        // ourselves when randomly choosing a villager. for this case we don't need to send the token anywhere, just
        // hang onto it and iterate through the main loop again.
        int i = chooseAnotherVillagerRandomly();
        if (i < 0) {
            return;
        }

        VillagerAddress to = new VillagerAddress(_messenger.getMyAddress(), _portStart + i, i);

        System.out.println(_myId.getDisplayString() + "sending the token to " + to.getDisplayString());
        sendMessageToVillager(to, Payload.makeTokenAndGrantedList(this, _villagerGrantedList));

        relinquishToken(); // clears internal state
    }

    /**
     * Determines if this villager is NOT requesting mini mart access.
     *
     * Only called by the Receiver thread, but the Villager thread writes the value of _requestingMiniMartAccess, hence
     * this method is synchronised.
     * @return true if this villager is NOT requesting mini mart access, false otherwise
     */
    @Override
    public boolean isNotRequestingMiniMartAccess() {
        return !_requestingMiniMartAccess;
    }

    /**
     * Updates internal storage to indicate that this villager has started to request mini mart access.
     *
     * Only called by the MiniMartAccess class as part of the core loop above. The Receiver thread will read the value
     * of _requestingMiniMartAccess via the call to isNotRequestingMiniMartAccess(), hence this method is synchronised.
     */
    @Override
    public synchronized void startRequestingMiniMartAccess() {
        _requestingMiniMartAccess = true;
    }

    /**
     * Updates internal storage to indicate that this villager has stopped requesting mini mart access.
     *
     * Only called by the MiniMartAccess class as part of the core loop above. The Receiver thread will read the value
     * of _requestingMiniMartAccess via the call to isNotRequestingMiniMartAccess(), hence this method is synchronised.
     */
    @Override
    public synchronized void stopRequestingMiniMartAccess() {
        _requestingMiniMartAccess = false;
        System.out.println(_myId.getDisplayString() + "exited the Mini Mart " +
                _numTimesShopped + "/" + MAX_NUM_TIMES_SHOPPED + ". Letting the next villager in.");
    }

    /**
     * Clears knowledge of the token.
     */
    private synchronized void relinquishToken() {
        _token = null;
    }

    /**
     * Chooses another villager by collecting all villagers that are requesting a token, then randomly choosing one of
     * them. This does not prevent starvation in any way, nor is it trying to. The expectation of this method is to
     * use a uniform distribution to choose a villager randomly.
     * @return the index of villager that is requesting the token
     */
    private int chooseAnotherVillagerRandomly() {
        final ArrayList<Integer> villagers = new ArrayList<>();
        for (int i = 0; i < _totalVillagers; ++i) {
            if (isVillagerRequestingToken(i)) {
                villagers.add(i);
            }
        }
        if (villagers.isEmpty()) { // will happen when this villager is the last villager
            return -1;
        }
        return villagers.get(_random.nextInt(villagers.size()));
    }

    /**
     * Determines if a villager is requesting a token. True is returned if the index is not this villager, is not for a
     * villager that has finished shopping, and is for a villager that has more requests for the token than grants for
     * the token.
     * @param i the index of a villager to test
     * @return true if the villager is requesting a token, false otherwise
     */
    private boolean isVillagerRequestingToken(int i) {
        return i != _myId.getIndex() &&                             // don't send it to myself
               !_villagerHasFinishedShopping[i] &&                  // don't bother if they're finished
               _villagerRequestList[i] > _villagerGrantedList[i];   // more requests than grants?
    }

    /**
     * This method spends some time doing nothing at all, really. It's used to indicate to the user that this villager
     * has entered the mini mart, which really means this villager has achieved mutual exclusivity.
     *
     * Only called by the core loop above, no need to protect any state with a synchronisation mechanism.
     * @throws InterruptedException if the thread is interrupted
     */
    private void enterMiniMart() throws InterruptedException {
        System.out.println(_myId.getDisplayString() + "entered the Mini Mart.");

        int count = MIN_SHOPPING_MSGS + _random.nextInt(MAX_SHOPPING_MSGS - MIN_SHOPPING_MSGS);
        for (int i = 0; i < count; ++i) {
            System.out.println(_myId.getDisplayString() + "is shopping...");
            Thread.sleep(MIN_SHOPPING_TIME + _random.nextInt(MAX_SHOPPING_TIME - MIN_SHOPPING_TIME));
        }
    }

    /**
     * Determines if this villager has NOT finished shopping.
     *
     * Only called by the Receiver thread, but the Villager thread writes the value of _numTimesShopped, hence this
     * method is synchronised.
     * @return true if this villager has finished shopping, false otherwise
     */
    private boolean hasNotFinishedShopping() {
        return _numTimesShopped < MAX_NUM_TIMES_SHOPPED;
    }

    /**
     * Bumps the count that indicates how many times this villager has shopped. Ultimately this method is the way in
     * which this application ends.
     *
     * Only called by the above core loop, but the Receiver thread reads the value of _numTimesShopped via the
     * hasNotFinishedShopping() method, hence this method is synchronised.
     * @throws IOException if the message can't be sent
     */
    private synchronized void incrementShoppingCount() throws IOException {
        if (++_numTimesShopped >= MAX_NUM_TIMES_SHOPPED) {
            System.out.println(_myId.getDisplayString() + "finished all their shopping.");
            _villagerHasFinishedShopping[_myId.getIndex()] = true;

            // we must let other villagers know that we're finished shopping. this will allow them to exit their calls
            // to the waitForOtherVillagersToFinishShopping() method and end their process.
            tellOtherVillagersIveFinishedShopping();
        }
    }

    /**
     * Increments internal state to indicate a new request for the token
     *
     * Only called above by the core loop. The Receiver thread reads the value of the _villagerRequestList array, hence
     * this method is synchronised.
     */
    private synchronized void incrementMyRequestCount() {
        ++_villagerRequestList[_myId.getIndex()];
    }

    /**
     * Updates internal state to indicate that a token has been granted
     *
     * Only called above by the core loop. The Receiver thread reads the value of the _villagerRequestList array, hence
     * this method is synchronised.
     */
    private synchronized void updateGrantedCount() {
        _villagerGrantedList[_myId.getIndex()] = _villagerRequestList[_myId.getIndex()];
    }

    /**
     * For each iteration of the core loop where the villager does not possess the token, this method is called to block
     * the Villager thread until a token is granted. This is a core part of the Ricart-Agrawala algorithm. This method
     * implements the Monitor pattern.
     *
     * The _token array is accessed by the Receiver thread, hence this method is synchronised.
     */
    private synchronized void waitUntilGrantedTheToken() {
        // Monitor the _token variable
        while (!hasToken()) {
            try {
                wait();
            }
            catch (InterruptedException ignored) { }
        }
    }

    /**
     * Informs all other villagers that this villager is requesting the token.
     * @throws IOException if the message can't be sent
     */
    private void requestTheTokenFromOtherVillagers() throws IOException {
        sendMessageToOtherVillagers(Payload.makeRequestForToken(this, _villagerRequestList[_myId.getIndex()]));
    }

    /**
     * Informs all other villagers that this villager is finished shopping. This means that this villager has shopped 3
     * times, and therefore has ended its core loop. It also means this villager will no longer request mini mart
     * access.
     * @throws IOException if the message can't be sent
     */
    private void tellOtherVillagersIveFinishedShopping() throws IOException {
        sendMessageToOtherVillagers(Payload.makeFinishedShopping(this));
    }

    /**
     * Uses the messenger object to physically put bytes on the wire for another villager to read. All socket errors are
     * swallowed, but printed to the console window.
     * @throws IOException if the message can't be sent
     */
    private void sendMessageToVillager(VillagerAddress to, Payload payload) throws IOException {
        try {
            _messenger.send(Message.makeMessage(to, payload));
        }
        catch (SocketException e) {
            System.out.println("Caught exception [" + e.getLocalizedMessage() +
                    "] when sending a message to " + to.getDisplayString());
        }
    }

    /**
     * Sends the passed in payload data to all other villagers. All socket errors are swallowed, but printed to the
     * console window.
     * @throws IOException if the message can't be sent
     */
    private void sendMessageToOtherVillagers(Payload payload) throws IOException {
        for (int i = 0; i < _totalVillagers; ++i) {
            if (i != _myId.getIndex()) { // be sure to skip ourselves when looping
                VillagerAddress to = new VillagerAddress(_messenger.getMyAddress(), _portStart + i, i);
                sendMessageToVillager(to, payload);
            }
        }
    }
}
