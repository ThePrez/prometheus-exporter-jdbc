package com.ibm.jesseg;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ConsoleQuestionAsker;
import com.github.theprez.jcmdutils.StringUtils;

public class Config {

  public static final int DEFAULT_PORT = 8910;
  private final JSONObject m_json;
  private AppLogger m_logger;
  private File m_file;
  private String m_password;
  private String m_hostname;
  private String m_username;

  public static class SQLQuery {
    public final String m_sql;
    public final long m_interval;

    public SQLQuery(String _sql, long _interval) {
      m_sql = _sql;
      m_interval = _interval;
    }

    public long getInterval() {
      return m_interval;
    }

    public String getSql() {
      return m_sql;
    }
  }

  public Config(final AppLogger _logger, final File _file) throws IOException, ParseException {
    m_logger = _logger;
    m_file = _file;
    try (Reader reader = new FileReader(_file)) {
      JSONParser parser = new JSONParser();
      m_json = (JSONObject) parser.parse(reader);
    }
  }

  public List<SQLQuery> getSQLQueries() {
    List<SQLQuery> ret = new LinkedList<SQLQuery>();
    final JSONArray queries = (JSONArray) m_json.get("queries");
    if (null == queries) {
      m_logger.printfln_err("No queries found in config file %s", m_file.getAbsolutePath());
      return ret;
    }
    int size = queries.size();
    for (int i = 0; i < size; i++) {
      final JSONObject query = (JSONObject) queries.get(i);
      final String sql = (String) query.get("sql");
      if (StringUtils.isEmpty(sql)) {
        m_logger.printfln_err("No SQL found for query in config file %s", m_file.getAbsolutePath());
        continue;
      }
      Object intervalObj = query.get("interval");
      if (null == intervalObj) {
        m_logger.printfln_err("No interval found for query '%s' in config file %s", sql, m_file.getAbsolutePath());
        continue;
      }
      final long interval = (long) intervalObj;
      ret.add(new SQLQuery(sql, interval));
    }
    return ret;
  }

  public String getHostName() {
    if (StringUtils.isNonEmpty(m_hostname)) {
      return m_hostname;
    }
    Object val = m_json.get("hostname");
    if (null == val) {
      return m_hostname = ConsoleQuestionAsker.get().askNonEmptyStringQuestion(m_logger, "", "Enter system name: ");
    }
    return m_hostname = val.toString();
  }

  public String getPassword() throws IOException {
    if (StringUtils.isNonEmpty(m_password)) {
      return m_password;
    }
    Object val = m_json.get("password");
    if (null == val) {
      String pwEnv = System.getenv("PASSWORD");
      if(StringUtils.isNonEmpty(pwEnv)) {
        return m_password=pwEnv;
      }
      return m_password = ConsoleQuestionAsker.get().askUserForPwd("Password: ");
    }
    m_logger.printfln_warn("WARNING: Password is stored in config file %s. THIS IS NOT SECURE!", m_file.getAbsolutePath());
    return m_password = val.toString();
  }

  public String getUsername() {
    if (StringUtils.isNonEmpty(m_username)) {
      return m_username;
    }
    Object val = m_json.get("username");
    if (null == val) {
      return m_username = ConsoleQuestionAsker.get().askNonEmptyStringQuestion(m_logger, "", "Username:");
    }
    return m_username = val.toString();
  }

  public int getPort(AppLogger _logger) {
    String portEnv = System.getenv("PORT");
    if (StringUtils.isNonEmpty(portEnv)) {
      try {
        return Integer.parseInt(portEnv);
      } catch (NumberFormatException e) {
        _logger.printfln_warn("Invalid port number in PORT environment variable: " + portEnv);
      }
    }
    String portProp = System.getProperty("promclient.port", "");
    if (StringUtils.isNonEmpty(portProp)) {
      try {
        return Integer.parseInt(portProp);
      } catch (NumberFormatException e) {
        _logger.printfln_warn("Invalid port number in 'promclient.port' property: " + portProp);
      }
    }
    Object val = m_json.get("port");
    try {
      return ((Number) val).intValue();
    } catch (Exception e) {
      _logger.printfln_err("Unable to use default port. Falling back to tool default");
      return DEFAULT_PORT;
    }
  }
}
