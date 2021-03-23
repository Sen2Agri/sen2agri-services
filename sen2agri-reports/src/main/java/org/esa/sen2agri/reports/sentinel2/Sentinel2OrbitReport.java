package org.esa.sen2agri.reports.sentinel2;

import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.reports.spi.OrbitReport;

public class Sentinel2OrbitReport extends OrbitReport {

    @Override
    public Satellite intendedFor() { return Satellite.Sentinel2; }

}
