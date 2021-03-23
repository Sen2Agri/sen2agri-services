package org.esa.sen2agri.entities;

import org.esa.sen2agri.entities.enums.Status;

import java.util.Objects;

public class ExpectedBackscatter {
    private final int id;
    private final Status status;
    private final String expected;
    private final String found;

    public ExpectedBackscatter(int id, Status status, String expected, String found) {
        this.id = id;
        this.status = status;
        this.expected = expected;
        this.found = found;
    }

    public int getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public String getExpected() {
        return expected;
    }

    public String getFound() {
        return found;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpectedBackscatter that = (ExpectedBackscatter) o;
        return id == that.id && status == that.status && expected.equals(that.expected) && Objects.equals(found, that.found);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status, expected, found);
    }
}
