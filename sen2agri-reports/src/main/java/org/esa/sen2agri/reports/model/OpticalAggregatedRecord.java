package org.esa.sen2agri.reports.model;

public class OpticalAggregatedRecord extends AggregatedRecord {
    private int clouds;

    public int getClouds() {
        return clouds;
    }

    public void setClouds(int clouds) {
        this.clouds = clouds;
    }
}
