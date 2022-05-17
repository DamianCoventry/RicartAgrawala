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

/**
 * This class provides the ability for a villager to receive messages. Each villager has their own instance of this
 * class.
 *
 * The receiving logic runs in its own thread because the call to receive from the IMessenger reference blocks until a
 * message is received. Therefore, the ability for any villager to avoid starvation is up to the other villagers.
 *
 * This behaviour has the side effect that all villagers must stay resident, and active, so that all villagers can
 * finish their 3 shopping sessions.
 *
 * The best example of this is the waitForOtherVillagersToFinishShopping() method within the Receiver class. This only
 * exists to send messages to other villagers that have not finished shopping.
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
    }

    /**
     * Sets an internal shutdown flag to true. This method is synchronised because the expectation is that the Villager
     * thread and the Receiver thread will read/write this value concurrently.
     */
    public synchronized void shutdown() {
        _mustShutdown = true;
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
     * Villager thread will only call that method when it is certain that all villagers have finished shopping.
     *
     * Roughly half of the Ricart-Agrawala algorithm is implemented here. The other half is within the Villager class.
     *
     * The approach taken here is to be cognisant of two states: finished shopping, and not finished shopping.
     *
     * The not finished shopping state must perform all Ricart-Agrawala logic provided by this class. It must record
     * acknowledgements, record finished shopping messages, update the largest known ticket number, record other
     * villager addresses, and send our own acknowledgements.
     *
     * When this villager is in the finished shopping state, the only task we need to perform is responding to
     * finished shopping messages from other villagers. This prevents other villagers from becoming starved. If we did
     * any of the other aforementioned tasks, then we would confuse the Ricart-Agrawala algorithm's logic.
     */
    @Override
    public void run() {
        try {
            while (!mustShutdown()) {
                Message from = _messenger.receive();        // blocks until a message arrives

                if (_villager.hasNotFinishedShopping()) {
                    // this code block represents the not finished shopping state

                    if (from.isAcknowledgement()) {
                        // recording this state allows the Villager thread to enter the mini mart.
                        // do NOT send a response now. we don't want the sender to enter the mini mart yet.
                        _villager.recordAcknowledgement(from);
                    }
                    else if (from.isFinishedShopping()) {
                        // recording this state allows the Villager thread to end.
                        // do NOT send a response now. we don't want the sender to enter the mini mart yet.
                        _villager.recordFinishedShopping(from);
                    }
                    else if (from.isTicketNumber()) {
                        // if this is the largest ticket number we've seen then save it
                        _villager.updateLargestTicket(from);

                        if (!_villager.isRequestingMiniMartAccess() || _villager.doesVillagerShopBeforeMe(from)) {
                            // by replying to the sender we're giving our consent for them to enter the mini mart before
                            // us. if all other villagers do the same, then the sender can happily enter the mini mart.
                            replyToVillagersMessage(from);
                        }
                        else {
                            // we're either currently requesting mini mart access, or we should enter the mini mart
                            // before the sender, therefore, do NOT send a response now. we don't want the sender to
                            // enter the mini mart yet.
                            _villager.recordVillagersAddress(from.makeReplyToAddress());
                        }
                    }
                }
                else {
                    // this code block represents the finished shopping state

                    if (from.isFinishedShopping()) {
                        // recording this state allows the Villager thread to end
                        _villager.recordFinishedShopping(from);
                    }

                    // provide a message so that the sender is not starved
                    replyToVillagersMessage(from);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a reply message to the passed in message. The payload of the message is the details of this villager.
     * @param from the message to reply to
     * @throws IOException if the message is unable to be sent
     */
    private void replyToVillagersMessage(Message from) throws IOException {
        _messenger.send(Message.makeReplyMessage(from, _villager));
    }
}
