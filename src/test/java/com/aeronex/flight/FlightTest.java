package com.aeronex.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.aeronex.airport.Airport;
import com.aeronex.flight.exception.InvalidFlightStatusTransitionException;

class FlightTest {

    private Flight flightWithStatus(FlightStatus status) {
        Airport origin = new Airport("JFK", null, "JFK Airport", "New York", "USA", "America/New_York",
                BigDecimal.ZERO, BigDecimal.ZERO);
        Airport destination = new Airport("LAX", null, "LAX Airport", "Los Angeles", "USA", "America/Los_Angeles",
                BigDecimal.ZERO, BigDecimal.ZERO);
        return new Flight("AA100", origin, destination, null,
                OffsetDateTime.parse("2026-06-01T10:00:00Z"), OffsetDateTime.parse("2026-06-01T13:00:00Z"),
                null, null, status);
    }

    @Test
    void allowsFullHappyPathLifecycle() {
        Flight flight = flightWithStatus(FlightStatus.SCHEDULED);

        flight.transitionTo(FlightStatus.BOARDING);
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.BOARDING);

        flight.transitionTo(FlightStatus.DEPARTED);
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.DEPARTED);

        flight.transitionTo(FlightStatus.ARRIVED);
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.ARRIVED);
    }

    @Test
    void allowsDelayFromScheduledAndBoarding() {
        Flight scheduled = flightWithStatus(FlightStatus.SCHEDULED);
        scheduled.transitionTo(FlightStatus.DELAYED);
        assertThat(scheduled.getStatus()).isEqualTo(FlightStatus.DELAYED);

        Flight boarding = flightWithStatus(FlightStatus.BOARDING);
        boarding.transitionTo(FlightStatus.DELAYED);
        assertThat(boarding.getStatus()).isEqualTo(FlightStatus.DELAYED);
    }

    @Test
    void allowsCancellationFromScheduledBoardingAndDelayed() {
        assertThat(flightWithStatus(FlightStatus.SCHEDULED).canTransitionTo(FlightStatus.CANCELLED)).isTrue();
        assertThat(flightWithStatus(FlightStatus.BOARDING).canTransitionTo(FlightStatus.CANCELLED)).isTrue();
        assertThat(flightWithStatus(FlightStatus.DELAYED).canTransitionTo(FlightStatus.CANCELLED)).isTrue();
    }

    @Test
    void allowsReturningFromDelayedToBoarding() {
        Flight flight = flightWithStatus(FlightStatus.DELAYED);
        flight.transitionTo(FlightStatus.BOARDING);
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.BOARDING);
    }

    @Test
    void doesNotAllowCancellationAfterDeparture() {
        Flight flight = flightWithStatus(FlightStatus.DEPARTED);

        assertThat(flight.canTransitionTo(FlightStatus.CANCELLED)).isFalse();
        assertThatThrownBy(() -> flight.transitionTo(FlightStatus.CANCELLED))
                .isInstanceOf(InvalidFlightStatusTransitionException.class);
    }

    @Test
    void doesNotAllowDelayedToDepartDirectly() {
        Flight flight = flightWithStatus(FlightStatus.DELAYED);

        assertThat(flight.canTransitionTo(FlightStatus.DEPARTED)).isFalse();
        assertThatThrownBy(() -> flight.transitionTo(FlightStatus.DEPARTED))
                .isInstanceOf(InvalidFlightStatusTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(FlightStatus.class)
    void arrivedAndCancelledAreTerminal(FlightStatus target) {
        Flight arrived = flightWithStatus(FlightStatus.ARRIVED);
        assertThat(arrived.canTransitionTo(target)).isFalse();

        Flight cancelled = flightWithStatus(FlightStatus.CANCELLED);
        assertThat(cancelled.canTransitionTo(target)).isFalse();
    }

    @Test
    void illegalTransitionDoesNotMutateStatus() {
        Flight flight = flightWithStatus(FlightStatus.SCHEDULED);

        assertThatThrownBy(() -> flight.transitionTo(FlightStatus.ARRIVED))
                .isInstanceOf(InvalidFlightStatusTransitionException.class);
        assertThat(flight.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
    }
}
