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

/**
 * This class exists to provide a guarantee that a villager will STOP requesting mini mart access. This reduces the
 * chances that a developer will inadvertently forget to do so, or accidentally provide a path through the code that
 * does not stop the request i.e. a thrown exception. This is achieved by using the Java try() {} feature and
 * implementing AutoCloseable.
 *
 * This is required due to the Ricart-Agrawala algorithm being aware of 'quiescent nodes', or as the lecture notes put
 * it:
 *      "If a node remains inactive for a long period, other nodes cannot enter the critical section because of the
 *      ever-increasing ticket numbers."
 *
 * The Receiver class is the place where mini mart access is tested. The Receiver class uses the IVillager interface's
 * method isRequestingMiniMartAccess() to perform the test.
 *
 * To reduce class coupling, the MiniMartAccess constructor need not accept a reference to the entire Villager class,
 * just the far smaller IRequestsMiniMartAccess interface.
 */
public class MiniMartAccess implements AutoCloseable {
    private final IRequestsMiniMartAccess _owner;

    /**
     * Saves the reference to the owner class so that it can be used in the close() method, then lets the owner know
     * that mini mart access is requested.
     * @param owner a class that cares about mini mart access being requested
     */
    public MiniMartAccess(IRequestsMiniMartAccess owner) {
        _owner = owner;
        _owner.startRequestingMiniMartAccess();
    }

    /**
     * Lets the owner know that mini mart access is no longer requested.
     */
    @Override
    public void close() {
        _owner.stopRequestingMiniMartAccess();
    }
}
