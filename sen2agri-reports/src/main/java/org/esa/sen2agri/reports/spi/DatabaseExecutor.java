package org.esa.sen2agri.reports.spi;

import org.esa.sen2agri.db.Database;
import org.esa.sen2agri.entities.enums.Satellite;
import ro.cs.tao.utils.DateUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class DatabaseExecutor extends Database {

    public static int executeRawQuery(BaseReport<?> report) {
        int rows = 0;
        try (Connection connection = getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(report.rawDataQuery());
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                rows = resultSet.getInt(1);
            }
        } catch (SQLException e) {
            logger.severe(String.format("Cannot produce %s. Reason: %s", report.name(), e.getMessage()));
        }
        return rows;
    }

    public static List<Integer> getOrbits(Satellite satellite, Short siteId) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT orbit_id FROM reports.sp_reports_orbits(")
                .append(satellite.value()).append("::smallint,")
                .append(siteId != null && siteId > 0 ? siteId : "null").append("::smallint)");
        final List<Integer> results = new ArrayList<>();
        try (Connection connection = getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(query.toString());
            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                results.add(resultSet.getInt(1));
            }
        } catch (SQLException e) {
            logger.severe(String.format("Cannot retrieve %s orbits. Reason: %s", satellite.friendlyName(), e.getMessage()));
        }
        return results;
    }

    public static <T> List<T> executeFunction(BaseReport<T> report, Map<String, Object> parameters) {
        StringBuilder query = new StringBuilder();
        query.append(report.reportQuery()).append(computeFunctionArguments(report, parameters));
        final List<T> results = new ArrayList<>();
        try (Connection connection = getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(query.toString());
            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                results.add(report.mapRow(resultSet));
            }
        } catch (SQLException e) {
            logger.severe(String.format("Cannot retrieve %s. Reason: %s", report.name(), e.getMessage()));
        }
        return results;
    }

    public static <T> List<T> executeQuery(BaseReport<T> report, Map<String, Object> parameters) {
        StringBuilder query = new StringBuilder();
        query.append(report.reportQuery()).append(computeWhereCondition(report, parameters));
        final List<T> results = new ArrayList<>();
        try (Connection connection = getConnection()) {
            final PreparedStatement statement = connection.prepareStatement(query.toString());
            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                results.add(report.mapRow(resultSet));
            }
        } catch (SQLException e) {
            logger.severe(String.format("Cannot retrieve %s. Reason: %s", report.name(), e.getMessage()));
        }
        return results;
    }

    private static String computeFunctionArguments(BaseReport<?> report, Map<String, Object> parameters) {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("(");
        if (parameters != null) {
            final Map<String, Map.Entry<DatabaseCondition, String>> databaseTypes = report.parameterDatabaseTypes();
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                final Map.Entry<DatabaseCondition, String> stringEntry = databaseTypes.get(entry.getKey());
                if (stringEntry == null) {
                    throw new IllegalArgumentException(entry.getKey() + " is not supported");
                }
                buffer.append(entry.getKey()).append(":=").append(toString(entry.getValue()))
                        .append("::").append(stringEntry.getValue()).append(",");
            }
            buffer.setLength(buffer.length() - 1);
        }
        buffer.append(")");
        return buffer.toString();
    }

    private static String computeWhereCondition(BaseReport<?> report, Map<String, Object> parameters) {
        final StringBuilder buffer = new StringBuilder();
        if (parameters != null) {
            boolean whereAdded = false;
            final Map<String, Map.Entry<DatabaseCondition, String>> databaseTypes = report.parameterDatabaseTypes();
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                final Map.Entry<DatabaseCondition, String> stringEntry = databaseTypes.get(entry.getKey());
                if (stringEntry == null) {
                    throw new IllegalArgumentException(entry.getKey() + " is not supported");
                }
                if (!whereAdded) {
                    buffer.append("WHERE ");
                    whereAdded = true;
                } else {
                    buffer.append("AND ");
                }
                buffer.append(entry.getKey())
                        .append(stringEntry.getKey().value())
                        .append(toString(entry.getValue()))
                        .append(" ");
            }
        }
        return buffer.toString();
    }

    private static String toString(Object value) {
        final String stringValue;
        if (value != null) {
            if (value instanceof Date) {
                stringValue = "'" + DateUtils.getFormatterAtUTC("yyyy-MM-dd").format((Date) value) + "'";
            } else if (value instanceof String) {
                stringValue = "'" + value.toString() + "'";
            } else {
                stringValue = value.toString();
            }
        } else {
            stringValue = "null";
        }
        return stringValue;
    }
}
