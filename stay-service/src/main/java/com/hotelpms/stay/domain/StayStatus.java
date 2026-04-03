package com.hotelpms.stay.domain;

/**
 * Represents the status of a stay in the hotel.
 */
public enum StayStatus {

    /**
     * Guest is expected to arrive.
     */
    EXPECTED,

    /**
     * Guest has checked in and is currently staying.
     */
    CHECKED_IN,

    /**
     * Guest has checked out.
     */
    CHECKED_OUT,

    /**
     * Stay was cancelled before arrival or after check-in (manual rollback).
     */
    CANCELLED
}
