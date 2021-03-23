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
package org.esa.sen2agri.db;

import org.esa.sen2agri.entities.*;
import org.esa.sen2agri.entities.converters.ActivityStatusConverter;
import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.entities.enums.Status;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import ro.cs.tao.EnumUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Repository-like class for handling entities not mapped in the orm.xml file.
 *
 * @author Cosmin Cara
 */
class NonMappedEntitiesRepository {

    private PersistenceManager persistenceManager;

    NonMappedEntitiesRepository(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    SiteTiles getSiteTiles(short siteId, short satelliteId) {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        SiteTiles siteTiles = new SiteTiles();
        try (Connection conn = dataSource.getConnection()) {
            jdbcTemplate.query(
                    connection -> {
                        PreparedStatement statement =
                                connection.prepareStatement("SELECT site_id, satellite_id, tiles FROM public.site_tiles " +
                                                                    "WHERE site_id = ? AND satellite_id = ?");
                        statement.setShort(1, siteId);
                        statement.setShort(2, satelliteId);
                        return statement;
                    },
                    (resultSet, i) -> {
                        siteTiles.setSiteId(resultSet.getShort(1));
                        siteTiles.setSatellite(EnumUtils.getEnumConstantByValue(Satellite.class, resultSet.getShort(2)));
                        siteTiles.setTiles((String[]) resultSet.getArray(3).getArray());
                        return siteTiles;
                    });
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return siteTiles;
    }

    List<ProductCount> getEstimatedProductCount(int siteId, int satelliteId) {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try (Connection conn = dataSource.getConnection()) {
            return jdbcTemplate.query(
                    connection -> {
                        PreparedStatement statement =
                                connection.prepareStatement("SELECT site_id, satellite_id, start_date, end_date, product_count FROM public.downloader_count " +
                                                                    "WHERE site_id = ? AND satellite_id = ?");
                        statement.setInt(1, siteId);
                        statement.setInt(2, satelliteId);
                        return statement;
                    },
                    (resultSet, i) -> {
                        ProductCount productCount = new ProductCount();
                        productCount.setSiteId(resultSet.getShort(1));
                        productCount.setSatellite(EnumUtils.getEnumConstantByValue(Satellite.class, resultSet.getShort(2)));
                        productCount.setStartDate(resultSet.getDate(3).toLocalDate());
                        productCount.setEndDate(resultSet.getDate(4).toLocalDate());
                        productCount.setCount(resultSet.getInt(5));
                        return productCount;
                    });
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    ProductDetails getProductStatistics(int productId) {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try (Connection conn = dataSource.getConnection()){
            return jdbcTemplate.query(
                    connection -> {
                        PreparedStatement statement =
                                connection.prepareStatement("SELECT product_id, min_value, max_value, mean_value, std_dev, histogram FROM public.product_stats " +
                                                                    "WHERE product_id = ?");
                        statement.setInt(1, productId);
                        return statement;
                    }, resultSet -> {
                        ProductDetails productDetails = null;
                        if (resultSet.isFirst()) {
                            do {
                                productDetails = new ProductDetails();
                                productDetails.setId(resultSet.getInt(1));
                                productDetails.setMinValue(resultSet.getDouble(2));
                                productDetails.setMaxValue(resultSet.getDouble(3));
                                productDetails.setMeanValue(resultSet.getDouble(4));
                                productDetails.setStdDevValue(resultSet.getDouble(5));
                                Array array = resultSet.getArray(6);
                                if (array != null) {
                                    productDetails.setHistogram((Integer[]) array.getArray());
                                }
                            } while (resultSet.next());
                        }
                        return productDetails;
                    });
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    List<ProductDetails> getStatisticsForProducts(Set<Integer> productIds) {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try (Connection conn = dataSource.getConnection()) {
            return jdbcTemplate.query(
                    connection -> {
                        PreparedStatement statement =
                                connection.prepareStatement("SELECT product_id, min_value, max_value, mean_value, std_dev, histogram FROM public.product_stats " +
                                                                    "WHERE product_id IN (?)");
                        statement.setString(1, productIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
                        return statement;
                    },
                    (resultSet, i) -> {
                        ProductDetails productDetails = new ProductDetails();
                        productDetails.setId(resultSet.getInt(1));
                        productDetails.setMinValue(resultSet.getDouble(2));
                        productDetails.setMaxValue(resultSet.getDouble(3));
                        productDetails.setMeanValue(resultSet.getDouble(4));
                        productDetails.setStdDevValue(resultSet.getDouble(5));
                        Array array = resultSet.getArray(6);
                        if (array != null) {
                            productDetails.setHistogram((Integer[]) array.getArray());
                        }
                        return productDetails;
                    });
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    Task getTask(int jobId, String name) {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return jdbcTemplate.queryForObject(String.format("SELECT id, job_id, module_short_name, parameters, submit_timestamp, start_timestamp, end_timestamp, " +
                                                                 "status_id, status_timestamp, preceding_task_ids FROM public.task WHERE job_id = %d AND module_short_name = '%s'",
                                                         jobId, name),
                                           (rs, i) -> {
                                               Task task = new Task();
                                               task.setId(rs.getInt(1));
                                               task.setJobId(rs.getInt(2));
                                               task.setModuleShortName(rs.getString(3));
                                               task.setParameters(rs.getString(4));
                                               Timestamp timestamp = rs.getTimestamp(5);
                                               task.setSubmitTimestamp(timestamp != null ? timestamp.toLocalDateTime() : null);
                                               timestamp = rs.getTimestamp(6);
                                               task.setStartTimestamp(timestamp != null ? timestamp.toLocalDateTime() : null);
                                               timestamp = rs.getTimestamp(7);
                                               task.setEndTimestamp(timestamp != null ? timestamp.toLocalDateTime() : null);
                                               task.setStatus(new ActivityStatusConverter().convertToEntityAttribute(rs.getInt(8)));
                                               timestamp = rs.getTimestamp(9);
                                               task.setStatusTimestamp(timestamp != null ? timestamp.toLocalDateTime() : null);
                                               Array array = rs.getArray(10);
                                               if (array != null) {
                                                   task.setPrecedingTasks(Arrays.stream(((Integer[])array.getArray())).mapToInt(Integer::intValue).toArray());
                                               }
                                               return task;
                                           });
    }

    void save(ProductDetails productDetails) throws SQLException {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            jdbcTemplate.batchUpdate("WITH upsert AS ( UPDATE public.product_stats SET min_value = ?, max_value = ?, mean_value = ?, std_dev = ?, histogram = ? " +
                                             "WHERE product_id = ? RETURNING *) " +
                                             "INSERT INTO public.product_stats (product_id, min_value, max_value, mean_value, std_dev, histogram) " +
                                             "SELECT ?, ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT * FROM upsert);",
                                     new BatchPreparedStatementSetter() {
                                         @Override
                                         public void setValues(PreparedStatement preparedStatement, int row) throws SQLException {
                                             preparedStatement.setDouble(1, productDetails.getMinValue());
                                             preparedStatement.setDouble(2, productDetails.getMaxValue());
                                             preparedStatement.setDouble(3, productDetails.getMeanValue());
                                             preparedStatement.setDouble(4, productDetails.getStdDevValue());
                                             Array array = null;
                                             if (productDetails.getHistogram() != null) {
                                                 array = connection.createArrayOf("integer", productDetails.getHistogram());
                                             }
                                             preparedStatement.setArray(5, array);
                                             preparedStatement.setInt(6, productDetails.getId());
                                             preparedStatement.setInt(7, productDetails.getId());
                                             preparedStatement.setDouble(8, productDetails.getMinValue());
                                             preparedStatement.setDouble(9, productDetails.getMaxValue());
                                             preparedStatement.setDouble(10, productDetails.getMeanValue());
                                             preparedStatement.setDouble(11, productDetails.getStdDevValue());
                                             preparedStatement.setArray(12, array);
                                         }

                                         @Override
                                         public int getBatchSize() {
                                             return 1;
                                         }
                                     });
        }
    }

    Task save(Task task) {
        DataSource dataSource = persistenceManager.getDataSource();
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(dataSource).withTableName("task").usingGeneratedKeyColumns("id");
        try(Connection connection = dataSource.getConnection()) {
            task.setId((int) jdbcInsert.executeAndReturnKey(new HashMap<String, Object>() {{
                put("job_id", task.getJobId());
                put("module_short_name", task.getModuleShortName());
                put("parameters", task.getParameters());
                LocalDateTime dateTime = task.getSubmitTimestamp();
                put("submit_timestamp", dateTime != null ? Timestamp.valueOf(dateTime) : null);
                dateTime = task.getStartTimestamp();
                put("start_timestamp", dateTime != null ? Timestamp.valueOf(dateTime) : null);
                dateTime = task.getEndTimestamp();
                put("end_timestamp", dateTime != null ? Timestamp.valueOf(dateTime) : null);
                put("status_id", new ActivityStatusConverter().convertToDatabaseColumn(task.getStatus()));
                dateTime = task.getStatusTimestamp();
                put("status_timestamp", dateTime != null ? Timestamp.valueOf(dateTime) : null);
                int[] ids = task.getPrecedingTasks();
                Array array = null;
                if (ids != null && ids.length > 0) {
                    final Integer[] integers = IntStream.of(ids).boxed().toArray(Integer[]::new);
                    array = connection.createArrayOf("integer", integers);
                }
                put("preceding_task_ids", array);
            }}));
        } catch (SQLException e ) {
            e.printStackTrace();
        }
        return task;
    }

    Task update(Task task) throws SQLException {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            jdbcTemplate.batchUpdate("UPDATE public.task SET module_short_name = ?, parameters = ?, submit_timestamp = ?, start_timestamp = ?, end_timestamp = ?, " +
                                             "status_id = ?, status_timestamp = ?, preceding_task_ids = ? " +
                                             "WHERE id = ?",
                                     new BatchPreparedStatementSetter() {
                                         @Override
                                         public void setValues(PreparedStatement preparedStatement, int row) throws SQLException {
                                             preparedStatement.setString(1, task.getModuleShortName());
                                             preparedStatement.setString(2, task.getParameters());
                                             LocalDateTime dateTime = task.getSubmitTimestamp();
                                             preparedStatement.setTimestamp(3, dateTime != null ? Timestamp.valueOf(dateTime) : null);
                                             dateTime = task.getStartTimestamp();
                                             preparedStatement.setTimestamp(4, dateTime != null ? Timestamp.valueOf(dateTime) : null);
                                             dateTime = task.getEndTimestamp();
                                             preparedStatement.setTimestamp(5, dateTime != null ? Timestamp.valueOf(dateTime) : null);
                                             preparedStatement.setInt(6, new ActivityStatusConverter().convertToDatabaseColumn(task.getStatus()));
                                             dateTime = task.getStatusTimestamp();
                                             preparedStatement.setTimestamp(7, dateTime != null ? Timestamp.valueOf(dateTime) : null);
                                             Array array = null;
                                             int[] ids = task.getPrecedingTasks();
                                             if (ids != null && ids.length > 0) {
                                                 final Integer[] integers = IntStream.of(ids).boxed().toArray(Integer[]::new);
                                                 array = connection.createArrayOf("integer", integers);
                                             }
                                             preparedStatement.setArray(8, array);
                                             preparedStatement.setInt(9, task.getId());
                                         }

                                         @Override
                                         public int getBatchSize() {
                                             return 1;
                                         }
                                     });
        }
        return task;
    }

    void save(ProductCount productCount) throws SQLException {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            jdbcTemplate.batchUpdate("WITH upsert AS ( UPDATE public.downloader_count SET product_count = ?, last_updated = ? " +
                                             "WHERE site_id = ? AND satellite_id = ? AND start_date = ? AND end_date = ? RETURNING *) " +
                                             "INSERT INTO public.downloader_count (site_id, satellite_id, start_date, end_date, product_count) " +
                                             "SELECT ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT * FROM upsert);",
                                     new BatchPreparedStatementSetter() {
                                         @Override
                                         public void setValues(PreparedStatement preparedStatement, int row) throws SQLException {
                                             preparedStatement.setInt(1, productCount.getCount());
                                             preparedStatement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                                             preparedStatement.setShort(3, productCount.getSiteId());
                                             preparedStatement.setShort(4, productCount.getSatellite().value());
                                             preparedStatement.setDate(5, Date.valueOf(productCount.getStartDate()));
                                             preparedStatement.setDate(6, Date.valueOf(productCount.getEndDate()));
                                             preparedStatement.setShort(7, productCount.getSiteId());
                                             preparedStatement.setShort(8, productCount.getSatellite().value());
                                             preparedStatement.setDate(9, Date.valueOf(productCount.getStartDate()));
                                             preparedStatement.setDate(10, Date.valueOf(productCount.getEndDate()));
                                             preparedStatement.setInt(11, productCount.getCount());
                                         }

                                         @Override
                                         public int getBatchSize() {
                                             return 1;
                                         }
                                     });
        }
    }

    List<S2Tile> getIntersectingTiles(String wkt, double minIntersection) {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try (Connection conn = dataSource.getConnection()) {
            return jdbcTemplate.query(
                    connection -> {
                        PreparedStatement statement =
                                connection.prepareStatement("WITH Transformed AS (" +
                                        "SELECT TILE_ID, 'EPSG:' || EPSG_CODE::VARCHAR AS PROJ, ST_TRANSFORM(GEOM, EPSG_CODE) AS GEOM FROM shape_tiles_s2 " +
                                        "WHERE st_area(st_intersection(GEOM, st_geomfromtext(?,4326))) / st_area(GEOM) > ?) " +
                                        "SELECT TILE_ID, PROJ, ST_X(ST_POINTN(ST_EXTERIORRING(GEOM), 4))::INT AS xMin, ST_Y(ST_POINTN(ST_EXTERIORRING(GEOM), 4))::INT AS yMin, " +
                                        "ST_X(ST_POINTN(ST_EXTERIORRING(GEOM), 2))::INT AS xMax, ST_Y(ST_POINTN(ST_EXTERIORRING(GEOM), 2))::INT AS yMax " +
                                        "FROM Transformed");
                        statement.setString(1, wkt);
                        statement.setDouble(2, minIntersection);
                        return statement;
                    },
                    (rs, i) -> new S2Tile(rs.getString("TILE_ID"), rs.getString("PROJ"), new int[] {
                            rs.getInt("xMin"), rs.getInt("yMin"), rs.getInt("xMax"), rs.getInt("yMax")
                    }));
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    List<S2Tile> getIntersectingTiles(short siteId, double minIntersection) {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return jdbcTemplate.query(
                connection -> {
                    PreparedStatement statement =
                            connection.prepareStatement("WITH Transformed AS (SELECT T.TILE_ID, 'EPSG:' || T.EPSG_CODE::VARCHAR AS PROJ, ST_TRANSFORM(T.GEOM, T.EPSG_CODE) AS GEOM FROM shape_tiles_s2 T " +
                                    "JOIN site S ON st_area(st_intersection(T.GEOM, S.geog)) / st_area(T.GEOM) > ? " +
                                    "WHERE S.id = ?) " +
                                    "SELECT TILE_ID, PROJ, ST_X(ST_POINTN(ST_EXTERIORRING(GEOM), 4))::INT AS xMin, ST_Y(ST_POINTN(ST_EXTERIORRING(GEOM), 4))::INT AS yMin, " +
                                    "ST_X(ST_POINTN(ST_EXTERIORRING(GEOM), 2))::INT AS xMax, ST_Y(ST_POINTN(ST_EXTERIORRING(GEOM), 2))::INT AS yMax " +
                                    "FROM Transformed");
                    statement.setDouble(1, minIntersection);
                    statement.setShort(2, siteId);
                    return statement;
                },
                (rs, i) -> new S2Tile(rs.getString("TILE_ID"), rs.getString("PROJ"), new int[] {
                        rs.getInt("xMin"), rs.getInt("yMin"), rs.getInt("xMax"), rs.getInt("yMax")
                }));
    }

    List<Map.Entry<LocalDateTime, Path>> getPreviousAsymmetricFilteredProducts(int siteId, int productType, String utmTile,
                                                                               String polarisation, int orbit, LocalDateTime acquisitionDate, int daysBack) {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try (Connection conn = dataSource.getConnection()) {
            return jdbcTemplate.query(
                    connection -> {
                        PreparedStatement statement =
                                connection.prepareStatement("SELECT created_timestamp, full_path FROM product " +
                                        "WHERE site_id = ? AND product_type_id = ? AND name LIKE ? AND " +
                                        "DATE_PART('day', ? - created_timestamp) BETWEEN 1 AND ? order by created_timestamp");
                        statement.setInt(1, siteId);
                        statement.setInt(2, productType);
                        statement.setString(3, '%'+ polarisation + "_" + String.format("%03d", orbit) + "_" + utmTile + '%');
                        statement.setTimestamp(4, Timestamp.valueOf(acquisitionDate));
                        statement.setInt(5, daysBack);
                        return statement;
                    },
                    (rs, i) -> new AbstractMap.SimpleEntry<>(rs.getTimestamp(1).toLocalDateTime(), Paths.get(rs.getString(2))
                    ));
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    List<ExpectedBackscatter> getPreviousExpectedProducts(int dldProductId, short siteId, String polarisation) {
        DataSource dataSource = persistenceManager.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        try (Connection conn = dataSource.getConnection()) {
            return jdbcTemplate.query(
                    connection -> {
                        PreparedStatement statement =
                                connection.prepareStatement("WITH CurrentS2Tiles AS (" +
                                        "SELECT d.id, s.tile_id " +
                                        "FROM shape_tiles_s2 s " +
                                        "JOIN site_tiles st ON s.tile_id = ANY(st.tiles) " +
                                        "JOIN downloader_history d ON st_area(st_intersection(s.geom, d.footprint)) / st_area(s.geom) > (SELECT value::float FROM config WHERE key = 'processor.l2s1.min.s2.intersection') " +
                                        "WHERE st.site_id = ? AND d.id = ? " +
                                        "), OlderS2Tiles AS ( " +
                                        "SELECT d.id, d.product_name, d.status_id, d.orbit_id, s.tile_id " +
                                        "FROM downloader_history d " +
                                        "JOIN downloader_history d2 ON d2.site_id = d.site_id AND d2.satellite_id = d.satellite_id AND d2.orbit_id = d.orbit_id AND d2.orbit_type_id = d.orbit_type_id AND DATE_PART('day', d2.product_date - d.product_date) BETWEEN 1 AND (SELECT value::int FROM Config WHERE key = 'processor.l2s1.temporal.filter.interval') " +
                                        "JOIN shape_tiles_s2 s ON st_area(st_intersection(s.geom, d.footprint)) / st_area(s.geom) > (SELECT value::float FROM config WHERE key = 'processor.l2s1.min.s2.intersection') " +
                                        "JOIN site_tiles st ON s.tile_id = ANY(st.tiles) " +
                                        "WHERE st.site_id = ? AND d2.id = ? " +
                                        ") " +
                                        "SELECT o.id, o.status_id, CONCAT(LEFT(o.product_name, 3),'_',split_part(o.product_name, '_', 6),'_##POL##_',LPAD(o.orbit_id::text, 3, '0'),'_',o.tile_id) as expected, p.name as found FROM OlderS2Tiles o " +
                                        "JOIN CurrentS2Tiles c ON c.tile_id = o.tile_id " +
                                        "LEFT JOIN product p ON o.id = p.downloader_history_id AND p.product_type_id = 10 AND o.tile_id = ANY(p.tiles) AND p.name LIKE ? " +
                                        "ORDER BY o.id;");
                        statement.setShort(1, siteId);
                        statement.setInt(2, dldProductId);
                        statement.setShort(3, siteId);
                        statement.setInt(4, dldProductId);
                        statement.setString(5, "%_" + polarisation + "_%");
                        return statement;
                    },
                    (rs, i) -> new ExpectedBackscatter(rs.getInt("id"), EnumUtils.getEnumConstantByValue(Status.class, rs.getInt("status_id")),
                                                       rs.getString("expected").replace("##POL##", polarisation), rs.getString("found")));
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
