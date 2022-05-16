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
 * This class is used by the MiniMartAccess class to take advantage of the Java try () {} feature so that we get a
 * strong guarantee that the mini mart access will always stop being requested.
 *
 * This class is implemented by the Villager class. That class also extends Thread hence the need to not name the
 * methods simply start() and stop().
 */
public interface IRequestsMiniMartAccess {
    /**
     * Used to signal the intent to start requesting mini mart access
     */
    void startRequestingMiniMartAccess();

    /**
     * Used to signal the intent to stop requesting mini mart access
     */
    void stopRequestingMiniMartAccess();
}
