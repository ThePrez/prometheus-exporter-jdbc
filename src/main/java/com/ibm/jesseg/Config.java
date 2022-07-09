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

public class Config {

  private final JSONObject m_json;

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

  public Config(final File _file) throws IOException, ParseException {
    try (Reader reader = new FileReader(_file)) {
      JSONParser parser = new JSONParser();
      m_json = (JSONObject) parser.parse(reader);
    }
  }

  public List<SQLQuery> getSQLQueries() {
    List<SQLQuery> ret = new LinkedList<SQLQuery>();
    try {
      final JSONArray queries = (JSONArray) m_json.get("queries");
      if (null == queries) {
        System.err.println("no queries found");
        return ret;
      }
      int size = queries.size();
      for (int i = 0; i < size; i++) {
        final JSONObject query = (JSONObject) queries.get(i);
        final String sql = (String) query.get("sql");
        final long interval = (long) query.get("interval");
        ret.add(new SQLQuery(sql, interval));
      }
    } catch (Exception e) {
      System.err.println("Error reading JSON file");
      e.printStackTrace();
    }
    return ret;
  }

  public String getHostName() {
    return m_json.get("hostname").toString();
  }

  public String getPassword() {
    return m_json.get("password").toString();
  }

  public String getUsername() {
    return m_json.get("username").toString();
  }
}
