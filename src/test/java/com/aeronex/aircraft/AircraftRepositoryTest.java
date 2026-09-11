package com.aeronex.aircraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class AircraftRepositoryTest {

    @Autowired
    private AircraftRepository aircraftRepository;

    private Aircraft boeing737() {
        return new Aircraft("N12345", "Boeing", "737-800", 189, AircraftStatus.ACTIVE);
    }

    @Test
    void savesAndFindsByRegistrationNumber() {
        aircraftRepository.saveAndFlush(boeing737());

        assertThat(aircraftRepository.findByRegistrationNumber("N12345")).isPresent();
        assertThat(aircraftRepository.existsByRegistrationNumber("N12345")).isTrue();
    }

    @Test
    void rejectsDuplicateRegistrationNumber() {
        aircraftRepository.saveAndFlush(boeing737());

        Aircraft duplicate = new Aircraft("N12345", "Airbus", "A320", 150, AircraftStatus.MAINTENANCE);

        assertThatThrownBy(() -> aircraftRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsMultipleAircraftWithDifferentRegistrationNumbers() {
        Aircraft first = new Aircraft("N11111", "Boeing", "737-800", 189, AircraftStatus.ACTIVE);
        Aircraft second = new Aircraft("N22222", "Airbus", "A320", 150, AircraftStatus.OUT_OF_SERVICE);

        aircraftRepository.saveAndFlush(first);
        aircraftRepository.saveAndFlush(second);

        assertThat(aircraftRepository.count()).isEqualTo(2);
    }
}
