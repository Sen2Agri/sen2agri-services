package org.esa.sen2agri.services.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.services.QueryListener;
import org.esa.sen2agri.web.beans.Query;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.FileUtilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class QueryHandler implements QueryListener {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final short siteId;
    private final Path targetPath;
    private final ObjectMapper objectMapper;
    private final Logger logger;

    public QueryHandler(short siteId, Path targetPath) {
        this.siteId = siteId;
        this.targetPath = targetPath;
        this.objectMapper = new ObjectMapper();
        this.logger = Logger.getLogger(QueryHandler.class.getName());
    }

    @Override
    public void onCompleted(Satellite satellite, Query dataQuery) {
        try {
            final Path file = computePath(satellite, dataQuery);
            Files.deleteIfExists(file);
        } catch (Exception e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
    }

    @Override
    public void onFailed(Satellite satellite, Query dataQuery) {
        try {
            if (!dataQuery.getValues().containsKey(CommonParameterNames.PRODUCT)) {
                final Path file = computePath(satellite, dataQuery);
                if (!Files.exists(file)) {
                    final Polygon2D footprint = (Polygon2D) dataQuery.getValues().get(CommonParameterNames.FOOTPRINT);
                    if (footprint != null) {
                        dataQuery.getValues().put(CommonParameterNames.FOOTPRINT, footprint.toWKT());
                    }
                    dataQuery.setUser(null);
                    dataQuery.setPassword(null);
                    Files.write(file, this.objectMapper.writeValueAsString(dataQuery).getBytes());
                }
            }
        } catch (Exception e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
    }

    public List<Query> list(Satellite satellite, LocalDateTime reference) {
        final List<Query> results = new ArrayList<>();
        try {
            final String pattern = siteId + "_" + satellite.name() + "\\w+.json";
            final List<Path> files = FileUtilities.listFiles(targetPath, pattern, reference);
            for (Path file : files) {
                final String[] tokens = file.getFileName().toString().split("_");
                switch (tokens.length) {
                    case 3: // siteId_satellite_productName.json
                    case 4: // siteId_satellite_startDate_endDate.json
                        if (Short.parseShort(tokens[0]) == siteId && Satellite.valueOf(tokens[1]).equals(satellite)) {
                            results.add(fromFile(file));
                        }
                        break;
                    default:
                        throw new IllegalArgumentException(String.format("The name of the file '%s' doesn't follow the rules", file));
                }
            }
        } catch (Exception e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
        return results;
    }

    private Path computePath(Satellite satellite, Query query) {
        final Map<String, Object> values = query.getValues();
        final Object start = values.get(CommonParameterNames.START_DATE);
        final Object end = values.get(CommonParameterNames.END_DATE);
        final StringBuilder builder = new StringBuilder();
        String startDate = null, endDate = null;
        if (start != null) {
            startDate = start instanceof String[] ?
                    ((String[]) start)[0] :
                    List.class.isAssignableFrom(start.getClass()) ? ((List<String>) start).get(0) : (String) start;
        }
        if (startDate != null) {
            builder.append("_").append(startDate.replaceAll("[:\\- ]", ""));
        }
        if (end != null) {
            endDate = end instanceof String[] ?
                    ((String[]) end)[1] :
                    List.class.isAssignableFrom(end.getClass()) ? ((List<String>) end).get(1) : (String) end;
        }
        if (endDate != null) {
            builder.append("_").append(endDate.replaceAll("[:\\- ]", ""));
        }
        if (startDate == null && endDate == null) {
            final String product = (String) values.get(CommonParameterNames.PRODUCT);
            if (product != null) {
                builder.append("_").append(product);
            }
        }
        return targetPath.resolve(siteId + "_" + satellite.name() + builder.toString() + ".json");
    }

    private Query fromFile(Path file) throws IOException {
        final Query query = this.objectMapper.readerFor(Query.class).readValue(Files.readAllBytes(file));
        query.getValues().computeIfPresent(CommonParameterNames.FOOTPRINT, (s, o) -> Polygon2D.fromWKT((String) query.getValues().get(CommonParameterNames.FOOTPRINT)));
        return query;
    }
}
