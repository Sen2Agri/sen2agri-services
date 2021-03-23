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

package org.esa.sen2agri.config;

import org.esa.sen2agri.ServicesStartup;
import org.esa.sen2agri.commons.Config;
import org.esa.sen2agri.db.Database;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.services.commons.config.ConfigurationFileProcessor;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ServicesConfigurationFileProcessor implements ConfigurationFileProcessor {

    @Override
    public String getFileName() { return "services.properties"; }

    @Override
    public String getFileResourceLocation() { return "/config/services.properties"; }

    @Override
    public void performAdditionalConfiguration(Path configDirectory, Properties properties) {
        Config.setFileConfiguration(properties);
        final String location = System.getProperty("user.home");
        Config.setProperty("workspace.location", location);
        Config.setProperty("node.mount.folder", location);
        ConfigurationManager.setConfigurationProvider(new ConfigurationProvider() {
            private Path scriptFolder;
            private Path configFolder;

            @Override
            public Path getApplicationHome() { return ServicesStartup.homeDirectory(); }

            @Override
            public String getValue(String s) { return Config.getProperty(s); }
            @Override
            public String getValue(String s, String s1) { return Config.getProperty(s, s1); }
            @Override
            public boolean getBooleanValue(String s) { return Boolean.parseBoolean(Config.getProperty(s, "false")); }
            @Override
            public Map<String, String> getValues(String s) {
                final Map<String, String> settings = Config.getSiteSettings((short) 0);
                return settings.entrySet().stream().filter(e -> e.getKey().contains(s))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }
            @Override
            public Map<String, String> getAll() { return Config.getSiteSettings((short) 0); }
            @Override
            public void setValue(String s, String s1) { Config.setSetting(s, s1); }
            @Override
            public void putAll(Properties properties) { Config.setFileConfiguration(properties); }
            @Override
            public Path getScriptsFolder() { return scriptFolder; }
            @Override
            public void setScriptsFolder(Path path) { scriptFolder = path; }
            @Override
            public Path getConfigurationFolder() { return configFolder; }
            @Override
            public void setConfigurationFolder(Path path) { configFolder = path; }
        });
        final String logLevel = Config.getProperty("log.level");
        if (logLevel != null) {
            Logger rootLogger = LogManager.getLogManager().getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            Level newLevel = Level.parse(logLevel);
            rootLogger.setLevel(newLevel);
            for (Handler h : handlers) {
                h.setLevel(newLevel);
            }
        }
        try {
            Database.checkDatasource();
            Database.checkConfig();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        try {
            Database.checkProductStats();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
