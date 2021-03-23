package org.esa.sen2agri.reports.landsat8;

import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.reports.spi.OrbitReport;

public class Landsat8OrbitReport extends OrbitReport {

    @Override
    public Satellite intendedFor() { return Satellite.Landsat8; }

}
