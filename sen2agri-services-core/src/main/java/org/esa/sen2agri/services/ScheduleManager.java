/*
 * Copyright (C) 2018 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.sen2agri.services;

import org.esa.sen2agri.commons.Commands;
import org.esa.sen2agri.commons.Config;
import org.esa.sen2agri.commons.ProcessingTopic;
import org.esa.sen2agri.db.ConfigurationKeys;
import org.esa.sen2agri.db.PersistenceManager;
import org.esa.sen2agri.entities.DataSourceConfiguration;
import org.esa.sen2agri.entities.Site;
import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.scheduling.DownloadJob;
import org.esa.sen2agri.scheduling.Job;
import org.esa.sen2agri.scheduling.JobDescriptor;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.messaging.Message;
import ro.cs.tao.messaging.Notifiable;
import ro.cs.tao.spi.ServiceRegistryManager;
import ro.cs.tao.utils.DateUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
@Component("scheduleManager")
public class ScheduleManager  extends Notifiable {

    private static Set<Class<? extends Job>> jobTemplates;
    private Logger logger;

    @Autowired
    private DownloadService service;
    @Autowired
    private PersistenceManager persistenceManager;

    private Scheduler scheduler;

    private Set<String> registeredJobs;

    @PostConstruct
    public void initialize() throws SchedulerException {
        logger = Logger.getLogger(ScheduleManager.class.getName());
        Config.setPersistenceManager(persistenceManager);
        Config.setDownloadService(service);
        service.setProductStatusListener(new ProductDownloadListener(persistenceManager));
        scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.getListenerManager().addTriggerListener(new TriggerListener() {
            private final Map<JobKey, Date> lastFireTimes = new HashMap<>();
            @Override
            public String getName() { return "prevent-duplicates"; }

            @Override
            public void triggerFired(Trigger trigger, JobExecutionContext context) {
            }

            @Override
            public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
                final Date fireTime = context.getScheduledFireTime();
                final JobKey jobKey = trigger.getJobKey();
                Date lastFireTime = lastFireTimes.get(jobKey);
                if (fireTime.equals(lastFireTime))  {
                    return true;
                }
                lastFireTimes.put(jobKey, fireTime);
                return false;
            }

            @Override
            public void triggerMisfired(Trigger trigger) {
                logger.warning(String.format("Trigger '%s' misfired!", trigger.getKey()));
            }

            @Override
            public void triggerComplete(Trigger trigger, JobExecutionContext context, Trigger.CompletedExecutionInstruction triggerInstructionCode) {
                logger.finest(String.format("Trigger '%s' completed with code '%s'", trigger.getKey(), triggerInstructionCode.name()));
            }
        });
        scheduler.start();
        List<Site> enabledSites = getEnabledSites();
        logger.config("Enabled sites: " +
                            enabledSites.stream().map(Site::getShortName).collect(Collectors.joining(",")));
        Set<Job> jobMocks = ServiceRegistryManager.getInstance().getServiceRegistry(Job.class).getServices();
        logger.config("Found scheduled job types: " + jobMocks.stream().map(Job::groupName).collect(Collectors.joining(",")));
        jobTemplates = jobMocks.stream().map(Job::getClass).collect(Collectors.toSet());
        registeredJobs = new HashSet<>();
        for (Class<? extends Job> jobTemplate : jobTemplates) {
            try {
                schedule(jobTemplate, enabledSites, new HashSet<>());
            } catch (IllegalAccessException | InstantiationException e) {
               logger.severe(e.getMessage());
            }
        }
        subscribe(ProcessingTopic.COMMAND.value());
    }

    @PreDestroy
    public void shutdown() throws SchedulerException {
        scheduler.shutdown();
    }

    public void refresh() {
        Config.reload();
        logger.fine("Configuration refreshed from database ");
        List<Site> enabledSites = getEnabledSites();
        if (jobTemplates == null) {
            jobTemplates = ServiceRegistryManager.getInstance().getServiceRegistry(Job.class).getServices().stream().map(Job::getClass).collect(Collectors.toSet());
        }
        final Set<String> executingJobs = getExecutingJobs();
        for (Class<? extends Job> jobTemplate : jobTemplates) {
            try {
                schedule(jobTemplate, enabledSites, executingJobs);
            } catch (IllegalAccessException | InstantiationException e) {
                logger.severe(e.getMessage());
            }
        }
        try {
            logger.info(String.format("Running scheduled jobs: %d", scheduler.getCurrentlyExecutingJobs().size()));
        } catch (SchedulerException e) {
            logger.severe(e.getMessage());
        }
    }

    @Override
    protected void onMessageReceived(Message message) {
        String command = message.getMessage();
        if (Commands.DOWNLOADER_FORCE_START.equals(command)) {
            String job = message.getItem("job");
            String siteId = message.getItem("siteId");
            String satelliteId = message.getItem("satelliteId");
            if (job != null && siteId != null && satelliteId != null) {
                Site site = persistenceManager.getSiteById(Short.parseShort(siteId));
                if (site == null) {
                    logger.warning(String.format("Received command '%s' for non-existent site id '%s'. Will do nothing.",
                                                 command, siteId));
                    return;
                }
                Job mockJob = ServiceRegistryManager.getInstance().getServiceRegistry(Job.class).getServices()
                                                    .stream().filter(j -> j.groupName().toLowerCase().equals(job))
                                                    .findFirst().orElse(null);
                if (mockJob == null) {
                    logger.warning(String.format("Received command '%s' for non-existent job type '%s'. Will do nothing.",
                                                 command, job));
                    return;
                }
                final Set<String> executingJobs = getExecutingJobs();
                try {
                    schedule(mockJob.getClass(), new ArrayList<Site>() {{ add(site); }}, executingJobs);
                } catch (Exception e) {
                    logger.severe(e.getMessage());
                }
            }
        }
    }

    private List<Site> getEnabledSites() {
        final List<Site> enabledSites = persistenceManager.getEnabledSites();
        enabledSites.removeIf(s -> s.getExtent() == null || Polygon2D.fromWKT(s.getExtent()).getNumPoints() == 0);
        short forcedSiteId = Short.parseShort(Config.getProperty(getClass().getSimpleName() + ".forced.site", "0"));
        if (forcedSiteId != 0) {
            logger.config(String.format("This instance of services is forced to process only the site with id '%d'",
                                      forcedSiteId));
            enabledSites.removeIf(s -> s.getId() != forcedSiteId);
        }
        return enabledSites;
    }

    public Map<JobKey, List<TriggerKey>> getExecutingJobsWithTriggers() {
        Map<JobKey, List<TriggerKey>> jobTriggers = new HashMap<>();
        try {
            final List<JobExecutionContext> executing = scheduler.getCurrentlyExecutingJobs();
            for (JobExecutionContext context : executing) {
                final JobKey key = context.getJobDetail().getKey();
                jobTriggers.put(key, new ArrayList<>());
                final TriggerKey currentTrigger = context.getTrigger().getKey();
                jobTriggers.get(key).add(currentTrigger);
                final List<? extends Trigger> triggers = scheduler.getTriggersOfJob(key);
                for (Trigger trigger : triggers) {
                    if (!trigger.getKey().equals(currentTrigger)) {
                        jobTriggers.get(key).add(trigger.getKey());
                    }
                }
            }
        } catch (SchedulerException e) {
            logger.severe(e.getMessage());
        }
        return jobTriggers;
    }

    private Set<String> getExecutingJobs() {
        final Set<String> executingJobs = new HashSet<>();
        try {
            final List<JobExecutionContext> executing = scheduler.getCurrentlyExecutingJobs();
            if (executing != null) {
                StringBuilder builder = new StringBuilder();
                builder.append("Jobs in progress: ");
                if (executing.size() == 0) {
                    builder.append("none");
                }
                for (JobExecutionContext context : executing) {
                    final JobKey key = context.getJobDetail().getKey();
                    builder.append(key.getName()).append(" [next run: ").append(DateUtils.getFormatterAtLocal("yyyy-MM-dd HH:mm:ss").format(context.getNextFireTime())).append("]; ");
                    if (executingJobs.contains(key.getName())) {
                        logger.warning(String.format("Found executing job with same key: %s. The oldest one will be removed", key.getName()));
                        List<JobExecutionContext> oldJobs = executing.stream().filter(c -> c.getNextFireTime().compareTo(context.getNextFireTime()) <= 0).collect(Collectors.toList());
                        for (JobExecutionContext oldContext : oldJobs) {
                            final JobKey previousKey = oldContext.getJobDetail().getKey();
                            try {
                                scheduler.deleteJob(previousKey);
                                logger.info(String.format("Job with key %s removed", previousKey));
                            } catch (SchedulerException ex) {
                                logger.warning(String.format("Cannot remove job %s. Reason: %s", previousKey, ex.getMessage()));
                            }
                        }
                    }
                    executingJobs.add(key.getName());
                }
                logger.fine(builder.toString());
                builder.setLength(0);
            }
        } catch (SchedulerException e) {
            logger.severe(e.getMessage());
        }
        return executingJobs;
    }

    private void schedule(Class<? extends Job> jobTemplate, List<Site> sites, Set<String> executingJobs) throws IllegalAccessException, InstantiationException {
        final Job mockJob = jobTemplate.newInstance();
        final String jobKey = mockJob.configKey();
        if (!Config.isFeatureEnabled((short) 0, jobKey)) {
            logger.config(String.format("Setting %s is disabled", jobKey));
            return;
        }
        String jobId;
        switch (mockJob.jobType()) {
            case SINGLE_INSTANCE:
                jobId = mockJob.getId();
                if (!executingJobs.contains(jobId)) {
                    schedule(jobId, jobTemplate);
                } else {
                    logger.warning(String.format("A job with ID '%s' is already scheduled", jobId));
                }
                break;
            case SITE:
                for (Site site : sites) {
                    jobId = mockJob.groupName() + "-" + site.getShortName();
                    if (!executingJobs.contains(jobId)) {
                        schedule(jobId, jobTemplate, site);
                    } else {
                        logger.warning(String.format("A job with ID '%s' is already scheduled", jobId));
                    }
                }
                break;
            case SITE_SATELLITE:
                final Map<Satellite, List<DataSourceConfiguration>> queryConfigurations = Config.getQueryConfigurations();
                final Map<Satellite, List<DataSourceConfiguration>> downloadConfigurations = Config.getDownloadConfigurations();
                if (downloadConfigurations.size() == 0) {
                    logger.severe(String.format("No datasource is configured for product retrieval. Check the [datasource] table."));
                    return;
                }
                for (Site site : sites) {
                    schedule(mockJob, site, executingJobs, queryConfigurations, downloadConfigurations);
                }
                break;
        }
    }

    private void schedule(final Job jobTemplate, final Site site, final Set<String> executingJobs,
                          final Map<Satellite, List<DataSourceConfiguration>> queryConfigurations,
                          final Map<Satellite, List<DataSourceConfiguration>> downloadConfigurations) {
        for (Satellite s : downloadConfigurations.keySet()) {
            final Set<Satellite> filter = jobTemplate.getSatelliteFilter();
            if (filter != null && !filter.contains(s)) {
                logger.finest(String.format("Job '%s' is not intended for sensor '%s'", jobTemplate.groupName(), s.friendlyName()));
                continue;
            }
            final String sensorCode = s.friendlyName().toLowerCase();
            if (Config.isFeatureEnabled((short) 0, String.format(ConfigurationKeys.SENSOR_STATE, sensorCode)) ||
                    Config.isFeatureEnabled((short) 0, String.format(ConfigurationKeys.DOWNLOADER_SENSOR_ENABLED, sensorCode))) {
                final String key = site.getShortName() + "-" + s.name();
                try {
                    final Job job = jobTemplate.getClass().newInstance();
                    job.setId(job.groupName() + "-" + sensorCode + "-" + site.getShortName());
                    if (!queryConfigurations.containsKey(s)) {
                        throw new SchedulerException(String.format("No query configuration found for satellite %s",
                                                                   s.friendlyName()));
                    }
                    final DataSourceConfiguration queryConfig = queryConfigurations.get(s).stream()
                            .filter(ds -> ds.getSiteId() == null || ds.getSiteId().equals(site.getId()))
                            .findFirst().orElse(null);
                    if (queryConfig == null) {
                        throw new SchedulerException(String.format("Cannot find query configuration for site %s",
                                                                   site.getShortName()));
                    }
                    if (!downloadConfigurations.containsKey(s)) {
                        throw new SchedulerException(String.format("No download configuration found for satellite %s",
                                                                   s.friendlyName()));
                    }
                    final DataSourceConfiguration downloadConfig;
                    final List<DataSourceConfiguration> configurations = downloadConfigurations.get(s);
                    if (configurations.size() > 1) {
                        downloadConfig = configurations.stream()
                                .filter(ds -> (ds.getSiteId() == null || ds.getSiteId().equals(site.getId())) && ds.getSecondaryDatasourceId() != null)
                                .findFirst().orElse(configurations.get(0));
                    } else if (configurations.size() == 1) {
                        downloadConfig = configurations.get(0);
                    } else {
                        downloadConfig = null;
                    }
                    if (downloadConfig == null) {
                        throw new SchedulerException(String.format("Cannot find download configuration for site %s",
                                                                   site.getShortName()));
                    }
                    JobDetail jobDetail = job.buildDetail(site, queryConfig, downloadConfig);
                    if (!executingJobs.contains(job.getId())) {
                        schedule(job, site, s, queryConfig, downloadConfig);
                    } else {
                        logger.warning(String.format("A job with key '%s' is already scheduled", jobDetail.getKey()));
                    }
                } catch (IllegalAccessException | InstantiationException | SchedulerException e) {
                    logger.severe(String.format("Failed to schedule job '%s'. Reason: %s", key, e.getMessage()));
                }
            } else {
                logger.config(String.format("Sensor '%s' is globally disabled", s.name()));
            }
        }
    }

    private void schedule(final Job job, Site site, Satellite s, DataSourceConfiguration queryConfig, DataSourceConfiguration downloadConfig) {
        String sensorCode = s.friendlyName().toLowerCase();
        JobDetail jobDetail;
        boolean canSchedule = Config.isFeatureEnabled(site.getId(), String.format(ConfigurationKeys.SENSOR_STATE, sensorCode));
        if (!canSchedule) {
            logger.config(String.format("Sensor '%s' is disabled for site %s", sensorCode, site.getShortName()));
            return;
        }
        canSchedule = !(job instanceof DownloadJob) || Config.isFeatureEnabled(site.getId(), String.format(ConfigurationKeys.DOWNLOADER_SENSOR_ENABLED, sensorCode));
        if (!canSchedule) {
            logger.config(String.format("Download is disabled for site %s and sensor '%s'", site.getShortName(), sensorCode));
            return;
        }
        try {
            final JobDescriptor descriptor = job.createDescriptor(downloadConfig.getRetryInterval());
            jobDetail = job.buildDetail(site, queryConfig, downloadConfig);
            final Trigger trigger;
            if (job instanceof DownloadJob) {
                trigger = descriptor.buildTrigger(jobDetail.getJobDataMap());
            } else {
                trigger = descriptor.buildTrigger();
            }
            final JobKey key = jobDetail.getKey();
            final String jobKey = key.getGroup() + "-" + key.getName();
            if (!registeredJobs.contains(jobKey)) {
                scheduler.scheduleJob(jobDetail, trigger);
                registeredJobs.add(jobKey);
                logger.info(String.format("Scheduled new job '%s' (next run: %s, repeat after %d minutes)",
                                          key.getName(),
                                          descriptor.getFireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                                          descriptor.getRepeatInterval()));
            } else {
                JobDetail previousJobDetail = scheduler.getJobDetail(key);
                if (previousJobDetail == null) {
                    throw new SchedulerException(String.format("Job %s seems to be scheduled, but its detail is missing", key));
                }
                TriggerKey triggerKey = TriggerKey.triggerKey(key.getName(), key.getGroup());
                SimpleTrigger oldTrigger = (SimpleTrigger) scheduler.getTrigger(triggerKey);
                if (oldTrigger == null) {
                    throw new SchedulerException(String.format("Trigger %s not found for already scheduled job %s", triggerKey, key));
                }
                final JobDataMap jobDataMap = previousJobDetail.getJobDataMap();
                if (jobDataMap == null) {
                    throw new SchedulerException(String.format("JobDataMap is missing for already scheduled job %s", key));
                }
                final Site oldSite = (Site) jobDataMap.get("site");
                final long oldRepeatInterval = oldTrigger.getRepeatInterval() / 60000L;
                if (oldSite != null && !site.equals(oldSite)) {
                    logger.fine(String.format("Job %s: old site=%s, new site=%s", key, oldSite.getId(), site.getId()));
                    try {
                        if (!scheduler.deleteJob(previousJobDetail.getKey())) {
                            throw new SchedulerException("Cannot delete");
                        }
                        logger.info(String.format("Deleted previous job '%s'", previousJobDetail.getKey()));
                        scheduler.scheduleJob(jobDetail, trigger);
                        logger.info(String.format("Scheduled new job '%s' (next run: %s)",
                                                  key, DateUtils.getFormatterAtLocal("yyyy-MM-dd'T'HH:mm:ss").format(trigger.getNextFireTime())));
                    } catch (SchedulerException e1) {
                        logger.warning(String.format("Unable to delete job '%s'", jobDetail.getKey()));
                    }
                } else if (oldTrigger.getJobDataMap ().containsKey("downloadConfig") &&
                            ((long) downloadConfig.getRetryInterval()) != oldRepeatInterval) {
                    logger.fine(String.format("Old interval: %d min; new interval: %d min",
                                              oldRepeatInterval / 60000, downloadConfig.getRetryInterval()));
                    if (!scheduler.deleteJob(previousJobDetail.getKey())) {
                        throw new SchedulerException("Cannot delete");
                    }
                    logger.info(String.format("Deleted previous job '%s'", previousJobDetail.getKey()));
                    scheduler.scheduleJob(jobDetail, trigger);
                    logger.info(String.format("Rescheduled job '%s' (next run: %s, repeat after %d minutes)",
                                              jobDetail.getKey(),
                                              descriptor.getFireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                                              descriptor.getRepeatInterval()));
                }
            }
        } catch (SchedulerException e) {
            logger.severe(String.format("Failed to schedule job '%s-%s'. Reason: %s",
                                        job.getId(), job.groupName(), e.getMessage()));
        }
    }

    private void schedule(final String jobId, Class<? extends Job> jobTemplate, Site site) {
        JobDetail jobDetail;
        if (!site.isEnabled()) {
            logger.config(String.format("Site %s is disabled", site.getShortName()));
            return;
        }
        Job job = null;
        try {
            job = jobTemplate.newInstance();
            job.setId(jobId);
            final JobDescriptor descriptor = job.createDescriptor();
            jobDetail = job.buildDetail(site);
            final Trigger trigger;
            if (job instanceof DownloadJob) {
                trigger = descriptor.buildTrigger(jobDetail.getJobDataMap());
            } else {
                trigger = descriptor.buildTrigger();
            }
            final JobKey key = jobDetail.getKey();
            final String jobKey = key.getGroup() + "-" + key.getName();
            if (!registeredJobs.contains(jobKey)) {
                scheduler.scheduleJob(jobDetail, trigger);
                registeredJobs.add(jobKey);
                logger.info(String.format("Scheduled new job '%s' (next run: %s, repeat after %d minutes)",
                        key,
                        descriptor.getFireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                        descriptor.getRepeatInterval()));
            } else {
                JobDetail previousJobDetail = scheduler.getJobDetail(key);
                if (previousJobDetail == null) {
                    throw new SchedulerException(String.format("Job %s seems to be scheduled, but its detail is missing", key));
                }
                TriggerKey triggerKey = TriggerKey.triggerKey(key.getName(), key.getGroup());
                SimpleTrigger oldTrigger = (SimpleTrigger) scheduler.getTrigger(triggerKey);
                if (oldTrigger == null) {
                    throw new SchedulerException(String.format("Trigger %s not found for already scheduled job %s", triggerKey, key));
                }
                final JobDataMap jobDataMap = previousJobDetail.getJobDataMap();
                if (jobDataMap == null) {
                    throw new SchedulerException(String.format("JobDataMap is missing for already scheduled job %s", key));
                }
                final Site oldSite = (Site) jobDataMap.get("site");
                final long oldRepeatInterval = oldTrigger.getRepeatInterval() / 60000L;
                if (oldSite != null && !site.equals(oldSite)) {
                    logger.fine(String.format("Job %s: old site=%s, new site=%s", key, oldSite.getId(), site.getId()));
                    try {
                        if (!scheduler.deleteJob(previousJobDetail.getKey())) {
                            throw new SchedulerException("Cannot delete");
                        }
                        logger.info(String.format("Deleted previous job '%s'", previousJobDetail.getKey()));
                        scheduler.scheduleJob(jobDetail, trigger);
                        logger.info(String.format("Scheduled new job '%s' (next run: %s)",
                                key, DateUtils.getFormatterAtLocal("yyyy-MM-dd'T'HH:mm:ss").format(trigger.getNextFireTime())));
                    } catch (SchedulerException e1) {
                        logger.warning(String.format("Unable to delete job '%s'", jobDetail.getKey()));
                    }
                }
            }
        } catch (InstantiationException | IllegalAccessException e) {
            logger.severe(String.format("Failed to instantiate %s. Reason: %s", jobTemplate.getName(), e.getMessage()));
        } catch (SchedulerException e) {
            logger.severe(String.format("Failed to schedule job '%s-%s'. Reason: %s",
                    job.getId(), job.groupName(), e.getMessage()));
        }
    }

    private void schedule(final String jobId, final Class<? extends Job> jobTemplate) {
        JobDetail jobDetail;
        Job job = null;
        try {
            job = jobTemplate.newInstance();
            job.setId(jobId);
            final JobDescriptor descriptor = job.createDescriptor();
            jobDetail = job.buildDetail();
            final Trigger trigger;
            if (job instanceof DownloadJob) {
                trigger = descriptor.buildTrigger(jobDetail.getJobDataMap());
            } else {
                trigger = descriptor.buildTrigger();
            }
            final JobKey key = jobDetail.getKey();
            final String jobKey = key.getGroup() + "-" + key.getName();
            if (!registeredJobs.contains(jobKey)) {
                scheduler.scheduleJob(jobDetail, trigger);
                registeredJobs.add(jobKey);
                logger.info(String.format("Scheduled new job '%s' (next run: %s, repeat after %d minutes)",
                                          key,
                                          descriptor.getFireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                                          descriptor.getRepeatInterval()));
            } else {
                JobDetail previousJobDetail = scheduler.getJobDetail(key);
                if (previousJobDetail == null) {
                    throw new SchedulerException(String.format("Job %s seems to be scheduled, but its detail is missing", key));
                }
                TriggerKey triggerKey = TriggerKey.triggerKey(key.getName(), key.getGroup());
                SimpleTrigger oldTrigger = (SimpleTrigger) scheduler.getTrigger(triggerKey);
                if (oldTrigger == null) {
                    throw new SchedulerException(String.format("Trigger %s not found for already scheduled job %s", triggerKey, key));
                }
                final JobDataMap jobDataMap = previousJobDetail.getJobDataMap();
                if (jobDataMap == null) {
                    throw new SchedulerException(String.format("JobDataMap is missing for already scheduled job %s", key));
                }
                if (descriptor.getRepeatInterval() != oldTrigger.getRepeatInterval()) {
                    logger.fine(String.format("Old interval: %d min; new interval: %d min",
                                              oldTrigger.getRepeatInterval(), descriptor.getRepeatInterval()));
                    if (!scheduler.deleteJob(previousJobDetail.getKey())) {
                        throw new SchedulerException("Cannot delete");
                    }
                    logger.fine(String.format("Deleted previous job '%s'", previousJobDetail.getKey()));
                    scheduler.scheduleJob(jobDetail, trigger);
                    logger.info(String.format("Rescheduled job '%s' (next run: %s, repeat after %d minutes)",
                                              jobDetail.getKey(),
                                              descriptor.getFireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                                              descriptor.getRepeatInterval()));
                }
            }
        } catch (InstantiationException | IllegalAccessException e) {
            logger.severe(String.format("Failed to instantiate %s. Reason: %s", jobTemplate.getName(), e.getMessage()));
        } catch (SchedulerException e) {
            logger.severe(String.format("Failed to schedule job '%s-%s'. Reason: %s", job.getId(), job.groupName(), e.getMessage()));
        }
    }
}
