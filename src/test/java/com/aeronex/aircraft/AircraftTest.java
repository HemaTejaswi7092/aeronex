package com.aeronex.aircraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.aeronex.aircraft.exception.InvalidAircraftStatusTransitionException;

class AircraftTest {

    private Aircraft aircraftWithStatus(AircraftStatus status) {
        return new Aircraft("N12345", "Boeing", "737-800", 189, status);
    }

    @Test
    void allowsActiveToMaintenance() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.ACTIVE);
        aircraft.transitionTo(AircraftStatus.MAINTENANCE);
        assertThat(aircraft.getStatus()).isEqualTo(AircraftStatus.MAINTENANCE);
    }

    @Test
    void allowsActiveToOutOfService() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.ACTIVE);
        aircraft.transitionTo(AircraftStatus.OUT_OF_SERVICE);
        assertThat(aircraft.getStatus()).isEqualTo(AircraftStatus.OUT_OF_SERVICE);
    }

    @Test
    void allowsMaintenanceToOutOfService() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.MAINTENANCE);
        aircraft.transitionTo(AircraftStatus.OUT_OF_SERVICE);
        assertThat(aircraft.getStatus()).isEqualTo(AircraftStatus.OUT_OF_SERVICE);
    }

    @Test
    void allowsOutOfServiceToActive() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.OUT_OF_SERVICE);
        aircraft.transitionTo(AircraftStatus.ACTIVE);
        assertThat(aircraft.getStatus()).isEqualTo(AircraftStatus.ACTIVE);
    }

    @Test
    void doesNotAllowMaintenanceDirectlyToActive() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.MAINTENANCE);

        assertThat(aircraft.canTransitionTo(AircraftStatus.ACTIVE)).isFalse();
        assertThatThrownBy(() -> aircraft.transitionTo(AircraftStatus.ACTIVE))
                .isInstanceOf(InvalidAircraftStatusTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(AircraftStatus.class)
    void noSelfTransitionsAreAllowed(AircraftStatus status) {
        Aircraft aircraft = aircraftWithStatus(status);
        assertThat(aircraft.canTransitionTo(status)).isFalse();
    }

    @Test
    void illegalTransitionDoesNotMutateStatus() {
        Aircraft aircraft = aircraftWithStatus(AircraftStatus.MAINTENANCE);

        assertThatThrownBy(() -> aircraft.transitionTo(AircraftStatus.ACTIVE))
                .isInstanceOf(InvalidAircraftStatusTransitionException.class);
        assertThat(aircraft.getStatus()).isEqualTo(AircraftStatus.MAINTENANCE);
    }
}
