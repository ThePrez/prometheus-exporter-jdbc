package com.ibm.jesseg;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ConsoleQuestionAsker;
import com.ibm.jesseg.Config.SQLQuery;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;

public class MainApp {

    public static void main(String[] args) {
        AppLogger logger = AppLogger.getSingleton(Boolean.getBoolean("promclient.verbose"));
        com.sun.net.httpserver.HttpServer rootServer = null;

        try {

            CollectorRegistry registry = CollectorRegistry.defaultRegistry;

            File jsonConfig = getConfigFile(logger);
            Config config = new Config(logger, jsonConfig);
            int port = config.getPort(logger);

            List<SQLMetricPopulator> populators = new LinkedList<SQLMetricPopulator>();
            for (SQLQuery query : config.getSQLQueries()) {
                populators.add(new SQLMetricPopulator(logger, registry, config,
                        query.getInterval(),
                        query.getSql()));
            }
            rootServer = com.sun.net.httpserver.HttpServer.create();

            try {
                rootServer.bind(new InetSocketAddress(port), 100);
            } catch (BindException e) {
                throw new IOException("Port " + port + " is already in use", e);
            }
            logger.println("Verifying metrics collection....");
            for (SQLMetricPopulator populator : populators) {
                populator.gatherNow(12);
            }
            logger.println_success("Metrics collection verified.");
            
            HTTPServer server = new HTTPServer.Builder()
                    .withHttpServer(rootServer)
                    .build();
                    
            String successMessage = "\n\n\n";
            successMessage += "==============================================================\n";
            successMessage += "Successfully started Prometheus client on port " + port + "\n";
            successMessage += "==============================================================\n";
            logger.println_success(successMessage);
        } catch (Exception e) {

            logger.printfln_err("ERROR: %s", e.getLocalizedMessage());
            logger.printExceptionStack_verbose(e);
            if (null != rootServer) {
                rootServer.stop(0);
            }
        }
    }

    private static File writeResourcesToFile(AppLogger _logger, File _dest, String _resource) throws IOException {
        InputStream in = MainApp.class.getResourceAsStream(_resource);
        if (null == in) {
            throw new IOException("Could not find resource to create "+_dest.getName());
        }
        try(FileOutputStream out = new FileOutputStream(_dest, false)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
        return _dest;
    }
    private static File getConfigFile(AppLogger _logger) throws IOException {
        File ret = new File(System.getProperty("promclient.config", "config.json"));
        if (ret.exists()) {
            _logger.printfln("Using config file: %s", ret.toString());
            return ret.getAbsoluteFile();
        }
        ConsoleQuestionAsker asker = ConsoleQuestionAsker.get();
        boolean isCreatingDefault = asker.askBooleanQuestion(
                _logger, "y", "Configuration file %s not found. Would you like to initialize one with defaults?",
                ret.toString());

        if (!isCreatingDefault) {
            throw new IOException("No configuration file");
        }
        return writeResourcesToFile(_logger, ret, "config.json");
    }
}