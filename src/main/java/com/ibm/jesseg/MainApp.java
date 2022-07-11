package com.ibm.jesseg;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
            HTTPServer server = new HTTPServer.Builder()
                    .withHttpServer(rootServer)
                    .build();
            logger.println("Verifying metrics collection....");
            for (SQLMetricPopulator populator : populators) {
                populator.gatherNow(12);
            }
            logger.println_success("Metrics collection verified.");
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
        String fileContents = "{\n" +
                "  \"port\": 8910,\n" +
                "  \"queries\": [\n" +
                "    {\n" +
                "      \"name\": \"System Statistics\",\n" +
                "      \"interval\": 60,\n" +
                "      \"sql\": \"SELECT * FROM TABLE(QSYS2.SYSTEM_STATUS(RESET_STATISTICS=>'YES',DETAILED_INFO=>'ALL')) X\"\n"
                +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"number of remote connections\",\n" +
                "      \"interval\": 60,\n" +
                "      \"sql\": \"select COUNT(REMOTE_ADDRESS) as REMOTE_CONNECTIONS from qsys2.netstat_info where TCP_STATE = 'ESTABLISHED' AND REMOTE_ADDRESS != '::1' AND REMOTE_ADDRESS != '127.0.0.1'\"\n"
                +
                "    }\n" +
                "  ]\n" +
                "}";
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(ret), "UTF-8")) {
            out.write(fileContents);
            return ret.getAbsoluteFile();
        }
    }
}