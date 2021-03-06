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
package org.esa.sen2agri.web;

import org.esa.sen2agri.services.ScheduleManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import ro.cs.tao.services.commons.ControllerBase;
import ro.cs.tao.services.commons.ResponseStatus;
import ro.cs.tao.services.commons.ServiceResponse;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Endpoint for controlling the log level at runtime
 *
 * @author Cosmin Cara
 */
@Controller
@RequestMapping("/log")
public class LogController extends ControllerBase {
    @Autowired
    private ScheduleManager scheduleManager;

    @RequestMapping(value = "/level", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<ServiceResponse<?>> changeLevel(@RequestParam("level") String level,
                                                          @RequestParam("logger") String logger) {
        try {
            Logger log = Logger.getLogger(logger);
            if (log != null) {
                log.setLevel(Level.parse(level.toUpperCase()));
                final String message = String.format("Log level for %s changed to %s", logger, level);
                info(message);
                return prepareResult(message, ResponseStatus.SUCCEEDED);
            }
            return prepareResult(String.format("No logger found for %s", logger),
                                 ResponseStatus.SUCCEEDED);
        } catch (Exception e) {
            return handleException(e);
        }
    }
}
