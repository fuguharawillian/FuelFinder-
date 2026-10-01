package com.fuelfinder.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HaversineCalculatorTest {

    @Test
    void calculatesZeroDistanceForIdenticalCoordinates() {
        assertEquals(0.0, HaversineCalculator.calculateDistanceKm(
                -23.5505, -46.6333, -23.5505, -46.6333), 0.001);
    }

    @Test
    void calculatesApproximateDistanceBetweenSaoPauloAndRioDeJaneiro() {
        double distance = HaversineCalculator.calculateDistanceKm(
                -23.5505, -46.6333,
                -22.9068, -43.2096);

        assertEquals(357.0, distance, 1.0);
    }

    @Test
    void rejectsCoordinatesOutsideTheirValidRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> HaversineCalculator.calculateDistanceKm(91, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> HaversineCalculator.calculateDistanceKm(Double.NaN, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> HaversineCalculator.calculateDistanceKm(-91, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> HaversineCalculator.calculateDistanceKm(0, 181, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> HaversineCalculator.calculateDistanceKm(0, Double.POSITIVE_INFINITY, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> HaversineCalculator.calculateDistanceKm(0, -181, 0, 0));
    }
}
