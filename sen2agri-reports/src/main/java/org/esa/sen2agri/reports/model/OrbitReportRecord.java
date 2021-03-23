package org.esa.sen2agri.reports.model;

import java.util.Date;

public class OrbitReportRecord {
    private Date calendarDate;
    private int acquisitions;

    public Date getCalendarDate() {
        return calendarDate;
    }

    public void setCalendarDate(Date calendarDate) {
        this.calendarDate = calendarDate;
    }

    public int getAcquisitions() {
        return acquisitions;
    }

    public void setAcquisitions(int acquisitions) {
        this.acquisitions = acquisitions;
    }
}
