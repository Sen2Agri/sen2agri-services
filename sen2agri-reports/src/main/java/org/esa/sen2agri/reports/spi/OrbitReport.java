package org.esa.sen2agri.reports.spi;

import org.esa.sen2agri.reports.model.OrbitReportRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class OrbitReport extends BaseReport<OrbitReportRecord> {

    @Override
    public ReportType reportType() { return ReportType.ORBIT; }

    @Override
    public Class<OrbitReportRecord> resultType() { return OrbitReportRecord.class; }

    @Override
    public String rawDataQuery() { return null; }

    @Override
    public Map<String, String> columnLabels() {
        return new LinkedHashMap<String, String>() {{
            put("calendarDate", "Date");
            put("acquisitions", "Acquisitions");
        }};
    }

    @Override
    public String reportQuery() {
        return "SELECT calendar_date, acquisitions FROM reports.sp_reports_" +
                intendedFor().friendlyName().toLowerCase() + "_statistics_orbit";
    }

    @Override
    public Map<String, Map.Entry<DatabaseCondition, String>> parameterDatabaseTypes() {
        return new LinkedHashMap<String, Map.Entry<DatabaseCondition, String>>() {{
            put("siteId", new SimpleEntry<>(DatabaseCondition.EQ, "smallint"));
            put("orbitId", new SimpleEntry<>(DatabaseCondition.EQ, "integer"));
            put("fromDate", new SimpleEntry<>(DatabaseCondition.EQ, "date"));
            put("toDate", new SimpleEntry<>(DatabaseCondition.EQ, "date"));
        }};
    }

    @Override
    public OrbitReportRecord mapRow(ResultSet resultSet) throws SQLException {
        final OrbitReportRecord record = new OrbitReportRecord();
        record.setCalendarDate(resultSet.getDate(1));
        record.setAcquisitions(resultSet.getInt(2));
        return record;
    }

    @Override
    public int executeRawQuery() { return -1; }

    @Override
    public List<OrbitReportRecord> execute(Map<String, Object> parameters) {
        return DatabaseExecutor.executeFunction(this, parameters);
    }
}
