package com.opticareer.app;

import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
class ResumeBuilderApplicationTests {

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    DataSource mockDataSource() throws Exception {
      DataSource ds = mock(DataSource.class);
      Connection connection = mock(Connection.class);
      DatabaseMetaData metaData = mock(DatabaseMetaData.class);
      when(ds.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
      when(metaData.getDatabaseMajorVersion()).thenReturn(16);
      when(metaData.getDatabaseMinorVersion()).thenReturn(0);
      return ds;
    }
  }

  @Test
  void contextLoads() {
  }
}
