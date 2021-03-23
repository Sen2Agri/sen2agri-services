package org.esa.sen2agri.reports.model;

import java.util.List;
import java.util.Map;

public class ReportResult {
    private Map<String, String> columnLabels;
    private List<?> series;

    public ReportResult(Map<String, String> columnLabels, List<?> series) {
        this.columnLabels = columnLabels;
        this.series = series;
    }

    public Map<String, String> getColumnLabels() {
        return columnLabels;
    }

    public void setColumnLabels(Map<String, String> columnLabels) {
        this.columnLabels = columnLabels;
    }

    public List<?> getSeries() {
        return series;
    }

    public void setSeries(List<?> series) {
        this.series = series;
    }
}
