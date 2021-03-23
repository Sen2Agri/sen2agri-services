package org.esa.sen2agri.services;

import org.esa.sen2agri.entities.enums.Satellite;
import org.esa.sen2agri.web.beans.Query;

public interface QueryListener {
    void onCompleted(Satellite satellite, Query dataQuery);
    void onFailed(Satellite satellite, Query dataQuery);
}

