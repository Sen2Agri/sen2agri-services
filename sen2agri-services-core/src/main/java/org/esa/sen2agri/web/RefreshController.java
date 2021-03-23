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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.esa.sen2agri.services.ScheduleManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import ro.cs.tao.services.commons.ControllerBase;
import ro.cs.tao.services.commons.ServiceResponse;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Cosmin Udroiu
 */
@Controller
@RequestMapping("/refresh")
public class RefreshController extends ControllerBase {
    @Autowired
    private ScheduleManager scheduleManager;

    @RequestMapping(value = "/", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<ServiceResponse<?>> refresh() {
        info("Refreshing configuration ...");
        scheduleManager.refresh();
        return prepareResult("Refresh triggered");
    }

    @RequestMapping(value = "/info", method = RequestMethod.GET, produces = "application/json")
    public ResponseEntity<ServiceResponse<?>> info() {
        final MemoryUsage heapMemoryUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        final MemoryUsage nonHeapMemoryUsage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        final int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        final ThreadInfo[] threadInfos = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
        final Map<String, Object> info = new LinkedHashMap<>();
        final ObjectWriter writer = new ObjectMapper().writerFor(ThreadInfo.class);
        info.put("Heap Memory Usage", heapMemoryUsage.toString());
        info.put("Non-heap Memory Usage", nonHeapMemoryUsage.toString());
        info.put("Thread Count", String.valueOf(threadCount));
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (ThreadInfo threadInfo : threadInfos) {
            try {
                builder.append(writer.writeValueAsString(threadInfo));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        builder.append("]");
        info.put("Threads", builder.toString());
        builder.setLength(0);
        info.put("Quartz Jobs and Triggers", scheduleManager.getExecutingJobsWithTriggers());
        return prepareResult(info);
    }
}
