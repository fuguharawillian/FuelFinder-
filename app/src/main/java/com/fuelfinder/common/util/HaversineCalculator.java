package com.fuelfinder.common.util;

public final class HaversineCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private HaversineCalculator() {
    }

    public static double calculateDistanceKm(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2) {
        validateCoordinates(latitude1, longitude1);
        validateCoordinates(latitude2, longitude2);

        double latitudeDifference = Math.toRadians(latitude2 - latitude1);
        double longitudeDifference = Math.toRadians(longitude2 - longitude1);
        double latitude1Radians = Math.toRadians(latitude1);
        double latitude2Radians = Math.toRadians(latitude2);

        double haversine = Math.pow(Math.sin(latitudeDifference / 2), 2)
                + Math.cos(latitude1Radians)
                * Math.cos(latitude2Radians)
                * Math.pow(Math.sin(longitudeDifference / 2), 2);
        double centralAngle = 2 * Math.asin(Math.sqrt(Math.min(1.0, haversine)));

        return EARTH_RADIUS_KM * centralAngle;
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be finite and between -90 and 90 degrees.");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be finite and between -180 and 180 degrees.");
        }
    }
}
