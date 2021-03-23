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
package org.esa.sen2agri.scheduling;

import org.esa.sen2agri.commons.Commands;
import org.esa.sen2agri.commons.Config;
import org.esa.sen2agri.commons.Constants;
import org.esa.sen2agri.commons.ProcessingTopic;
import org.esa.sen2agri.db.ConfigurationKeys;
import org.esa.sen2agri.entities.*;
import org.esa.sen2agri.entities.enums.OrbitType;
import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.entities.enums.Status;
import org.esa.sen2agri.services.internal.QueryHandler;
import org.esa.sen2agri.web.beans.Query;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.quartz.JobDataMap;
import ro.cs.tao.EnumUtils;
import ro.cs.tao.Tuple;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.eodata.EOData;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.util.TileExtent;
import ro.cs.tao.messaging.Message;
import ro.cs.tao.products.landsat.Landsat8TileExtent;
import ro.cs.tao.products.sentinels.Sentinel2TileExtent;
import ro.cs.tao.serialization.GeometryAdapter;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.NetUtils;
import ro.cs.tao.utils.Triple;

import java.awt.geom.Path2D;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class LookupJob extends DownloadJob {

    private static final Map<Tuple<String, String>, Integer> runningJobs = Collections.synchronizedMap(new HashMap<>());
    private static final Object sharedLock = new Object();

    public LookupJob() {
        super();
    }

    @Override
    public String configKey() {
        return ConfigurationKeys.SCHEDULED_LOOKUP_ENABLED;
    }

    @Override
    public String groupName() { return "Lookup"; }

    @Override
    public JobType jobType() { return JobType.SITE_SATELLITE; }

    @Override
    protected void onMessageReceived(Message message) {
        String jobType = message.getItem("job");
        if (jobType != null && !jobType.equals(groupName().toLowerCase())) {
            return;
        }
        String command = message.getMessage();
        logger.fine(String.format("%s:%d received command [%s]",
                                  LookupJob.class.getSimpleName(), this.hashCode(), command));
        if (Commands.DOWNLOADER_FORCE_START.equals(command)) {
            String siteId = message.getItem("siteId");
            if (siteId != null) {
                Site site = persistenceManager.getSiteById(Short.parseShort(siteId));
                String satelliteId = message.getItem("satelliteId");
                if (satelliteId != null) {
                    Satellite satellite = EnumUtils.getEnumConstantByValue(Satellite.class, Short.parseShort(satelliteId));
                    if (satellite != null) {
                        Tuple<String, String> key = new Tuple<>(site.getName(), satellite.friendlyName());
                        runningJobs.remove(key);
                        logger.fine(String.format("Job with key %s removed", key));
                    }
                } else {
                    Set<Tuple<String, String>> keySet = runningJobs.keySet();
                    for (Tuple<String, String> key : keySet) {
                        if (key.getKeyOne().equals(site.getName())) {
                            runningJobs.remove(key);
                            logger.fine(String.format("Job with key %s removed", key));
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void executeImpl(JobDataMap dataMap) {
        final Site site =  (Site) dataMap.get("site");
        if (!site.isEnabled()) {
            return;
        }
        DataSourceConfiguration queryConfig = (DataSourceConfiguration) dataMap.get("queryConfig");
        // make sure the configuration reflects the last state from the database
        queryConfig = Config.refreshDbConfiguration(queryConfig);
        dataMap.put("queryConfig", queryConfig);
        DataSourceConfiguration downloadConfig = (DataSourceConfiguration) dataMap.get("downloadConfig");
        downloadConfig = Config.refreshDbConfiguration(downloadConfig);
        dataMap.put("downloadConfig", downloadConfig);
        final Satellite satellite = queryConfig.getSatellite();
        final Tuple<String, String> key = new Tuple<>(site.getName(), satellite.friendlyName());
        final Integer previousCount = runningJobs.get(key);
        if (previousCount != null) {
            if (previousCount > 0) {
                logger.warning(String.format("A job for {site:%s,satellite:%s} is already running [%d products remaining]",
                                             site.getName(), satellite.friendlyName(), previousCount));
                return;
            } else {
                runningJobs.remove(key);
            }
        }
        final boolean downloadEnabled = Config.isFeatureEnabled(site.getId(), ConfigurationKeys.DOWNLOADER_ENABLED);
        final boolean sensorDownloadEnabled = Config.isFeatureEnabled(site.getId(),
                                                                      String.format(ConfigurationKeys.DOWNLOADER_SENSOR_ENABLED,
                                                                                    satellite.friendlyName().toLowerCase()));
        if (!(downloadEnabled && sensorDownloadEnabled)) {
            logger.config(String.format(MESSAGE, site.getShortName(), satellite.name(), "Download disabled"));
            cleanupJob(site, satellite);
            return;
        }
        final short siteId = site.getId();
        final List<Season> seasons = persistenceManager.getEnabledSeasons(siteId);
        try {
            if (seasons != null && seasons.size() > 0) {
                seasons.sort(Comparator.comparing(Season::getStartDate));
                logger.fine(String.format(MESSAGE, site.getShortName(), satellite.name(),
                                          "Seasons defined: " +
                                                  seasons.stream()
                                                          .map(Season::toString)
                                                          .collect(Collectors.joining(";"))));
                final LocalDateTime startDate = getStartDate(satellite, site, seasons);
                final LocalDateTime endDate = getEndDate(seasons);
                logger.fine(String.format(MESSAGE, site.getShortName(), satellite.name(),
                                          String.format("Using start date: %s and end date: %s", startDate, endDate)));
                final String downloadPath = Paths.get(queryConfig.getDownloadPath(), site.getShortName()).toString();
                if (LocalDateTime.now().compareTo(startDate) >= 0) {
                    if (endDate.compareTo(startDate) >= 0) {
                        logger.fine(String.format(MESSAGE, site.getShortName(), satellite.name(),
                                                  String.format("Lookup for new products in range %s - %s",
                                                                startDate.format(DateTimeFormatter.ofPattern(Constants.FULL_DATE_FORMAT)),
                                                                endDate.format(DateTimeFormatter.ofPattern(Constants.FULL_DATE_FORMAT)))));
                        lookupAndDownload(site, startDate, endDate, downloadPath, queryConfig, downloadConfig);
                    } else {
                        logger.info(String.format(MESSAGE, site.getName(), satellite.name(),
                                                  "No products to download (endDate past startDate)"));
                    }
                } else {
                    logger.warning(String.format(MESSAGE, site.getName(), satellite.name(), "Season not started"));
                }
            } else {
                logger.warning(String.format(MESSAGE, site.getName(), satellite.name(),
                                             "No season defined"));
            }
        } catch (Throwable e) {
            cleanupJob(site, satellite);
        }
    }

    private boolean useESAL2A(Site site) {
        return Boolean.parseBoolean(Config.getSetting(site.getId(), ConfigurationKeys.DOWNLOADER_USE_ESA_L2A, "false"));
    }

    protected LocalDateTime getStartDate(Satellite satellite, Site site, List<Season> seasons) {
        LocalDateTime minStartDate = seasons.get(0).getStartDate().atStartOfDay();
        int downloaderStartOffset = Config.getAsInteger(ConfigurationKeys.DOWNLOADER_START_OFFSET, 0);
        if (satellite == Satellite.Sentinel1) {
            // For S1, if L2S1 v2 processor is used, the MTF interval should be considered instead of
            // the regular offset of 6 days
            int s1Offset = Math.max(Config.getAsInteger(ConfigurationKeys.S1_DOWNLOAD_OFFSET, 6),
                                    Config.getAsInteger(ConfigurationKeys.S1_MTF_INTERVAL, 0));
            minStartDate = minStartDate.minusDays(s1Offset);
        } else {
            if (downloaderStartOffset > 0) {
                minStartDate = minStartDate.minusMonths(downloaderStartOffset);
            }
        }
        LocalDateTime startDate;
        LocalDateTime lastDate = null;
        // Only use the last download date if the flag 'downloader.%s.forcestart' is set to 'false' or not present
        if (!Config.getAsBoolean(site.getId(),
                                 String.format(ConfigurationKeys.DOWNLOADER_SENSOR_FORCE_START, satellite.friendlyName()),
                                 false)) {
            lastDate = persistenceManager.getLastDownloadDate(site.getId(), satellite.value());
        }
        int queryBuffer = Config.getAsInteger(String.format(ConfigurationKeys.DOWNLOADER_SENSOR_QUERY_BUFFER, satellite.friendlyName()), 0);
        startDate = lastDate == null ? minStartDate : lastDate.plusDays(1).minusDays(queryBuffer).toLocalDate().atStartOfDay();
        //startDate = lastDate == null ? minStartDate : lastDate.plusDays(1).toLocalDate().atStartOfDay();
        return startDate;
    }

    protected LocalDateTime getEndDate(List<Season> seasons) {
        LocalDateTime endDate = seasons.get(seasons.size() - 1).getEndDate().atStartOfDay().plusDays(1).minusSeconds(1);
        if (LocalDateTime.now().compareTo(endDate) < 0) {
            endDate = LocalDateTime.now();
        }
        return endDate;
    }

    private void lookupAndDownload(Site site, LocalDateTime start, LocalDateTime end, String path,
                                   DataSourceConfiguration queryConfiguration, DataSourceConfiguration downloadConfiguration) {
        final Query query = new Query();
        query.setUser(queryConfiguration.getUser());
        query.setPassword(queryConfiguration.getPassword());
        final Map<String, Object> params = new HashMap<>();
        final Satellite satellite = queryConfiguration.getSatellite();
        final Set<String> tiles = persistenceManager.getSiteTiles(site, downloadConfiguration.getSatellite());
        final String dataSourceName = queryConfiguration.getDataSourceName();
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.FULL_DATE_FORMAT);
        final String startDate = start.format(formatter);
        final String endDate = end.format(formatter);
        if ("Scientific Data Hub".equals(dataSourceName)) {
            final String[] dateInterval = {startDate, endDate};
            params.put(CommonParameterNames.START_DATE, dateInterval);
            params.put(CommonParameterNames.END_DATE, dateInterval);
        } else {
            params.put(CommonParameterNames.START_DATE, startDate);
            params.put(CommonParameterNames.END_DATE, endDate);
        }
        int polygons;
        try {
            polygons = new WKTReader().read(site.getExtent()).getNumGeometries();
        } catch (ParseException e) {
            polygons = 1;
            ExceptionUtils.getStackTrace(logger, e);
        }
        params.put(CommonParameterNames.FOOTPRINT, Polygon2D.fromWKT(site.getExtent()));
        if (satellite == Satellite.Sentinel2) {
            params.put(CommonParameterNames.PRODUCT_TYPE,
                       //useESAL2A(site) ? Constants.S2L2A_PRODUCT_TYPE : Constants.S2L1C_PRODUCT_TYPE);
                    // accommodate different values for product types from different providers
                    useESAL2A(site) ?
                            ProductTypes.Sentinel2.getValue(Constants.S2L2A_PRODUCT_TYPE, dataSourceName) :
                            ProductTypes.Sentinel2.getValue(Constants.S2L1C_PRODUCT_TYPE, dataSourceName));
        }
        if (tiles != null && tiles.size() > 0) {
            int initialSize = tiles.size();
            logger.finest(String.format("Validating %s tile filter for site %s", satellite.friendlyName(), site.getShortName()));
            validateTiles(site, tiles, satellite);
            logger.finest(String.format("%s %s tiles were discarded. Filter has %d tiles.",
                                        tiles.size() == initialSize ? "No" : initialSize - tiles.size(),
                                        satellite.friendlyName(),
                                        tiles.size()));
            final String tileList;
            if (tiles.size() == 1) {
                tileList = tiles.stream().findFirst().get();
            } else {
                tileList = "[" + String.join(",", tiles) + "]";
            }
            params.put(CommonParameterNames.TILE, tileList);
        }
        final List<Parameter> specificParameters = queryConfiguration.getSpecificParameters();
        boolean hasChanged = false;
        if (specificParameters != null) {
            for (Parameter p : specificParameters) {
                try {
                    final Object newValue = params.get(p.getName());
                    if (newValue != null && !p.typedValue().equals(newValue)) {
                        logger.config(String.format("Parameter %s was set by another setting. Data source [%s-%s] will be updated with the new value ['%s'->'%s']",
                                                  p.getName(), dataSourceName, dataSourceName,
                                                  p.getValue(), newValue));
                        p.setValue(String.valueOf(newValue));
                        hasChanged = true;
                    } else {
                        params.put(p.getName(), p.typedValue());
                    }
                } catch (Exception e) {
                    logger.warning(String.format(MESSAGE, site.getName(), satellite.name(),
                                                 String.format("Incorrect typed value for parameter [%s] : expected '%s', found '%s'",
                                                               p.getName(), p.getType(), p.getValue())));
                }
            }
            if (hasChanged) {
                persistenceManager.save(queryConfiguration);
            }
        }
        query.setValues(params);
        try {
            final Path failedQueriesPath = Paths.get(path).resolve("failed_queries");
            FileUtilities.ensureExists(failedQueriesPath);
            logger.fine(String.format(MESSAGE, site.getName(), satellite.name(),
                    String.format("Performing query for interval %s - %s", start.toString(), end.toString())));
            final Tuple<String, String> key = new Tuple<>(site.getName(), satellite.friendlyName());
            runningJobs.put(key, 0);
            // Invoke the downloadService via this method to control the number of connections
            ThreadPoolExecutor worker = Config.getWorkerFor(queryConfiguration);
            // Invoke the downloadService via this method to control the number of connections
            final int defaultTimeout = Integer.parseInt(Config.getSetting(ConfigurationKeys.DOWNLOADER_QUERY_TIMEOUT, "90")) * 1000;
            NetUtils.setTimeout(defaultTimeout);
            logger.finest(String.format("Query timeout is set to %d s", defaultTimeout));
            //final long timeout = defaultTimeout * polygons;
            if (polygons > 1) {
                logger.finest(String.format("The site %s footprint has %d geometries", site.getShortName(), polygons));
            }
            final QueryHandler queryHandler = new QueryHandler(site.getId(), failedQueriesPath);
            downloadService.addQueryListener(site.getId(), queryHandler);
            final Future<List<EOProduct>> future = worker.submit(() -> downloadService.query(site.getId(), query, queryConfiguration));
            List<EOProduct> results;
            try {
                results = future.get();
            } catch (ExecutionException ex) {
                logger.warning(String.format("At least one query failed for site %s (reason: %s). It was saved in '%s' and will be retried later.",
                                             site.getShortName(), ex.getMessage(), failedQueriesPath));
                results = new ArrayList<>();
            }
            // Retry previously failed queries, but with a double timeout
            NetUtils.setTimeout(2 * defaultTimeout);
            final List<Query> queryList = queryHandler.list(satellite, LocalDateTime.now().minusHours(1));
            if (queryList.size() > 0) {
                final Set<String> names = results.stream().map(EOData::getName).collect(Collectors.toSet());
                for (Query q : queryList) {
                    final Future<List<EOProduct>> f = worker.submit(() -> downloadService.query(site.getId(), q, queryConfiguration));
                    try {
                        final List<EOProduct> eoProducts = f.get();
                        eoProducts.removeIf(p -> names.contains(p.getName()));
                        results.addAll(eoProducts);
                    } catch (ExecutionException ex) {
                        logger.warning(String.format("Query failed for site %s (reason: %s). It will be retried later.",
                                site.getShortName(), ex.getMessage()));
                    }
                }
            }
            downloadService.removeQueryListeners(site.getId());
            NetUtils.setTimeout(defaultTimeout);
            filterByTiles(satellite, tiles, results);
            // For S2, the product extent is not the same as the UTM tile extent, hence the site footprint may not intersect
            // the data area of the product => we need to discard such products
            final int initialResultsCount = results.size();
            validateProducts(site, results);
            logger.finest(String.format("%s %s products were discarded.",
                                        results.size() == initialResultsCount ? "No" : initialResultsCount - results.size(),
                                        satellite.friendlyName()));
            saveProductCount(site.getId(), satellite, start, end, results.size());
            logger.info(String.format(MESSAGE, site.getName(), satellite.name(),
                                      String.format("Found %d products for site %s and satellite %s",
                                                    results.size(), site.getShortName(), satellite.friendlyName())));
            final String forceStartKey = String.format(ConfigurationKeys.DOWNLOADER_SENSOR_FORCE_START, satellite.friendlyName());
            if (Config.getAsBoolean(site.getId(), forceStartKey, false)) {
                // one-time forced lookup, therefore remove all products that have a NOK status
                logger.config(String.format("Forced lookup kicking in for site '%s' and satellite '%s', will delete products with status in (1, 3, 4)",
                                          site.getShortName(), satellite.friendlyName()));
                List<DownloadProduct> failed = persistenceManager.getProducts(site.getId(), satellite.value(),
                                                                              Status.DOWNLOADING, Status.FAILED, Status.ABORTED);
                int deleted = persistenceManager.deleteProducts(failed);
                if (deleted > 0) {
                    logger.info(String.format("Forced lookup purged %s products for site '%s' and satellite '%s'",
                                              deleted, site.getShortName(), satellite.friendlyName()));
                }
                Config.setSetting(site.getId(), forceStartKey, "false");
                logger.config(String.format("Flag '%s' for site '%s' and satellite '%s' was reset. Next lookup will perform normally",
                                          forceStartKey, site.getShortName(), satellite.friendlyName()));
            }
            // Check for already downloaded products with status (2, 5, 6, 7).
            // If such products exist for other sites, they will be "duplicated" for the current site
            final boolean skipExisting = Boolean.parseBoolean(Config.getSetting(ConfigurationKeys.SKIP_EXISTING_PRODUCTS, "false"));
            if (skipExisting && results.size() > 0) {
                final List<DownloadProduct> withoutOrbitDirection = persistenceManager.getProductsWithoutOrbitDirection(site.getId(), Satellite.Sentinel1.value());
                if (withoutOrbitDirection != null && withoutOrbitDirection.size() > 0) {
                    logger.info(String.format("Found %d products in database without orbit direction. Attempting to set it.",
                                              withoutOrbitDirection.size()));
                    for (DownloadProduct product : withoutOrbitDirection) {
                        EOProduct found = results.stream()
                                .filter(r -> r.getName().equals(product.getProductName().replace(".SAFE", "")))
                                .findFirst().orElse(null);
                        if (found != null && found.getAttributeValue("orbitdirection") != null) {
                            product.setOrbitType(OrbitType.valueOf(found.getAttributeValue("orbitdirection")));
                            persistenceManager.save(product);
                        }
                    }
                    withoutOrbitDirection.clear();
                }
                final List<String> existing = persistenceManager.getOtherSitesProducts(site.getId(),
                        results.stream()
                                .map(EOData::getName)
                                .collect(Collectors.toSet()));
                if (existing != null && existing.size() > 0) {
                    logger.info(String.format("The following products have already been downloaded for other sites and will not be re-downloaded: %s",
                                              String.join(",", existing)));
                    results.removeIf(r -> existing.contains(r.getName()));
                    persistenceManager.attachToSite(site, existing);
                }
            }
            final int resultsSize = results.size();
            logger.fine(String.format("Actual products to download for site %s and satellite %s: %d",
                                      site.getShortName(), satellite.friendlyName(), resultsSize));
            if (resultsSize > 0) {
                worker = Config.getWorkerFor(downloadConfiguration);
                runningJobs.put(key, resultsSize);
                final FetchMode fetchMode = downloadConfiguration.getFetchMode();
                for (int i = 0; i < resultsSize; i++) {
                    final List<EOProduct> subList = results.subList(i, i + 1);
                    final DownloadTask downloadTask = new DownloadTask(logger, site, satellite, subList,
                            () -> {
                                Instant startTime = Instant.now();
                                downloadService.download(site.getId(), subList, tiles, path, downloadConfiguration);
                                long seconds = Duration.between(startTime, Instant.now()).getSeconds();
                                if (fetchMode == FetchMode.SYMLINK && seconds > 10) {
                                    sendNotification(ProcessingTopic.PROCESSING_ATTENTION.value(),
                                                     String.format("Lookup site \"%s\"", site.getName()),
                                                     String.format("Symlink creation took %d seconds", seconds));
                                }
                            }, LookupJob.this::downloadCompleted);
                    if (fetchMode == FetchMode.SYMLINK || fetchMode == FetchMode.CHECK) {
                        downloadTask.run();
                    } else {
                        worker.submit(downloadTask);
                    }
                }
            }
        } catch (Throwable e) {
            final String message = ExceptionUtils.getStackTrace(logger, e);
            logger.severe(message);
            sendNotification(ProcessingTopic.PROCESSING_ATTENTION.value(),
                             String.format("Lookup site \"%s\"", site.getName()),
                             message);
        }
    }

    private TileExtent getExtentHelper(Satellite satellite) {
        final TileExtent extentHelper;
        switch (satellite) {
            case Sentinel2:
                extentHelper = Sentinel2TileExtent.getInstance();
                break;
            case Landsat8:
                extentHelper = Landsat8TileExtent.getInstance();
                break;
            case Sentinel1:
            default:
                extentHelper = null;
                break;
        }
        return extentHelper;
    }

    private Satellite getPrimarySensor() {
        return EnumUtils.getEnumConstantByFriendlyName(Satellite.class,
                                                       Config.getSetting("primary.sensor", "S2"));
    }

    private void filterByTiles(final Satellite satellite, final Set<String> tiles, final List<EOProduct> results) {
        final List<Geometry> tileExtents = getTilesGeometries(tiles, satellite);
        if (tileExtents.size() > 0) {
            // if the satellite is not the primary one, and there is a tile filter defined for the primary one,
            // filter the results by the intersection of the primary satellite tiles
            final WKTReader reader = new WKTReader();
            final Iterator<EOProduct> productIterator = results.iterator();
            while (productIterator.hasNext()) {
                final EOProduct current = productIterator.next();
                try {
                    final Geometry footprint = reader.read(current.getGeometry());
                    if (tileExtents.stream().noneMatch(e -> computeIntersection(e, footprint) >= 0.05)) {
                        logger.fine(String.format("Product %s was excluded because it intersects at all or very little the defined %s tiles",
                                                  current.getName(), getPrimarySensor().name()));
                        productIterator.remove();
                    }
                } catch (Exception e) {
                    ExceptionUtils.getStackTrace(logger, e);
                }
            }
        }
    }

    private List<Geometry> getTilesGeometries(Set<String> tiles, Satellite satellite) {
        final List<Geometry> extents = new ArrayList<>();
        final TileExtent extentHelper;
        if (tiles != null && tiles.size() > 0 && satellite != getPrimarySensor()
            && (extentHelper = getExtentHelper(satellite)) != null) {
            final WKTReader reader = new WKTReader();
            Polygon2D envelope;
            for (String tile : tiles) {
                envelope = Polygon2D.fromPath2D(extentHelper.getTileExtent(tile));
                try {
                    extents.add(reader.read(envelope.toWKT(8)));
                } catch (ParseException e) {
                    ExceptionUtils.getStackTrace(logger, e);
                }
            }
        }
        return extents;
    }

    private double computeIntersection(final Geometry g1, final Geometry g2) {
        final Geometry intersection = g1.intersection(g2);
        return intersection.isEmpty() ? 0.0 : intersection.getArea() / g1.getArea();
    }

    private void saveProductCount(short siteId, Satellite satellite, LocalDateTime start, LocalDateTime end, int count) {
        final ProductCount productCount = new ProductCount();
        productCount.setSiteId(siteId);
        productCount.setSatellite(satellite);
        productCount.setStartDate(start.toLocalDate());
        productCount.setEndDate(end.toLocalDate());
        productCount.setCount(count);
        persistenceManager.save(productCount);
    }

    private void validateTiles(Site site, Set<String> tiles, Satellite satellite) {
        if (tiles == null || tiles.size() == 0) {
            return;
        }
        final TileExtent extentHelper = getExtentHelper(satellite);
        if (extentHelper != null) {
            final WKTReader reader = new WKTReader();
            Geometry siteFootprint;
            try {
                siteFootprint = reader.read(site.getExtent());
            } catch (ParseException e) {
                logger.severe(String.format("Invalid geometry for site %s [%s]", site.getShortName(), site.getExtent()));
                return;
            }
            Iterator<String> iterator = tiles.iterator();
            while (iterator.hasNext()) {
                String tile = iterator.next();
                Path2D.Double tileExtent = extentHelper.getTileExtent(tile);
                if (tileExtent == null) {
                    logger.warning(String.format("No spatial footprint found for tile '%s'. Tile will be discarded.", tile));
                    iterator.remove();
                } else {
                    Polygon2D tilePolygon = Polygon2D.fromPath2D(tileExtent);
                    if (tilePolygon == null) {
                        logger.warning(String.format("Invalid spatial footprint found for tile '%s'. Tile will be discarded.", tile));
                        iterator.remove();
                    } else {
                        try {
                            Geometry tileFootprint = reader.read(tilePolygon.toWKT(8));
                            if (!siteFootprint.intersects(tileFootprint)) {
                                logger.warning(String.format("Tile '%s' does not intersect the footprint of site '%s'. Tile will be discarded.",
                                                             tile, site.getShortName()));
                                iterator.remove();
                            }
                        } catch (ParseException e) {
                            logger.severe(String.format("Invalid geometry for tile %s [%s]. Tile will be discarded", tile, e.getMessage()));
                            iterator.remove();
                        }
                    }
                }
            }
        }
    }

    private void validateProducts(Site site, List<EOProduct> products) {
        if (products == null || products.size() == 0) {
            return;
        }
        final GeometryAdapter adapter = new GeometryAdapter();
        Geometry siteFootprint;
        try {
            siteFootprint = adapter.marshal(site.getExtent());
        } catch (Exception e) {
            logger.severe(String.format("Invalid geometry for site %s [%s]", site.getShortName(), site.getExtent()));
            return;
        }
        final Iterator<EOProduct> iterator = products.iterator();
        while (iterator.hasNext()) {
            final EOProduct product = iterator.next();
            final String geometry = product.getGeometry();
            final String name = product.getName();
            if (geometry == null) {
                logger.warning(String.format("No spatial footprint found for product '%s'. Product will be discarded.", name));
                iterator.remove();
            } else {
                Geometry productFootprint;
                try {
                    productFootprint = adapter.marshal(geometry);
                    if (!siteFootprint.intersects(productFootprint)) {
                        logger.warning(String.format("Product '%s' does not intersect the footprint of site '%s'. It will be discarded.",
                                name, site.getShortName()));
                        iterator.remove();
                    }
                } catch (Exception e) {
                    logger.severe(String.format("Invalid geometry for product %s [%s]. Product will be discarded",
                            name, e.getMessage()));
                    iterator.remove();
                }
            }
        }
    }

    private void cleanupJob(Site site, Satellite sat) {
        runningJobs.remove(new Tuple<>(site.getName(), sat.friendlyName()));
    }

    private void downloadCompleted(Triple<String, String, String> key) {
        synchronized (sharedLock) {
            final String site = key.getKeyOne();
            final String satellite = key.getKeyTwo();
            final String products = key.getKeyThree();
            final Tuple<String, String> jobKey = new Tuple<>(site, satellite);
            Integer previousCount = runningJobs.get(jobKey);
            if (previousCount == null) {
                logger.warning(String.format("A download completed for site '%s' and satellite '%s', but no previous job was found for this pair",
                                             site, satellite));
                previousCount = 1;
            }
            int count = (products != null && !products.isEmpty()) ? products.split(",").length : 0;
            int current = previousCount - count;
            logger.fine(String.format("Download completed [%s] - remaining products for {site:'%s',satellite:'%s'}: %d",
                                      products, site, satellite, current));
            if (current > 0) {
                runningJobs.put(jobKey, current);
            } else {
                runningJobs.remove(jobKey);
            }
        }
    }
}
