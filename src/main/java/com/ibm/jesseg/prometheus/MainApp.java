package com.ibm.jesseg.prometheus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ConsoleQuestionAsker;
import com.ibm.jesseg.prometheus.Config.SQLQuery;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;

public class MainApp {

    public static void main(String[] _args) {
        AppLogger logger = AppLogger.getSingleton(!Boolean.getBoolean("promclient.verbose"));

        List<String> args = Arrays.asList(_args);
        if (args.contains("sc")) {
            try {
                File yaml = writeResourcesToFile(logger, new File("prometheus.yml"), "prometheus.yml");
                logger.println_success("Wrote Service Commander definition to file " + yaml.getAbsolutePath());
                logger.println("");
                logger.println("To register with Service Commander, first install Service Commander");
                logger.println("version 1.5.0 or newer. ");
                logger.println("");
                logger.println("Then, run the following command to register this service for the current user:");
                logger.printfln("    ln -sf %s $HOME/.sc/services/%s", yaml.getAbsolutePath(), yaml.getName());
                logger.println("");
                logger.println("Or, run the following command to register this service for all users:");
                logger.printfln("    ln -sf %s /QOpenSys/etc/sf/services/%s", yaml.getAbsolutePath(), yaml.getName());
            } catch (Exception e) {
                logger.printfln_err("Error writing prometheus.yml to file: %s", e.getMessage());
                System.exit(-3);
            }
            return;
        }

        Server server = null;
        int port = -1;
        try {

            CollectorRegistry registry = CollectorRegistry.defaultRegistry;

            File jsonConfig = getConfigFile(logger);
            Config config = new Config(logger, jsonConfig);
            port = config.getPort(logger);

            List<SQLMetricPopulator> populators = new LinkedList<SQLMetricPopulator>();
            final List<SQLQuery> queries = config.getSQLQueries();
            for (SQLQuery query : queries) {
                populators.add(new SQLMetricPopulator(logger, registry, config,
                        new ConnectionManager(config),
                        query.getInterval(),
                        query.isMultiRow(),
                        query.getSql(),
                        query.getShowHostname(),
                        query.getGaugePrefix()));
            }
            logger.println("Verifying metrics collection....");
            for (SQLMetricPopulator populator : populators) {
                populator.gatherNow(12);
            }
            logger.println_success("Metrics collection verified.");

            ServletContextHandler context = new ServletContextHandler();
            context.setContextPath("/");
            server = new Server(port);
            server.setHandler(context);
            MetricsServlet metricsServlet = new MetricsServlet(registry);

            ServletHolder nowGatherer = new ServletHolder(metricsServlet) {
                public void handle(org.eclipse.jetty.server.Request baseRequest, javax.servlet.ServletRequest request,
                        javax.servlet.ServletResponse response)
                        throws javax.servlet.ServletException, javax.servlet.UnavailableException, IOException {
                    for (SQLMetricPopulator queryPopulator : populators) {
                        try {
                            queryPopulator.gatherNow(4);
                        } catch (SQLException e) {
                            throw new IOException(e);
                        }
                    }
                    super.handle(baseRequest, request, response);
                };
            };

            context.addServlet(new ServletHolder(metricsServlet), "/metrics");
            context.addServlet(nowGatherer, "/metrics_now");
            context.addServlet(new ServletHolder(metricsServlet), "/");
            server.start();

            String successMessage = "\n\n\n";
            successMessage += "==============================================================\n";
            successMessage += "Successfully started Prometheus client on port " + port + "\n";
            successMessage += "==============================================================\n";
            logger.println_success(successMessage);

            server.join();
        } catch (Exception e) {
            if (e instanceof BindException) {
                logger.println_err("Port " + port + " is already in use");
            }
            logger.printfln_err("ERROR: %s", e.getLocalizedMessage());
            logger.printExceptionStack_verbose(e);
            if (null != server) {
                try {
                    server.stop();
                } catch (Exception e1) {
                    logger.printfln_err("ERROR: %s", e1.getLocalizedMessage());
                    logger.printExceptionStack_verbose(e);
                }
            }
        }
    }

    private static File writeResourcesToFile(AppLogger _logger, File _dest, String _resource) throws IOException {
        InputStream in = MainApp.class.getResourceAsStream(_resource);
        if (null == in) {
            throw new IOException("Could not find resource to create " + _dest.getName());
        }
        try (FileOutputStream out = new FileOutputStream(_dest, false)) {
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