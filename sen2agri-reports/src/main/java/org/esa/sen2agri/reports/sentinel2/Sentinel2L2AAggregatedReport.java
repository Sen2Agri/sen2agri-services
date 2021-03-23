package org.esa.sen2agri.reports.sentinel2;

import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.reports.spi.OpticalAggregatedReport;

public class Sentinel2L2AAggregatedReport extends OpticalAggregatedReport {

    @Override
    public Satellite intendedFor() { return Satellite.Sentinel2; }

    @Override
    public String rawDataQuery() { return "SELECT * FROM reports.sp_insert_s2_statistics()"; }

    @Override
    public String reportQuery() {
        return "SELECT calendar_date, acquisitions, failed_to_download, processed, not_yet_processed, falsely_processed, errors, clouds " +
                "FROM reports.sp_reports_s2_statistics";
    }

}
