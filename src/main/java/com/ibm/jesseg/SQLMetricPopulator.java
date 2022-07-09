package com.ibm.jesseg;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.ibm.as400.access.AS400JDBCDataSource;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;

public class SQLMetricPopulator {
  private final Object m_requestLock = new ReentrantLock();
  private final String m_sql;
  private volatile long m_numCollections = 0;

  private Connection m_connection = null;

  private final AS400JDBCDataSource m_datasource;

  private final long m_interval;

  private PreparedStatement m_statement = null;

  private final String m_systemName;
  private Map<Integer, Gauge> m_gauges = new HashMap<Integer, Gauge>();
  private volatile long m_lastSuccessTs = 0;

  public SQLMetricPopulator(CollectorRegistry _registry, Config _config, long _interval, String _sql) {
    m_sql = _sql;
    m_interval = _interval;
    if (isIBMi()) {
      m_datasource = new AS400JDBCDataSource("localhost", "*CURRENT", "*CURRENT");
      String localhost = "unknown";
      try {
        localhost = InetAddress.getLocalHost().getHostName().replaceAll("\\..*", "");
      } catch (UnknownHostException e) {
        e.printStackTrace();
      }
      m_systemName = localhost;
    } else {
      String hostname = _config.getHostName();
      String username = _config.getUsername();
      String password = _config.getPassword();
      m_datasource = new AS400JDBCDataSource(hostname, username,password);
      m_systemName = hostname;
    }
  }

  private boolean isIBMi() {
    String osname = System.getProperty("os.name", "").toLowerCase();
    return osname.equals("os400") || osname.equals("os/400");
  }

  public synchronized void run() throws SQLException {
    PreparedStatement statement = getStatement();
    ResultSetMetaData metadata = statement.getMetaData();
    int columnCount = metadata.getColumnCount();

    for (int i = 1; i <= columnCount; i++) {
      String columnName = metadata.getColumnName(i);
      String columnTypeStr = metadata.getColumnTypeName(i);
      int columnType = metadata.getColumnType(i);
      String columnLabel = metadata.getColumnLabel(i);
      if (!isColumnTypeNumeric(columnType)) {
        continue;
      }
      System.out.println("registering collector for " + columnName + " of type " + columnTypeStr);
      Gauge columnGauge = Gauge.build()
          .name(m_systemName + "__" + columnName).help(columnLabel).register();
      m_gauges.put(i, columnGauge);
    }
    if (0 == m_gauges.size()) {
      System.err.println("No numeric data for SQL: " + m_sql);
      return;
    }

    Thread sqlThread = new Thread(() -> {
      while (true) {
        gatherData();
        try {
          Thread.sleep(1000 * m_interval);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    });
    sqlThread.start();
  }

  private void gatherData() {
    synchronized (m_requestLock) {

      System.out.println("gathering metrics...");
      try {
        ResultSet rs = getStatement().executeQuery();
        if (rs.next()) {
          int columnCount = rs.getMetaData().getColumnCount();
          for (int i = 1; i <= columnCount; i++) {
            Gauge gauge = m_gauges.get(i);
            if (null == gauge) {
              continue;
            }
            double value = rs.getDouble(i);
            gauge.set(value);
          }
        }
        rs.close();
        m_numCollections++;
      } catch (SQLException e) {
        e.printStackTrace();
      }
      m_lastSuccessTs = System.currentTimeMillis();
    }
  }

  private PreparedStatement getStatement() throws SQLException {
    if (null == m_connection || m_connection.isClosed()) {
      m_connection = m_datasource.getConnection();
    }
    if (null == m_statement || m_statement.isClosed()) {
      m_statement = m_connection.prepareStatement(m_sql);
    }
    return m_statement;
  }

  public void gatherNow(int _millisTolerance) {
    if (System.currentTimeMillis() - m_lastSuccessTs > _millisTolerance) {
      gatherData();
    }
  }

  private boolean isColumnTypeNumeric(int _columnType) {

    switch (_columnType) {
      case java.sql.Types.BIGINT:
      case java.sql.Types.INTEGER:
      case java.sql.Types.DOUBLE:
      case java.sql.Types.FLOAT:
      case java.sql.Types.NUMERIC:
      case java.sql.Types.REAL:
      case java.sql.Types.SMALLINT:
      case java.sql.Types.TINYINT:
      case java.sql.Types.DECIMAL:
        return true;
    }
    return false;
  }

}