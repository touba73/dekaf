package org.jetbrains.jdba.jdbc;

/**
 * @author Leonid Bushuev from JetBrains
 */

import org.junit.runner.RunWith;
import org.junit.runners.Suite;



@RunWith(Suite.class)
@Suite.SuiteClasses({
                            PostgreInterServiceProviderTest.class
})
public class PostgreJdbcIntegrationTests {

  static {
    System.setProperty("java.awt.headless", "true");
  }

}
