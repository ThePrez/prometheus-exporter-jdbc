package com.ibm.jesseg;

import java.io.IOException;
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

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
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
  private final AppLogger m_logger;
  private final Thread m_sqlThread;

  public SQLMetricPopulator(AppLogger _logger, CollectorRegistry _registry, Config _config, long _interval, String _sql)
      throws IOException, SQLException {
    m_logger = _logger;
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
      if (StringUtils.isEmpty(hostname)) {
        throw new IOException("hostname is required");
      }
      if (StringUtils.isEmpty(username)) {
        throw new IOException("username is required");
      }
      if (StringUtils.isEmpty(password)) {
        throw new IOException("password is required");
      }
      m_datasource = new AS400JDBCDataSource(hostname, username, password);
      m_systemName = hostname;
    }
    m_sqlThread = new Thread(() -> {
      while (true) {
        try {
          Thread.sleep(1000 * m_interval);
          gatherData();
        } catch (InterruptedException | SQLException e) {
          m_logger.exception(e);
        }
      }
    });
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
      m_logger.println_verbose("registering collector for " + columnName + " of type " + columnTypeStr);
      Gauge columnGauge = Gauge.build()
          .name(m_systemName + "__" + columnName).help(columnLabel).register();
      m_gauges.put(i, columnGauge);
    }
    if (0 == m_gauges.size()) {
      m_logger.println_warn("No numeric data for SQL: " + m_sql);
      return;
    }

    
  }

  private boolean isIBMi() {
    String osname = System.getProperty("os.name", "").toLowerCase();
    return osname.equals("os400") || osname.equals("os/400");
  }

  private void gatherData() throws SQLException {
    synchronized (m_requestLock) {
      if(0 == m_gauges.size()) {
        return;
      }
      m_logger.println_verbose("gathering metrics...");
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
      m_lastSuccessTs = System.currentTimeMillis();
      if(1 == m_numCollections) {
        m_sqlThread.start();
      }
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

  public void gatherNow(int _millisTolerance) throws SQLException {
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