package org.esa.sen2agri.reports.spi;

import org.esa.sen2agri.reports.model.OpticalAggregatedRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class OpticalAggregatedReport extends BaseReport<OpticalAggregatedRecord> {

    @Override
    public ReportType reportType() { return ReportType.AGGREGATE; }

    @Override
    public Class<OpticalAggregatedRecord> resultType() { return OpticalAggregatedRecord.class; }

    @Override
    public Map<String, String> columnLabels() {
        return new LinkedHashMap<String, String>() {{
            put("calendarDate", "Date");
            put("acquisitions", "Acquisitions");
            put("downloadFailed", "Failed to Download");
            put("processed", "Processed");
            put("notYetProcessed", "Not Yet Processed");
            put("falselyProcessed", "Falsely Processed");
            put("errors", "Errors");
            put("clouds", "Cloudy");
        }};
    }

    @Override
    public Map<String, Map.Entry<DatabaseCondition, String>> parameterDatabaseTypes() {
        return new LinkedHashMap<String, Map.Entry<DatabaseCondition, String>>() {{
            put("siteId", new AbstractMap.SimpleEntry<>(DatabaseCondition.EQ, "smallint"));
            put("orbitId", new AbstractMap.SimpleEntry<>(DatabaseCondition.EQ, "integer"));
            put("fromDate", new AbstractMap.SimpleEntry<>(DatabaseCondition.EQ, "date"));
            put("toDate", new AbstractMap.SimpleEntry<>(DatabaseCondition.EQ, "date"));
        }};
    }

    @Override
    public OpticalAggregatedRecord mapRow(ResultSet resultSet) throws SQLException {
        final OpticalAggregatedRecord record = new OpticalAggregatedRecord();
        record.setCalendarDate(resultSet.getDate(1));
        record.setAcquisitions(resultSet.getInt(2));
        record.setDownloadFailed(resultSet.getInt(3));
        record.setProcessed(resultSet.getInt(4));
        record.setNotYetProcessed(resultSet.getInt(5));
        record.setFalselyProcessed(resultSet.getInt(6));
        record.setErrors(resultSet.getInt(7));
        record.setClouds(resultSet.getInt(8));
        return record;
    }

    @Override
    public int executeRawQuery() { return DatabaseExecutor.executeRawQuery(this); }

    @Override
    public List<OpticalAggregatedRecord> execute(Map<String, Object> parameters) {
        return DatabaseExecutor.executeFunction(this, parameters);
    }
}
