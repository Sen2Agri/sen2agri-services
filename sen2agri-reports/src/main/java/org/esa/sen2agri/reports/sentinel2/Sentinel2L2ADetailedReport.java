package org.esa.sen2agri.reports.sentinel2;

import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.reports.spi.OpticalDetailedReport;

public class Sentinel2L2ADetailedReport extends OpticalDetailedReport {

    @Override
    public Satellite intendedFor() { return Satellite.Sentinel2; }

}
