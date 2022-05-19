import java.io.IOException;

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

public class Receiver extends Thread {
    private final IMessenger _messenger;
    private final IVillager _villager;
    private boolean _mustShutdown;

    /**
     * Constructs a Receiver object by saving the passed in references for later use.
     * @param messenger a reference to a messenger object owned elsewhere
     * @param villager a reference to a villager object owned elsewhere
     */
    public Receiver(IMessenger messenger, IVillager villager) {
        _messenger = messenger;
        _villager = villager;
        _mustShutdown = false;
        setName("VillagerReceiver" + _villager.getMyId().getIndex());
    }

    /**
     * Sets an internal shutdown flag to true. This method is synchronised because the expectation is that the Villager
     * thread and the Receiver thread will read/write this value concurrently.
     */
    public synchronized void shutdown() throws IOException {
        _mustShutdown = true;

        // unblock our own call to _messenger.receive(). this is required for the last villager to end their Receiver
        // thread. all villagers before the last one will be unblocked by each other.
        _messenger.send(Message.makeMessage(_villager.getMyId(), Payload.makeFinishedShopping(_villager)));
    }

    /**
     * Reads the current value of an internal shutdown flag. This method is synchronised because the expectation is that
     * the Villager thread and the Receiver thread will read/write this value concurrently.
     */
    private synchronized boolean mustShutdown() {
        return _mustShutdown;
    }

    /**
     * This method is the core receiving logic for a villager. All received messages are processed by this method, and
     * there is no other code in this project receiving messages.
     *
     * This method ends when an exception is thrown, or the Villager thread uses the public shutdown() method. The
     * Villager thread will only call that method when it is certain that this villager instance has finished shopping.
     *
     * Roughly half of the Ricart-Agrawala algorithm is implemented here. The other half is within the Villager class.
     *
     * The approach here is straight forward -- we inspect the received message then record some state based upon the
     * message's type. For the case of receiving a request for the token, it's possible we immediately send the token to
     * some other villager.
     */
    @Override
    public void run() {
        try {
            while (!mustShutdown()) {
                Message from = _messenger.receive();        // blocks until a message arrives

                if (from.isRequestForToken()) {
                    // recording this request for a token allows this instance to potentially send the token to
                    // the sender.
                    _villager.recordRequestForToken(from);

                    // if this instance is not currently requesting mini mart access AND has the token, then we can
                    // send it to some other villager right now. otherwise the Villager thread will send it later.
                    if (_villager.isNotRequestingMiniMartAccess() && _villager.hasToken()) {
                        _villager.sendTokenToAnotherVillager();
                    }
                }
                else if (from.isToken()) {
                    // recording the token allows this villager to enter the mini mart
                    _villager.recordTokenAndGrantedList(from);
                }
                else if (from.isFinishedShopping()) {
                    // recording this state prevents the sender of this message from receiving the token
                    _villager.recordFinishedShopping(from);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
