package org.esa.sen2agri.entities;

public class S2Tile {
    private final String id;
    private final String crs;
    private final int[] coordinates;

    public S2Tile(String id, String crs, int[] coordinates) {
        this.id = id;
        this.crs = crs;
        this.coordinates = coordinates;
    }

    public String getId() {
        return id;
    }

    public String getCrs() {
        return crs;
    }

    public int[] getCoordinates() {
        return coordinates;
    }
}
