package org.esa.sen2agri.reports.web;

import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.reports.ReportManager;
import org.esa.sen2agri.reports.model.OrbitReportRecord;
import org.esa.sen2agri.reports.model.ReportParamsBuilder;
import org.esa.sen2agri.reports.model.ReportResult;
import org.esa.sen2agri.reports.spi.DatabaseExecutor;
import org.esa.sen2agri.reports.spi.ReportType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import ro.cs.tao.EnumUtils;
import ro.cs.tao.services.commons.ControllerBase;
import ro.cs.tao.services.commons.ServiceResponse;
import ro.cs.tao.utils.DateUtils;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reports")
public class ReportController extends ControllerBase {

    @RequestMapping(value = "/l2/detail", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<ServiceResponse<?>> getL2DetailReport(@RequestParam("satellite") String satellite,
                                                                @RequestParam(name = "siteId", required = false) Short siteId,
                                                                @RequestParam(name = "fromDate", required = false) String from,
                                                                @RequestParam(name = "toDate", required = false) String to,
                                                                @RequestParam(name = "orbit", required = false) Integer orbitId) {
        try {
            final Satellite sat = EnumUtils.getEnumConstantByFriendlyName(Satellite.class, satellite);
            final DateFormat formatter = DateUtils.getFormatterAtUTC("yyyy-MM-dd");
            Date fromDate = from != null ? formatter.parse(from) : null;
            Date toDate = to != null ? formatter.parse(to) : null;
            ReportParamsBuilder builder = new ReportParamsBuilder();
            final Map<String, Object> params = builder.withParam("siteId", siteId)
                    .withParam("fromDate", fromDate)
                    .withParam("toDate", toDate)
                    .withParam("orbitId", orbitId).build();
            return prepareResult(ReportManager.executeReport(sat, ReportType.DETAIL, params));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @RequestMapping(value = "/l2/aggregate", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<ServiceResponse<?>> getL2AggregatedReport(@RequestParam("satellite") String satellite,
                                                                    @RequestParam(name = "siteId", required = false) Short siteId,
                                                                    @RequestParam(name = "fromDate", required = false) String from,
                                                                    @RequestParam(name = "toDate", required = false) String to,
                                                                    @RequestParam(name = "orbit", required = false) Integer orbitId) {
        try {
            final Satellite sat = EnumUtils.getEnumConstantByFriendlyName(Satellite.class, satellite);
            final DateFormat formatter = DateUtils.getFormatterAtUTC("yyyy-MM-dd");
            Date fromDate = from != null ? formatter.parse(from) : null;
            Date toDate = to != null ? formatter.parse(to) : null;
            ReportParamsBuilder builder = new ReportParamsBuilder();
            final Map<String, Object> params = builder.withParam("siteId", siteId)
                    .withParam("fromDate", fromDate)
                    .withParam("toDate", toDate)
                    .withParam("orbitId", orbitId).build();
            return prepareResult(ReportManager.executeReport(sat, ReportType.AGGREGATE, params));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @RequestMapping(value = "/orbit/list", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<ServiceResponse<?>> getOrbits(@RequestParam(name = "satellite") String satellite,
                                                        @RequestParam(name = "siteId", required = false) Short siteId) {
        try {
            final Satellite sat = EnumUtils.getEnumConstantByFriendlyName(Satellite.class, satellite);
            return prepareResult(DatabaseExecutor.getOrbits(sat, siteId));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @RequestMapping(value = "/orbit", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<ServiceResponse<?>> getOrbitReport(@RequestParam(name = "satellite") String satellite,
                                                             @RequestParam(name = "siteId", required = false) Short siteId,
                                                             @RequestParam(name = "fromDate", required = false) String from,
                                                             @RequestParam(name = "toDate", required = false) String to,
                                                             @RequestParam(name = "orbit", required = false) Integer orbitId,
                                                             @RequestParam(name = "sort", required = false, defaultValue = "desc") String sort) {
        try {
            final Satellite sat = EnumUtils.getEnumConstantByFriendlyName(Satellite.class, satellite);
            final DateFormat formatter = DateUtils.getFormatterAtUTC("yyyy-MM-dd");
            Date fromDate = from != null ? formatter.parse(from) : null;
            Date toDate = to != null ? formatter.parse(to) : null;
            ReportParamsBuilder builder = new ReportParamsBuilder();
            final Map<String, Object> params = builder.withParam("siteId", siteId)
                    .withParam("fromDate", fromDate)
                    .withParam("toDate", toDate)
                    .withParam("orbitId", orbitId).build();
            ReportResult reportResult = ReportManager.executeReport(sat, ReportType.ORBIT, params);
            final List<OrbitReportRecord> results = (List<OrbitReportRecord>) reportResult.getSeries();
            final String sortOrder = sort != null ? sort : "desc";
            if ("desc".equalsIgnoreCase(sortOrder))
                results.sort((o1, o2) -> o2.getCalendarDate().compareTo(o1.getCalendarDate()));
            reportResult.setSeries(results);
            return prepareResult(reportResult);
        } catch (Exception e) {
            return handleException(e);
        }
    }
}
