package com.example.petroglyphcam;

public class PetroglyphItem {
    private String imageUri;
    private String description;
    private String coordinates;
    private String altitude;
    private String preservation;
    private String date;

    public PetroglyphItem(String imageUri, String description, String coordinates,
                          String altitude, String preservation, String date) {
        this.imageUri = imageUri;
        this.description = description;
        this.coordinates = coordinates;
        this.altitude = altitude;
        this.preservation = preservation;
        this.date = date;
    }

    // Геттеры
    public String getImageUri() { return imageUri; }
    public String getDescription() { return description; }
    public String getCoordinates() { return coordinates; }
    public String getAltitude() { return altitude; }
    public String getPreservation() { return preservation; }
    public String getDate() { return date; }
}