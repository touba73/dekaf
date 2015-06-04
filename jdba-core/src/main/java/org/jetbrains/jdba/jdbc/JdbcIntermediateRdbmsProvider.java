package org.jetbrains.jdba.jdbc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jdba.exceptions.DBInitializationException;
import org.jetbrains.jdba.intermediate.DBExceptionRecognizer;
import org.jetbrains.jdba.intermediate.IntegralIntermediateRdbmsProvider;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static org.jetbrains.jdba.util.Objects.castTo;



/**
 * @author Leonid Bushuev from JetBrains
 */
public abstract class JdbcIntermediateRdbmsProvider implements IntegralIntermediateRdbmsProvider {


  /**
   * Unfortunately, JDBC framework doesn't provide constant for this strange text 8-/
   */
  private static final String DAMN_JDBC_DRIVER_NOT_REGISTERED_ERROR_STATE = "08001";


  @NotNull
  @Override
  public JdbcIntermediateFacade openFacade(@NotNull final String connectionString,
                                           @Nullable final Properties connectionProperties,
                                           final int connectionsLimit) {
    Driver driver = getDriver(connectionString);
    return instantiateFacade(connectionString,
                             connectionProperties,
                             connectionsLimit,
                             driver);
  }

  @NotNull
  protected JdbcIntermediateFacade instantiateFacade(@NotNull final String connectionString,
                                                     @Nullable final Properties connectionProperties,
                                                     final int connectionsLimit,
                                                     @NotNull final Driver driver) {
    BaseExceptionRecognizer exceptionRecognizer = getExceptionRecognizer();
    return new JdbcIntermediateFacade(connectionString, connectionProperties, driver, connectionsLimit, exceptionRecognizer);
  }


  protected Driver getDriver(@NotNull final String connectionString) {
    tryToLoadDriverIfNeeded();
    try {
      return DriverManager.getDriver(connectionString);
    }
    catch (SQLException sqle) {
      throw getExceptionRecognizer().recognizeException(sqle,
                                                    "DriverManager.getDriver for: " + connectionString);
    }
  }

  protected void tryToLoadDriverIfNeeded() {
    String connectionStringExample = getConnectionStringExample();
    if (connectionStringExample != null) {
      if (!whetherApplicableDriverAlreadyRegistered(connectionStringExample)) {
        Driver driver = loadDriver();
        if (driver != null) {
          registerDriver(driver);
        }
      }
    }
  }

  @Nullable
  protected abstract String getConnectionStringExample();

  @Nullable
  protected abstract Driver loadDriver();


  protected boolean whetherApplicableDriverAlreadyRegistered(@NotNull final String connectionString) {
    try {
      DriverManager.getDriver(connectionString);
      return true;
    }
    catch (SQLException sqle) {
      if (!sqle.getSQLState().equals(DAMN_JDBC_DRIVER_NOT_REGISTERED_ERROR_STATE)) {
        // TODO log it somehow
      }
      return false;
    }
  }


  protected void registerDriver(final Driver driver) {
    try {
      DriverManager.registerDriver(driver);
    }
    catch (SQLException sqle) {
      throw new DBInitializationException("Failed to register JDBC Driver", sqle);
    }
  }


  protected Class<Driver> getSimpleAccessibleDriverClass(@NotNull final String driverClassName) {
    Class<Driver> driverClass;
    try {
      //noinspection unchecked
      return (Class<Driver>) Class.forName(driverClassName);
    }
    catch (ClassNotFoundException e) {
      return null;
    }
  }


  @NotNull
  public abstract BaseExceptionRecognizer getExceptionRecognizer();

  @NotNull
  @Override
  public Class<? extends DBExceptionRecognizer> getExceptionRecognizerClass() {
    DBExceptionRecognizer er = getExceptionRecognizer();
    return er.getClass();
  }

  @Nullable
  @Override
  public <I> I getSpecificService(@NotNull final Class<I> serviceClass,
                                  @NotNull final String serviceName) {
    if (serviceName.equalsIgnoreCase(Names.INTERMEDIATE_SERVICE)) {
      return castTo(serviceClass, this);
    }
    else {
      return null;
    }
  }
}
