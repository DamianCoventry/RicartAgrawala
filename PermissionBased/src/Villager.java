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

public class Villager extends Thread implements IState, IGroceryShopping {
    public static final int NUM_VILLAGERS_PER_NODE = 5;
    public static final int MAX_NUM_TIMES_SHOPPED = 3;
    private static final int MIN_SHOPPING_TIME = 100;
    private static final int MAX_SHOPPING_TIME = 500;
    private static final int MIN_SHOPPING_MSGS = 2;
    private static final int MAX_SHOPPING_MSGS = 5;

    private final CountDownLatch _done;
    private final ITransport _transport;
    private final ArrayDeque<VillagerId> _replyList;
    private final Random _random;
    private final int _portStart;
    private final int _totalVillagers;
    private final VillagerId _villagerId;
    private final boolean[] _hasReplied;
    private final boolean[] _hasEnded;

    private boolean _insideMiniMart;
    private int _ticket;
    private int _largestTicket;
    private int _numTimesShopped;

    private final Receiver _receiver;

    public Villager(CountDownLatch done, String ipAddress, int portStart, int totalVillagers, int id) throws IOException {
        _done = done;
        _portStart = portStart;
        _random = new Random();

        _insideMiniMart = false;
        _replyList = new ArrayDeque<>();

        _totalVillagers = totalVillagers;
        _hasReplied = new boolean[totalVillagers];
        _hasEnded = new boolean[totalVillagers];
        for (int i = 0; i < totalVillagers; ++i) {
            _hasEnded[i] = false;
        }

        int ticketNumber = _random.nextInt(4 * totalVillagers);
        _largestTicket = _ticket = ticketNumber;
        _numTimesShopped = 0;

        _villagerId = new VillagerId(InetAddress.getByName(ipAddress), portStart + id, id);
        _transport = new UdpTransport(InetAddress.getByName(ipAddress), portStart + id);

        _receiver = new Receiver(_transport, this, _villagerId);
        _receiver.start();
    }

    @Override
    public void run() {
        try {
            while (haveNotFinishedShopping()) {
                try (MiniMart ignored = new MiniMart(this)) {
                    takeTicket();
                    clearReplies();
                    broadcastMessage(Payload.Command.TICKET);
                    waitForReplies();
                    shop();
                    incShoppingCount();
                }
                sendReplies();
            }

            waitForAllEnded();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            _receiver.shutdown();
            _done.countDown();
        }
    }

    private void shop() throws InterruptedException {
        System.out.println(_villagerId.getDisplayString() + "entered the Mini Mart.");

        int count = MIN_SHOPPING_MSGS + _random.nextInt(MAX_SHOPPING_MSGS - MIN_SHOPPING_MSGS);
        for (int i = 0; i < count; ++i) {
            System.out.println(_villagerId.getDisplayString() + "is shopping...");
            Thread.sleep(MIN_SHOPPING_TIME + _random.nextInt(MAX_SHOPPING_TIME - MIN_SHOPPING_TIME));
        }
    }

    private synchronized void incShoppingCount() throws IOException {
        if (++_numTimesShopped >= MAX_NUM_TIMES_SHOPPED) {
            System.out.println(_villagerId.getDisplayString() + "finished shopping.");
            _hasEnded[_villagerId.getId()] = true;
            broadcastMessage(Payload.Command.ENDED);
        }
    }

    @Override
    public synchronized boolean haveNotFinishedShopping() {
        return _numTimesShopped < MAX_NUM_TIMES_SHOPPED;
    }

    @Override
    public synchronized void updateLargestTicket(Message message) {
        _largestTicket = Math.max(_largestTicket, message.getPayload()._ticket);
    }

    @Override
    public synchronized int getTicket() {
        return _ticket;
    }

    private synchronized void takeTicket() {
        _ticket = _largestTicket + 1;
    }

    private synchronized void sendReplies() throws IOException {
        System.out.println(_villagerId.getDisplayString() + "exited the Mini Mart " + _numTimesShopped + "/" + MAX_NUM_TIMES_SHOPPED + ". Letting the next villager in.");

        Payload payload = new Payload(_villagerId.getId(), getTicket(), Payload.Command.ACK);
        while (!_replyList.isEmpty()) {
            sendMessage(_replyList.pop(), payload);
        }
    }

    private void sendMessage(VillagerId to, Payload payload) throws IOException {
        try {
            _transport.send(new Message(to.getAddress(), to.getPort(), payload));
        }
        catch (SocketException e) {
            System.out.println("Caught exception [" + e.getLocalizedMessage() + "] when sending a message to " + to.getDisplayString());
        }
    }

    @Override
    public void addReplyToAddress(VillagerId villagerId) {
        _replyList.push(villagerId);
    }

    private synchronized void waitForReplies() {
        // Monitor the _hasResponded array
        while (doNotHaveAllReplies()) {
            try {
                wait();
            }
            catch (InterruptedException ignored) { }
        }
    }

    @Override
    public synchronized boolean isTicketFewerThan(Message message) {
        return message.getPayload()._ticket < _ticket ||
                (message.getPayload()._ticket == _ticket && message.getPort() < _transport.getPort());
    }

    private synchronized void clearReplies() {
        for (int i = 0; i < _totalVillagers; ++i) {
            _hasReplied[i] = false;
        }
    }

    private synchronized boolean doNotHaveAllReplies() {
        for (int i = 0; i < _totalVillagers; ++i) {
            if (i != _villagerId.getId() && !_hasReplied[i]) {
                return true;
            }
        }
        return false;
    }

    private synchronized boolean allHaveNotEnded() {
        for (int i = 0; i < _totalVillagers; ++i) {
            if (!_hasEnded[i]) {
                return true;
            }
        }
        return false;
    }

    private synchronized void waitForAllEnded() {
        System.out.println(_villagerId.getDisplayString() + "waiting for other villagers to end. They need my reply.");

        // Monitor the _hasEnded array
        while (allHaveNotEnded()) {
            try {
                wait();
            }
            catch (InterruptedException ignored) { }
        }
    }

    private void broadcastMessage(Payload.Command command) throws IOException {
        Payload payload = new Payload(_villagerId.getId(), getTicket(), command);
        for (int i = 0; i < _totalVillagers; ++i) {
            if (i != _villagerId.getId()) {
                VillagerId to = new VillagerId(_transport.getAddress(), _portStart + i, i);
                sendMessage(to, payload);
            }
        }
    }

    @Override
    public synchronized void setHasReplied(Message message) {
        int id = message.getPayload()._id;
        if (id >= 0 && id < _totalVillagers) {
            _hasReplied[id] = true;
            notifyAll();                    // Unblock waiting threads
        }
    }

    @Override
    public synchronized void setHasEnded(Message message) {
        int id = message.getPayload()._id;
        if (id >= 0 && id < _totalVillagers) {
            _hasEnded[id] = true;
            notifyAll();                    // Unblock waiting threads
        }
    }

    @Override
    public synchronized boolean isInsideMiniMart() {
        return _insideMiniMart;
    }

    @Override
    public synchronized void enterMiniMart() {
        _insideMiniMart = true;
    }

    @Override
    public synchronized void exitMiniMart() {
        _insideMiniMart = false;
    }
}
