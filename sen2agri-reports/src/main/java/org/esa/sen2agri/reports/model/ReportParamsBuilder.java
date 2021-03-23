package org.esa.sen2agri.reports.model;

import java.util.HashMap;
import java.util.Map;

public class ReportParamsBuilder {
    private final Map<String, Object> params = new HashMap<>();

    public ReportParamsBuilder withParam(String name, Object value) {
        this.params.put(name, value);
        return this;
    }

    public Map<String, Object> build() { return this.params; }
}
