package com.ibm.jesseg.prometheus;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.AS400JDBCDriver;

public class ConnectionManager {
  private Connection m_connection;
  private final Config m_config;

  public ConnectionManager(final Config _config) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, SQLException, IOException {
        Driver driver = (Driver)Class.forName(_config.getDriverClass()).getDeclaredConstructor().newInstance();
        DriverManager.registerDriver(driver);
        m_config = _config;
        m_connection = getConnection();

  }

  public Connection getConnection() throws SQLException, IOException {
    if(null != m_connection && !m_connection.isClosed()) {
      return m_connection;
    }
    if(m_config.getDriverClass().equals(AS400JDBCDriver.class.getName())) {
      AS400 as400 = new AS400(m_config.getHostNameForConnection(), m_config.getUsername(), m_config.getPassword());
      try {
        as400.setGuiAvailable(false);
      } catch (PropertyVetoException e1) {
        throw new SQLException(e1);
      }
      return m_connection = new AS400JDBCDataSource(as400).getConnection();
    }
    return m_connection = DriverManager.getConnection(m_config.getHostNameForConnection(), m_config.getUsername(), m_config.getPassword());
  }

}
