package org.esa.sen2agri.reports.spi;

import org.esa.sen2agri.entities.enums.Satellite;

import java.util.List;
import java.util.Map;

public interface Report<T> {
    default String name() {
        return intendedFor().friendlyName() + "-" + reportType().value();
    }
    Satellite intendedFor();
    ReportType reportType();
    int executeRawQuery();
    Map<String, String> columnLabels();
    List<T> execute(Map<String, Object> parameters);
}
