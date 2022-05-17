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

/**
 * This class provides the ability for a villager to enter the mini mart mutually exclusively.
 *
 * Roughly half of the Ricart-Agrawala algorithm is implemented here. The other half is within the Receiver class.
 *
 * The logic within this class runs in its own thread so that this node can run multiple villagers concurrently. From a
 * villager's point of view, it doesn't matter where it's run, nor where the other villagers are run. Each villager can
 * be reached via an IP address and port, that's the important part.
 *
 * The thread this class runs must be aware of all Ricart-Agrawala algorithm rules, and has the additional requirement
 * that the thread must stay resident until it's certain all villagers have finished shopping. This is because villagers
 * will starve if all other villagers are not acknowledging its messages. Essentially, they'll be stuck in a call to
 * _messenger.receive() with no message source to break them out of it.
 */
public class Villager extends Thread implements IVillager, IRequestsMiniMartAccess {
    public static final int NUM_VILLAGERS_PER_NODE = 5;
    public static final int MAX_NUM_TIMES_SHOPPED = 3;
    private static final int MIN_SHOPPING_TIME = 1000; // just to keep it interesting
    private static final int MAX_SHOPPING_TIME = 2750;
    private static final int MIN_SHOPPING_MSGS = 2; // just to keep it interesting
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

    private boolean _requestingMiniMartAccess; // essentially it means 'are we in the critical section?'
    private int _ticket;
    private int _largestTicket;
    private int _numTimesShopped;

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

        _requestingMiniMartAccess = false;
        _replyList = new ArrayDeque<>();

        _totalVillagers = totalVillagers;
        _villagerHasReplied = new boolean[totalVillagers]; // initialised via clearOtherVillagersReplies() below
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

    /**
     * This method is the core loop of the Villager. It only loops 3 times then ends. Each pass through the loop
     * represents the villager shopping one time. After the loop is ended, this thread will sit and wait inside the
     * waitForOtherVillagersToFinishShopping() method until all other villagers within the simulation have also looped
     * 3 times. This extra wait after the core loop prevents other villagers from facing message starvation.
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
                // this try block contains the villager's request to access the mini mart. by implementing this we
                // provide a way for the Receiver thread to know this thread is currently requesting mini mart access.
                // this is achieved by the constructor + close methods of the MiniMartAccess class calling back into the
                // Villager class, which in turn sets the value of the _requestingMiniMartAccess variable.
                try (MiniMartAccess ignored = new MiniMartAccess(this)) {
                    // _requestingMiniMartAccess is true at this point
                    takeTheNextTicket();
                    clearOtherVillagersReplies();
                    tellOtherVillagersMyTicket();
                    waitForOtherVillagersToReply(); // implements the Monitor pattern inside
                    enterMiniMart();
                    incrementShoppingCount(); // causes hasNotFinishedShopping() to return false eventually
                }
                // _requestingMiniMartAccess is strongly guaranteed to be false at this point

                tellOtherVillagersIveExitedTheMiniMart(); // the next villager in the 'virtual queue' will have a turn
            }

            waitForOtherVillagersToFinishShopping(); // implements the Monitor pattern inside
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            _receiver.shutdown();
            _done.countDown();
        }
    }

    /**
     * Updates the internal knowledge of the largest ticket.
     *
     * Only called by the Receiver thread, but the Villager thread reads the value of _largestTicket, hence this method
     * is synchronised.
     */
    @Override
    public synchronized void updateLargestTicket(Message message) {
        _largestTicket = message.getLargerTicket(_largestTicket);
    }

    /**
     * Retrieves the address of this villager.
     *
     * Only called by the Payload class. The Payload class is used by the Receiver thread and the Villager thread, hence
     * this method is synchronised.
     */
    @Override
    public synchronized VillagerAddress getMyId() {
        return _myId;
    }

    /**
     * Retrieves the ticket number of this villager.
     *
     * Only called by the Payload class. The Payload class is used by the Receiver thread and the Villager thread, hence
     * this method is synchronised.
     */
    @Override
    public synchronized int getTicket() {
        return _ticket;
    }

    /**
     * Copies the supplied villager address into internal storage.
     *
     * Only called by the Receiver thread, but the Villager thread reads the values of _replyList, hence this method is
     * synchronised.
     */
    @Override
    public synchronized void recordVillagersAddress(VillagerAddress villagerAddress) {
        _replyList.push(villagerAddress);
    }

    /**
     * Determines if the villager that sent the message must go before this Villager. The test also solves ties by using
     * a tiebreaker value.
     *
     * Only called by the Receiver thread, but the Villager thread reads the values of _ticket, hence this method is
     * synchronised.
     */
    @Override
    public synchronized boolean doesVillagerShopBeforeMe(Message message) {
        return message.isFewerThan(_ticket, _messenger.getTiebreakerValue());
    }

    /**
     * Updates internal storage to indicate that a villager has acknowledged a message sent by this Villager. This
     * method also nudges the monitor within the waitForOtherVillagersToReply() method.
     *
     * Only called by the Receiver thread, but the Villager thread reads the values of _villagerHasReplied, hence this
     * method is synchronised.
     */
    @Override
    public synchronized void recordAcknowledgement(Message message) {
        if (message.getVillagerIndex() >= 0 && message.getVillagerIndex() < _totalVillagers) {
            _villagerHasReplied[message.getVillagerIndex()] = true;
            notifyAll();        // Unblock waiting threads
        }
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

    /**
     * Determines if this villager has NOT finished shopping.
     *
     * Only called by the Receiver thread, but the Villager thread writes the value of _numTimesShopped, hence this
     * method is synchronised.
     */
    @Override
    public synchronized boolean hasNotFinishedShopping() {
        return _numTimesShopped < MAX_NUM_TIMES_SHOPPED;
    }

    /**
     * Determines if this villager is requesting mini mart access.
     *
     * Only called by the Receiver thread, but the Villager thread writes the value of _requestingMiniMartAccess, hence
     * this method is synchronised.
     */
    @Override
    public synchronized boolean isRequestingMiniMartAccess() {
        return _requestingMiniMartAccess;
    }

    /**
     * Updates internal storage to indicate that this villager has started to request mini mart access.
     *
     * Only called by the MiniMartAccess class as part of the core loop above. The Receiver thread will read the value
     * of _requestingMiniMartAccess via the call to isRequestingMiniMartAccess(), hence this method is synchronised.
     */
    @Override
    public synchronized void startRequestingMiniMartAccess() {
        _requestingMiniMartAccess = true;
    }

    /**
     * Updates internal storage to indicate that this villager has stopped requesting mini mart access.
     *
     * Only called by the MiniMartAccess class as part of the core loop above. The Receiver thread will read the value
     * of _requestingMiniMartAccess via the call to isRequestingMiniMartAccess(), hence this method is synchronised.
     */
    @Override
    public synchronized void stopRequestingMiniMartAccess() {
        _requestingMiniMartAccess = false;
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

    /**
     * Updates internal state to use a new ticket number.
     *
     * Only called above by the core loop. The Receiver thread reads the value of the _ticket variable, hence this
     * method is synchronised.
     */
    private synchronized void takeTheNextTicket() {
        _ticket = _largestTicket + 1;
    }

    /**
     * For each iteration of the core loop, this method is called to reset the knowledge of other villagers replying to
     * our messages. This must happen so that we can track whether our most recent message has been acknowledged.
     *
     * The _villagerHasReplied array is accessed by the Receiver thread, hence this method is synchronised.
     */
    private synchronized void clearOtherVillagersReplies() {
        for (int i = 0; i < _totalVillagers; ++i) {
            _villagerHasReplied[i] = false;
        }
    }

    /**
     * For each iteration of the core loop, this method is called to block the Villager thread until its current message
     * has been acknowledged by all other villagers. This is a core part of the Ricart-Agrawala algorithm. This method
     * implements the Monitor pattern.
     *
     * The _villagerHasReplied array is accessed by the Receiver thread, hence this method is synchronised.
     */
    private synchronized void waitForOtherVillagersToReply() {
        // Monitor the _villagerHasReplied array
        while (haveOtherVillagersNotReplied()) {
            try {
                wait();
            }
            catch (InterruptedException ignored) { }
        }
    }

    /**
     * After the core loop has ended, this method is called to block the Villager thread until we know that all other
     * villagers have also finished their core loops, i.e. have finished their 3 shopping sessions. This method
     * implements the Monitor pattern.
     *
     * The _villagerHasFinishedShopping array is accessed by the Receiver thread, hence this method is synchronised.
     */
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

    /**
     * Determines whether all other villagers have NOT replied to this villager's most recent message.
     *
     * The Receiver thread writes to the _villagerHasReplied array, and the Villager thread reads from it, hence this
     * method is synchronised.
     */
    private synchronized boolean haveOtherVillagersNotReplied() {
        for (int i = 0; i < _totalVillagers; ++i) {
            if (i != _myId.getIndex() && !_villagerHasReplied[i]) { // be sure to skip ourselves when looping
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether all other villagers have NOT finished shopping.
     *
     * The Receiver thread writes to the _villagerHasFinishedShopping array, and the Villager thread reads from it,
     * hence this method is synchronised.
     */
    private synchronized boolean haveOtherVillagersNotFinishedShopping() {
        for (int i = 0; i < _totalVillagers; ++i) {
            if (!_villagerHasFinishedShopping[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends a message to the other villagers who will enter the mini mart AFTER this villager. This was determined
     * earlier by the Receiver thread recording the address details from received messages.
     *
     * These other villagers can now be woken up from their wait loops. They will check to see if they've received this
     * wake-up message from all villagers.
     *
     * The Receiver thread writes to the _replyList array, and the Villager thread reads from it, hence this method is
     * synchronised.
     */
    private synchronized void tellOtherVillagersIveExitedTheMiniMart() throws IOException {
        System.out.println(_myId.getDisplayString() + "exited the Mini Mart " +
                _numTimesShopped + "/" + MAX_NUM_TIMES_SHOPPED + ". Letting the next villager in.");

        Payload payload = Payload.makeAcknowledgement(this);
        while (!_replyList.isEmpty()) {
            sendMessageToVillager(_replyList.pop(), payload);
        }
    }

    /**
     * Sends this villager's ticket number to all other villagers. This is a core part of the Ricart-Agrawala algorithm.
     */
    private void tellOtherVillagersMyTicket() throws IOException {
        sendMessageToOtherVillagers(Payload.makeTicketNumber(this));
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
