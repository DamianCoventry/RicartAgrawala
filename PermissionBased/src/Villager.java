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
import java.util.ArrayDeque;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class Villager extends Thread implements IVillager, IRequestsMiniMartAccess {
    public static final int NUM_VILLAGERS_PER_NODE = 5;
    public static final int MAX_NUM_TIMES_SHOPPED = 3;
    private static final int MIN_SHOPPING_TIME = 100;
    private static final int MAX_SHOPPING_TIME = 500;
    private static final int MIN_SHOPPING_MSGS = 2;
    private static final int MAX_SHOPPING_MSGS = 5;

    private final CountDownLatch _done;
    private final IMessenger _messenger;
    private final ArrayDeque<VillagerAddress> _replyList;
    private final Random _random;
    private final int _portStart;
    private final int _totalVillagers;
    private final VillagerAddress _myId;
    private final boolean[] _villagerHasReplied;
    private final boolean[] _villagerHasFinishedShopping;

    private boolean _requestingMiniMartAccess;
    private int _ticket;
    private int _largestTicket;
    private int _numTimesShopped;

    private final Receiver _receiver;

    public Villager(CountDownLatch done, String ipAddress, int portStart, int totalVillagers, int id) throws IOException {
        _done = done;
        _portStart = portStart;
        _random = new Random();

        _requestingMiniMartAccess = false;
        _replyList = new ArrayDeque<>();

        _totalVillagers = totalVillagers;
        _villagerHasReplied = new boolean[totalVillagers];
        _villagerHasFinishedShopping = new boolean[totalVillagers];
        for (int i = 0; i < totalVillagers; ++i) {
            _villagerHasFinishedShopping[i] = false;
        }

        int ticketNumber = _random.nextInt(4 * totalVillagers);  // the x4 will help reduce clashes
        _largestTicket = _ticket = ticketNumber;
        _numTimesShopped = 0;

        _myId = new VillagerAddress(InetAddress.getByName(ipAddress), portStart + id, id);
        _messenger = new UdpMessenger(InetAddress.getByName(ipAddress), portStart + id);

        _receiver = new Receiver(_messenger, this);
        _receiver.start();
    }

    @Override
    public void run() {
        try {
            while (hasNotFinishedShopping()) {
                try (MiniMartAccess ignored = new MiniMartAccess(this)) {
                    takeTheNextTicket();
                    clearOtherVillagersReplies();
                    tellOtherVillagersMyTicket();
                    waitForOtherVillagersToReply();
                    enterMiniMart();
                    incrementShoppingCount();
                }

                tellOtherVillagersIveExitedTheMiniMart();
            }

            waitForOtherVillagersToFinishShopping();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            _receiver.shutdown();
            _done.countDown();
        }
    }

    @Override
    public synchronized void updateLargestTicket(Message message) {
        _largestTicket = message.getLargerTicket(_largestTicket);
    }

    @Override
    public synchronized VillagerAddress getMyId() {
        return _myId;
    }

    @Override
    public synchronized int getTicket() {
        return _ticket;
    }

    @Override
    public void recordVillagersAddress(VillagerAddress villagerAddress) {
        _replyList.push(villagerAddress);
    }

    @Override
    public synchronized boolean doesVillagerShopBeforeMe(Message message) {
        return message.isFewerThan(_ticket, _messenger.getTieBreakerValue());
    }

    @Override
    public synchronized void recordAcknowledgement(Message message) {
        if (message.getVillagerIndex() >= 0 && message.getVillagerIndex() < _totalVillagers) {
            _villagerHasReplied[message.getVillagerIndex()] = true;
            notifyAll();        // Unblock waiting threads
        }
    }

    @Override
    public synchronized void recordFinishedShopping(Message message) {
        if (message.getVillagerIndex() >= 0 && message.getVillagerIndex() < _totalVillagers) {
            _villagerHasFinishedShopping[message.getVillagerIndex()] = true;
            notifyAll();        // Unblock waiting threads
        }
    }

    @Override
    public synchronized boolean hasNotFinishedShopping() {
        return _numTimesShopped < MAX_NUM_TIMES_SHOPPED;
    }

    @Override
    public synchronized boolean isRequestingMiniMartAccess() {
        return _requestingMiniMartAccess;
    }

    @Override
    public synchronized void startRequestingMiniMartAccess() {
        _requestingMiniMartAccess = true;
    }

    @Override
    public synchronized void stopRequestingMiniMartAccess() {
        _requestingMiniMartAccess = false;
    }

    private void enterMiniMart() throws InterruptedException {
        System.out.println(_myId.getDisplayString() + "entered the Mini Mart.");

        int count = MIN_SHOPPING_MSGS + _random.nextInt(MAX_SHOPPING_MSGS - MIN_SHOPPING_MSGS);
        for (int i = 0; i < count; ++i) {
            System.out.println(_myId.getDisplayString() + "is shopping...");
            Thread.sleep(MIN_SHOPPING_TIME + _random.nextInt(MAX_SHOPPING_TIME - MIN_SHOPPING_TIME));
        }
    }

    private synchronized void incrementShoppingCount() throws IOException {
        if (++_numTimesShopped >= MAX_NUM_TIMES_SHOPPED) {
            System.out.println(_myId.getDisplayString() + "finished all their shopping.");
            _villagerHasFinishedShopping[_myId.getIndex()] = true;

            tellOtherVillagersIveFinishedShopping();
        }
    }

    private synchronized void takeTheNextTicket() {
        _ticket = _largestTicket + 1;
    }

    private synchronized void clearOtherVillagersReplies() {
        for (int i = 0; i < _totalVillagers; ++i) {
            _villagerHasReplied[i] = false;
        }
    }

    private synchronized void waitForOtherVillagersToReply() {
        // Monitor the _villagerHasReplied array
        while (haveOtherVillagersNotReplied()) {
            try {
                wait();
            }
            catch (InterruptedException ignored) { }
        }
    }

    private synchronized void waitForOtherVillagersToFinishShopping() {
        System.out.println(_myId.getDisplayString() +
                "waiting for other villagers to finish shopping (they need me to reply)");

        // Monitor the _villagerHasFinishedShopping array
        while (haveOtherVillagersNotFinishedShopping()) {
            try {
                wait();
            }
            catch (InterruptedException ignored) { }
        }
    }

    private synchronized boolean haveOtherVillagersNotReplied() {
        for (int i = 0; i < _totalVillagers; ++i) {
            if (i != _myId.getIndex() && !_villagerHasReplied[i]) {
                return true;
            }
        }
        return false;
    }

    private synchronized boolean haveOtherVillagersNotFinishedShopping() {
        for (int i = 0; i < _totalVillagers; ++i) {
            if (!_villagerHasFinishedShopping[i]) {
                return true;
            }
        }
        return false;
    }

    private synchronized void tellOtherVillagersIveExitedTheMiniMart() throws IOException {
        System.out.println(_myId.getDisplayString() + "exited the Mini Mart " +
                _numTimesShopped + "/" + MAX_NUM_TIMES_SHOPPED + ". Letting the next villager in.");

        Payload payload = Payload.makeAcknowledgement(this);
        while (!_replyList.isEmpty()) {
            sendMessageToVillager(_replyList.pop(), payload);
        }
    }

    private void tellOtherVillagersMyTicket() throws IOException {
        sendMessageToOtherVillagers(Payload.makeTicketNumber(this));
    }

    private void tellOtherVillagersIveFinishedShopping() throws IOException {
        sendMessageToOtherVillagers(Payload.makeFinishedShopping(this));
    }

    private void sendMessageToVillager(VillagerAddress to, Payload payload) throws IOException {
        try {
            _messenger.send(Message.makeMessage(to, payload));
        }
        catch (SocketException e) {
            System.out.println("Caught exception [" + e.getLocalizedMessage() +
                    "] when sending a message to " + to.getDisplayString());
        }
    }

    private void sendMessageToOtherVillagers(Payload payload) throws IOException {
        for (int i = 0; i < _totalVillagers; ++i) {
            if (i != _myId.getIndex()) {
                VillagerAddress to = new VillagerAddress(_messenger.getMyAddress(), _portStart + i, i);
                sendMessageToVillager(to, payload);
            }
        }
    }
}
