package com.ibm.jesseg;

import java.beans.PropertyVetoException;
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
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.ibm.as400.access.AS400;
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
  private Map<String, Gauge> m_gauges = new HashMap<String, Gauge>();
  private volatile long m_lastSuccessTs = 0;
  private final AppLogger m_logger;
  private final Thread m_sqlThread;
  private final CollectorRegistry m_registry;
  private boolean m_includeHostname;
  private String m_gaugePrefix;
  private boolean m_isMultiRow;

  public SQLMetricPopulator(AppLogger _logger, CollectorRegistry _registry, Config _config, long _interval,
      boolean _isMultiRow,
      String _sql, boolean _includeHostname, String _gaugePrefix)
      throws IOException, SQLException {
    m_logger = _logger;
    m_sql = _sql;
    m_interval = _interval;
    m_registry = _registry;
    m_includeHostname = _includeHostname;
    m_gaugePrefix = _gaugePrefix;
    m_isMultiRow = _isMultiRow;
    final AS400 as400;
    if (isIBMi()) {
      as400 = new AS400("localhost", "*CURRENT", "*CURRENT");
      String localhost = "unknown";
      try {
        localhost = InetAddress.getLocalHost().getHostName();
      } catch (Exception e) {
        m_logger.printfln_warn("WARNING: could not resolve local host name (%s)", e.getLocalizedMessage());
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
      as400 = new AS400(hostname, username, password);

      m_systemName = hostname;
    }
    try {
      as400.setGuiAvailable(false);
    } catch (PropertyVetoException e1) {
      m_logger.printExceptionStack_verbose(e1);
    }
    m_datasource = new AS400JDBCDataSource(as400);

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

    if (!m_isMultiRow) {
      for (int i = 1; i <= columnCount; i++) {
        String columnName = metadata.getColumnName(i);
        String columnTypeStr = metadata.getColumnTypeName(i);
        int columnType = metadata.getColumnType(i);
        String columnLabel = metadata.getColumnLabel(i);
        if (!isColumnTypeNumeric(columnType)) {
          continue;
        }
        String gaugeName = getGaugeName(columnName, null);
        m_logger.printfln_verbose("registering collector: column %s of type %s (gauge name '%s')", columnName,
            columnTypeStr, gaugeName);
        getGauge(gaugeName, columnLabel);
        if (0 == m_gauges.size()) {
          m_logger.println_warn("No numeric data for SQL: " + m_sql);
          return;
        }
      }
    }
  }

  private Gauge getGauge(String _gaugeName, final String _help) {
    Gauge gauge = m_gauges.get(_gaugeName);
    if (null != gauge) {
      return gauge;
    }
    m_logger.printfln_verbose("registering gauge: %s", _gaugeName);
    Gauge ret = Gauge.build()
        .name(_gaugeName).help(_help).register();
    m_gauges.put(_gaugeName, ret);
    return ret;
  }

  private String getGaugeName(String _columnName, String _rowName) {
    String ret = "";
    if (m_includeHostname) {
      ret += m_systemName.replaceAll("\\..*", "") + "__";
    }
    if (StringUtils.isNonEmpty(m_gaugePrefix)) {
      ret += m_gaugePrefix + "__";
    }
    if (StringUtils.isNonEmpty(_rowName)) {
      ret += _rowName + "__";
    }
    ret += _columnName;
    return ret.replaceAll("[^_A-Za-z0-9]", "");
  }

  private boolean isIBMi() {
    String osname = System.getProperty("os.name", "").toLowerCase();
    return osname.equals("os400") || osname.equals("os/400");
  }

  private void gatherData() throws SQLException {
    synchronized (m_requestLock) {
      if (!m_isMultiRow &&  0 == m_gauges.size()) {
        return;
      }
      m_logger.println_verbose("gathering metrics..."+m_sql);
      try {
        ResultSet rs = getStatement().executeQuery();
        ResultSetMetaData meta = rs.getMetaData();
        while (rs.next()) {
          int columnCount = meta.getColumnCount();
          String multiRowName = "";
          for (int i = 1; i <= columnCount; i++) {
            String columnName = meta.getColumnName(i);
            final Gauge gauge;
            if (m_isMultiRow) {
              if (1 == i) {
                multiRowName = rs.getString(i);
                continue;
              }
              if (!isColumnTypeNumeric(meta.getColumnType(i))) {
                continue;
              }
              String gaugeName = getGaugeName(columnName, multiRowName);
              gauge = getGauge(gaugeName, columnName);
            } else {
              if (!isColumnTypeNumeric(meta.getColumnType(i))) {
                continue;
              }
              gauge = m_gauges.get(getGaugeName(columnName, null));
              if (null == gauge) {
                continue;
              }
            }
            double value = rs.getDouble(i);
            gauge.set(value);
          }
          if(!m_isMultiRow) {
            break;
          }
        }
        rs.close();
        m_numCollections++;
        m_lastSuccessTs = System.currentTimeMillis();
        if (1 == m_numCollections) {
          m_sqlThread.start();
        }
      } catch (Exception e) {
        m_logger.println_err("ERROR!! ABORTING COLLECTION!! Cause: " + e.getLocalizedMessage());
        m_logger.printExceptionStack_verbose(e);
        for (Entry<String, Gauge> gaugeEntry : m_gauges.entrySet()) {
          Gauge gauge = gaugeEntry.getValue();
          m_registry.unregister(gauge);
        }
        m_gauges.clear();
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