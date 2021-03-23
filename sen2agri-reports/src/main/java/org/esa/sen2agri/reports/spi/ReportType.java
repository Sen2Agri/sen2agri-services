package org.esa.sen2agri.reports.spi;

import ro.cs.tao.TaoEnum;

public enum ReportType implements TaoEnum<String> {
    ORBIT("Orbit"),
    AGGREGATE("Aggregate"),
    DETAIL("Detail");

    private final String value;

    private ReportType(String v) {
        this.value = v;
    }

    @Override
    public String friendlyName() { return value; }

    @Override
    public String value() { return value; }
}
