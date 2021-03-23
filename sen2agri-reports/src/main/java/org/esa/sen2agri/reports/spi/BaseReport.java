package org.esa.sen2agri.reports.spi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public abstract class BaseReport<T> implements Report<T> {

    /**
     * The type of an individual record
     */
    public abstract Class<T> resultType();

    /**
     * The query to produce raw data (that will be queried by the report query
     */
    public abstract String rawDataQuery();

    /**
     * The actual report query
     */
    public abstract String reportQuery();

    /**
     * The database parameter names, conditions and database types
     */
    public abstract Map<String, Map.Entry<DatabaseCondition, String>> parameterDatabaseTypes();

    /**
     * Maps the current result from the result set into an individual record
     * @param resultSet The sql result set
     */
    public abstract T mapRow(final ResultSet resultSet) throws SQLException;

}
