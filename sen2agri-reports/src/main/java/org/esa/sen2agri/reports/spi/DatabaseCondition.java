package org.esa.sen2agri.reports.spi;

import ro.cs.tao.TaoEnum;

public enum DatabaseCondition implements TaoEnum<String> {
    EQ("="),
    NEQ("!="),
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<=");

    private final String value;

    private DatabaseCondition(String value) {
        this.value = value;
    }

    @Override
    public String friendlyName() { return value; }

    @Override
    public String value() { return value; }
}
