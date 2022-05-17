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
 * This class is used by the MiniMartAccess class to take advantage of the Java try () {} feature. I used this Java
 * feature so that I get a strong guarantee from the JVM that an essential method will always be called, even in the
 * event of a thrown exception.
 *
 * In this case that method is the stopRequestingMiniMartAccess() method below. This method ensures that a villager
 * leaving the critical section is always signalled to the software.
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
