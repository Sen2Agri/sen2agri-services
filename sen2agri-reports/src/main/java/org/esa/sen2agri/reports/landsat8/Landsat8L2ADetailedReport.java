package org.esa.sen2agri.reports.landsat8;

import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.reports.spi.OpticalDetailedReport;

public class Landsat8L2ADetailedReport extends OpticalDetailedReport {

    @Override
    public Satellite intendedFor() { return Satellite.Landsat8; }
}
