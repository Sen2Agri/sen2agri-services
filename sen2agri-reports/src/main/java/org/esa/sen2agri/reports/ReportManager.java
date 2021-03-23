package org.esa.sen2agri.reports;

import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.reports.model.ReportResult;
import org.esa.sen2agri.reports.spi.Report;
import org.esa.sen2agri.reports.spi.ReportType;
import ro.cs.tao.Tuple;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ReportManager {
    private static final ReportManager instance;
    private final Map<Tuple<Satellite, ReportType>, Report<?>> reportMap;


    static {
        instance = new ReportManager();
        final Set<Report> reports = ServiceRegistryManager.getInstance().getServiceRegistry(Report.class).getServices();
        for (Report<?> report : reports) {
            instance.reportMap.put(new Tuple<>(report.intendedFor(), report.reportType()), report);
        }
    }

    private ReportManager() {
        this.reportMap = new HashMap<>();
    }

    public static ReportResult executeReport(Satellite satellite, ReportType reportType, Map<String, Object> parameters) {
        final Report<?> report = instance.reportMap.get(new Tuple<>(satellite, reportType));
        if (report == null) {
            throw new IllegalArgumentException(String.format("A %s report for satellite %s does not exist",
                                                             reportType.value(), satellite.friendlyName()));
        }
        return new ReportResult(report.columnLabels(), report.execute(parameters));
    }

}
