package com.aeronex.aircraft;

/**
 * Deliberately separate from com.aeronex.flight.TransitionSource even though the
 * values mirror it — the Aircraft and Flight bounded contexts don't share domain
 * enums anywhere else in this codebase (see AircraftStatus vs. FlightStatus), and
 * this milestone keeps that precedent rather than coupling the two.
 */
public enum AircraftTransitionSource {
    CREATED,
    OPERATOR,
    DISRUPTION_AUTO
}
