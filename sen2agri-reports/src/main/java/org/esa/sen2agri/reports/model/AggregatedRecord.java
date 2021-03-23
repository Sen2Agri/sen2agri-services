package org.esa.sen2agri.reports.model;

import java.util.Date;

public abstract class AggregatedRecord {
    private Date calendarDate;
    private int acquisitions;
    private int downloadFailed;
    private int processed;
    private int notYetProcessed;
    private int falselyProcessed;
    private int errors;

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

    public int getDownloadFailed() {
        return downloadFailed;
    }

    public void setDownloadFailed(int downloadFailed) {
        this.downloadFailed = downloadFailed;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getNotYetProcessed() {
        return notYetProcessed;
    }

    public void setNotYetProcessed(int notYetProcessed) {
        this.notYetProcessed = notYetProcessed;
    }

    public int getFalselyProcessed() {
        return falselyProcessed;
    }

    public void setFalselyProcessed(int falselyProcessed) {
        this.falselyProcessed = falselyProcessed;
    }

    public int getErrors() {
        return errors;
    }

    public void setErrors(int errors) {
        this.errors = errors;
    }
}
