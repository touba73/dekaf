package org.jetbrains.jdba.jdbc;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;



/**
 * @author Leonid Bushuev from JetBrains
 */
public class BaseHyperSonicCase {

  protected static final String HSQL_CONNECTION_STRING = "jdbc:hsqldb:mem:mymemdb?user=SA";

  protected static Driver ourHSDriver;


  @BeforeClass
  public static void instantiateDriver() {
    System.setProperty("java.awt.headless", "true");

    try {
      //noinspection unchecked
      Class<Driver> driverClass = (Class<Driver>) Class.forName("org.hsqldb.jdbc.JDBCDriver");
      ourHSDriver = driverClass.newInstance();
      DriverManager.registerDriver(ourHSDriver);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @AfterClass
  public static void deregisterDriver() throws SQLException {
    assert ourHSDriver != null;
    DriverManager.deregisterDriver(ourHSDriver);
  }


  protected static Connection obtainConnection() {
    assert ourHSDriver != null;
    final Connection connection;
    try {
      connection = ourHSDriver.connect(HSQL_CONNECTION_STRING, new Properties());
      connection.setAutoCommit(true);
    }
    catch (SQLException e) {
      throw new RuntimeException("Failed to get an HSQL connection: "+e.getMessage(), e);
    }
    return connection;
  }


}
