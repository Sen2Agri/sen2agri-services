/*
 *
 *  * Copyright (C) 2019 CS ROMANIA
 *  *
 *  * This program is free software; you can redistribute it and/or modify it
 *  * under the terms of the GNU General Public License as published by the Free
 *  * Software Foundation; either version 3 of the License, or (at your option)
 *  * any later version.
 *  * This program is distributed in the hope that it will be useful, but WITHOUT
 *  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 *  * more details.
 *  *
 *  * You should have received a copy of the GNU General Public License along
 *  * with this program; if not, see http://www.gnu.org/licenses/
 *
 */

package org.esa.sen2agri.scheduling;

import org.esa.sen2agri.commons.Config;
import org.esa.sen2agri.reports.spi.Report;
import org.esa.sen2agri.services.SiteHelper;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.time.LocalDateTime;
import java.util.Set;

public class ReportJob extends AbstractJob {

    private SiteHelper siteHelper;

    public ReportJob() {
        super();
        this.id = "Reports";
    }

    @Override
    public String configKey() { return ConfigurationKeys.REPORTS_ENABLED; }

    @Override
    public String groupName() { return "Reports"; }

    @Override
    public JobType jobType() { return JobType.SINGLE_INSTANCE; }

    @Override
    public JobDescriptor createDescriptor() {
        int hours = Integer.parseInt(Config.getSetting(ConfigurationKeys.REPORTS_INTERVAL_HOURS, "24"));
        return new JobDescriptor()
                .setName(getId())
                .setGroup(groupName())
                .setFireTime(LocalDateTime.now().plusMinutes(1))
                .setRate(hours * 60);
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        super.execute(jobExecutionContext);
        SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);
    }

    @Override
    protected void executeImpl(JobDataMap dataMap) {
        if (siteHelper == null) {
            siteHelper = new SiteHelper();
            siteHelper.setPersistenceManager(persistenceManager);
        }
        final Set<Report> reports = ServiceRegistryManager.getInstance().getServiceRegistry(Report.class).getServices();
        for (Report<?> report : reports) {
            try {
                int rows = report.executeRawQuery();
                if (rows >= 0) {
                    logger.fine(String.format("Report for S1 pre-processing added new %d rows", rows));
                }
            } catch (Exception e) {
                logger.severe(String.format("Error during %s report extraction: %s",report.name(), e.getMessage()));
            }
        }
    }
}
