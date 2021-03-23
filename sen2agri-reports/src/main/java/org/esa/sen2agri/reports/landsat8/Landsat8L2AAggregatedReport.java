package org.esa.sen2agri.reports.landsat8;

import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.reports.spi.OpticalAggregatedReport;

public class Landsat8L2AAggregatedReport extends OpticalAggregatedReport {

    @Override
    public Satellite intendedFor() { return Satellite.Landsat8; }

    @Override
    public String rawDataQuery() { return "SELECT * FROM reports.sp_insert_l8_statistics()"; }

    @Override
    public String reportQuery() {
        return "SELECT calendar_date, acquisitions, failed_to_download, processed, not_yet_processed, falsely_processed, errors, clouds " +
                "FROM reports.sp_reports_l8_statistics";
    }
}
