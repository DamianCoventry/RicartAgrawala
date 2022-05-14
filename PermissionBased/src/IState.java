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

public interface IState {
    int getTicket();
    void updateLargestTicket(Message message);
    void addReplyToAddress(VillagerId villagerId);
    void setHasReplied(Message message);
    void setHasEnded(Message message);
    boolean isInsideMiniMart();
    boolean isTicketFewerThan(Message message);
    boolean haveNotFinishedShopping();
}
