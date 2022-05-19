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

        if (id == 0) {
            _token = MAGICAL_TOKEN_VALUE;
            _villagerRequestList[id] = 1;
            System.out.println(_myId.getDisplayString() + "has the token.");
        }

        setName("Villager" + id);

        _receiver = new Receiver(_messenger, this);
        _receiver.start();
    }

    @Override
    public void run() {
        try {
            while (hasNotFinishedShopping()) {
                if (!hasToken()) {
                    incrementMyRequestCount();
                    requestTheTokenFromOtherVillagers();
                    waitUntilGrantedTheToken(); // implements the Monitor pattern inside
                }

                try (MiniMartAccess ignored = new MiniMartAccess(this)) {
                    // _requestingMiniMartAccess is true at this point
                    incrementGrantedCount();
                    enterMiniMart();
                    incrementShoppingCount(); // causes hasNotFinishedShopping() to return false eventually
                }
                // _requestingMiniMartAccess is strongly guaranteed to be false at this point

                sendTokenToAnotherVillager();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                _receiver.shutdown();
            } catch (IOException ignore) {}
            _done.countDown();
        }
    }

    @Override
    public synchronized VillagerAddress getMyId() {
        return _myId;
    }

    @Override
    public synchronized boolean hasToken() {
        // Objects.equals() checks for null before calling .equals()
        return Objects.equals(getToken(), MAGICAL_TOKEN_VALUE);
    }

    @Override
    public synchronized String getToken() {
        return _token;
    }

    private synchronized void relinquishToken() {
        _token = null;
    }

    /**
     * Updates internal storage to indicate that a villager has finished shopping. This method also nudges the monitor
     * within the waitForOtherVillagersToFinishShopping() method.
     *
     * Only called by the Receiver thread, but the Villager thread reads the values of _villagerHasFinishedShopping,
     * hence this method is synchronised.
     */
    @Override
    public synchronized void recordFinishedShopping(Message message) {
        if (message.getVillagerIndex() >= 0 && message.getVillagerIndex() < _totalVillagers) {
            _villagerHasFinishedShopping[message.getVillagerIndex()] = true;
            notifyAll();        // Unblock waiting threads
        }
    }

    @Override
    public synchronized void recordRequestForToken(Message message) {
        int i = message.getVillagerIndex();
        if (i >= 0 && i < _totalVillagers) {
            _villagerRequestList[i] = Math.max(_villagerRequestList[i], message.getRequestCount());
        }
    }

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

    @Override
    public synchronized void sendTokenToAnotherVillager() throws IOException {
        int i = randomlyChooseAnotherVillager();
        if (i >= 0) {
            VillagerAddress to = new VillagerAddress(_messenger.getMyAddress(), _portStart + i, i);

            System.out.println(_myId.getDisplayString() + "sending the token to " + to.getDisplayString());
            sendMessageToVillager(to, Payload.makeTokenAndGrantedList(this, _villagerGrantedList));

            relinquishToken();
        }
    }

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

    private int randomlyChooseAnotherVillager() {
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
     */
    private void enterMiniMart() throws InterruptedException {
        System.out.println(_myId.getDisplayString() + "entered the Mini Mart.");

        int count = MIN_SHOPPING_MSGS + _random.nextInt(MAX_SHOPPING_MSGS - MIN_SHOPPING_MSGS);
        for (int i = 0; i < count; ++i) {
            System.out.println(_myId.getDisplayString() + "is shopping...");
            Thread.sleep(MIN_SHOPPING_TIME + _random.nextInt(MAX_SHOPPING_TIME - MIN_SHOPPING_TIME));
        }
    }

    private boolean hasNotFinishedShopping() {
        return _numTimesShopped < MAX_NUM_TIMES_SHOPPED;
    }

    /**
     * Bumps the count that indicates how many times this villager has shopped. Ultimately this method is the way in
     * which this application ends.
     *
     * Only called by the above core loop, but the Receiver thread reads the value of _numTimesShopped via the
     * hasNotFinishedShopping() method, hence this method is synchronised.
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

    private synchronized void incrementMyRequestCount() {
        ++_villagerRequestList[_myId.getIndex()];
    }

    private synchronized void incrementGrantedCount() {
        _villagerGrantedList[_myId.getIndex()] = _villagerRequestList[_myId.getIndex()];
    }

    private synchronized void waitUntilGrantedTheToken() {
        // Monitor the _token variable
        while (!hasToken()) {
            try {
                wait();
            }
            catch (InterruptedException ignored) { }
        }
    }

    private void requestTheTokenFromOtherVillagers() throws IOException {
        sendMessageToOtherVillagers(Payload.makeRequestForToken(this, _villagerRequestList[_myId.getIndex()]));
    }

    /**
     * Informs all other villagers that this villager is finished shopping. This means that this villager has shopped 3
     * times, and therefore has ended its core loop. It also means this villager will no longer request mini mart
     * access.
     */
    private void tellOtherVillagersIveFinishedShopping() throws IOException {
        sendMessageToOtherVillagers(Payload.makeFinishedShopping(this));
    }

    /**
     * Uses the messenger object to physically put bytes on the wire for another villager to read. All socket errors are
     * swallowed, but printed to the console window.
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
