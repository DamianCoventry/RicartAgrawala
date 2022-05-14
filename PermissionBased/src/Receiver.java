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

import javax.net.ssl.ExtendedSSLSession;
import java.io.IOException;

public class Receiver extends Thread {
    private final ITransport _transport;
    private final IState _state;
    private final VillagerId _villagerId;
    private boolean _mustShutdown;

    public Receiver(ITransport transport, IState state, VillagerId villagerId) {
        _transport = transport;
        _state = state;
        _villagerId = villagerId;
        _mustShutdown = false;
    }

    public synchronized void shutdown() {
        _mustShutdown = true;
    }

    private synchronized boolean mustShutdown() {
        return _mustShutdown;
    }

    @Override
    public void run() {
        try {
            while (!mustShutdown()) {
                Message from = _transport.receive();            // blocks

                if (_state.haveNotFinishedShopping()) {
                    switch (from.getPayload()._command) {
                        case TICKET -> {
                            _state.updateLargestTicket(from);
                            if (!_state.isInsideMiniMart() || _state.isTicketFewerThan(from)) {
                                sendReply(from);
                            } else {
                                _state.addReplyToAddress(from.replyTo());
                            }
                        }
                        case ACK -> _state.setHasReplied(from);
                        case ENDED -> _state.setHasEnded(from);
                    }
                }
                else {
                    if (from.getPayload()._command == Payload.Command.ENDED) {
                        _state.setHasEnded(from);
                    }
                    sendReply(from);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendReply(Message from) throws IOException {
        Payload payload = new Payload(_villagerId.getId(), _state.getTicket(), Payload.Command.ACK);
        _transport.send(new Message(from.getAddress(), from.getPort(), payload));
    }
}
