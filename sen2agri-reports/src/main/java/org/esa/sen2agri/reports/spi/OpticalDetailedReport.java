package org.esa.sen2agri.reports.spi;

import org.esa.sen2agri.reports.model.OpticalDetailedRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class OpticalDetailedReport  extends BaseReport<OpticalDetailedRecord> {

    @Override
    public ReportType reportType() { return ReportType.DETAIL; }

    @Override
    public Class<OpticalDetailedRecord> resultType() { return OpticalDetailedRecord.class; }

    @Override
    public String rawDataQuery() { return null; }

    @Override
    public Map<String, String> columnLabels() {
        return new LinkedHashMap<String, String>() {{
            put("siteId", "Site ID");
            put("productId", "Product ID");
            put("orbit", "Orbit");
            put("acquisitionDate", "Date");
            put("acquisition", "Product");
            put("acquisitionStatus", "Status");
            put("statusReason", "Status Reason");
            put("l2Product", "L2 Product");
            put("clouds", "Clouds");
        }};
    }

    @Override
    public String reportQuery() {
        return "SELECT site_id, downloader_history_id, orbit_id, acquisition_date, product_name, " +
                "status_description, l2_product, clouds, status_reason FROM reports.sp_reports_" +
                intendedFor().friendlyName().toLowerCase() + "_detail";
    }

    @Override
    public Map<String, Map.Entry<DatabaseCondition, String>> parameterDatabaseTypes() {
        return new LinkedHashMap<String, Map.Entry<DatabaseCondition, String>>() {{
            put("siteId", new SimpleEntry<>(DatabaseCondition.EQ, "smallint"));
            put("orbitId", new SimpleEntry<>(DatabaseCondition.EQ, "integer"));
            put("fromDate", new SimpleEntry<>(DatabaseCondition.GTE, "date"));
            put("toDate", new SimpleEntry<>(DatabaseCondition.LTE, "date"));
        }};
    }

    @Override
    public OpticalDetailedRecord mapRow(ResultSet resultSet) throws SQLException {
        final OpticalDetailedRecord record = new OpticalDetailedRecord();
        record.setSiteId(resultSet.getShort(1));
        record.setProductId(resultSet.getInt(2));
        record.setOrbit(resultSet.getInt(3));
        record.setAcquisitionDate(resultSet.getDate(4));
        record.setAcquisition(resultSet.getString(5));
        record.setAcquisitionStatus(resultSet.getString(6));
        record.setL2Product(resultSet.getString(7));
        record.setClouds(resultSet.getInt(8));
        record.setStatusReason(resultSet.getString(9));
        return record;
    }

    @Override
    public int executeRawQuery() { return -1; }

    @Override
    public List<OpticalDetailedRecord> execute(Map<String, Object> parameters) {
        return DatabaseExecutor.executeFunction(this, parameters);
    }
}
