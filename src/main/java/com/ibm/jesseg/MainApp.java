package com.ibm.jesseg;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.InetSocketAddress;

import com.ibm.jesseg.Config.SQLQuery;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;

public class MainApp {

    public static void main(String[] args) {
        try {
            CollectorRegistry registry = CollectorRegistry.defaultRegistry;

            File jsonConfig = new File("config.json");
            Config config = new Config(jsonConfig);
            for (SQLQuery query : config.getSQLQueries()) {
                SQLMetricPopulator sqlMetrics = new SQLMetricPopulator(registry, config, query.getInterval(),
                        query.getSql());
                sqlMetrics.run();
            }
            com.sun.net.httpserver.HttpServer rootServer = com.sun.net.httpserver.HttpServer.create();
            rootServer.bind(new InetSocketAddress(8910), 100);
            HTTPServer server = new HTTPServer.Builder()
                    // .withPort(8910)
                    .withHttpServer(rootServer)
                    .build();

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
    }
}