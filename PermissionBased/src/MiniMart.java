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

public class MiniMart implements AutoCloseable {
    private final IGroceryShopping _owner;

    public MiniMart(IGroceryShopping owner) {
        _owner = owner;
        _owner.enterMiniMart();
    }

    @Override
    public void close() {
        _owner.exitMiniMart();
    }
}
