package com.opticareer.app.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opticareer.app.modules.identity.application.IdentityService;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "springdoc.api-docs.enabled=true",
    "springdoc.swagger-ui.enabled=true"
})
class OpenApiDocumentationTest {

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    DataSource mockDataSource() throws Exception {
      DataSource ds = Mockito.mock(DataSource.class);
      Connection connection = Mockito.mock(Connection.class);
      DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
      Mockito.when(ds.getConnection()).thenReturn(connection);
      Mockito.when(connection.getMetaData()).thenReturn(metaData);
      Mockito.when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
      Mockito.when(metaData.getDatabaseMajorVersion()).thenReturn(16);
      Mockito.when(metaData.getDatabaseMinorVersion()).thenReturn(0);
      return ds;
    }
  }

  @MockitoBean
  private IdentityService identityService;

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  @DisplayName("Swagger OpenAPI documentation endpoint /v3/api-docs is available and documents auth registration with fullName field")
  void openApiDocsAvailable() throws Exception {
    String response =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(response).contains("OptiCareer API");
    assertThat(response).contains("/api/v1/auth/register");
    assertThat(response).contains("Register user account");
    assertThat(response).contains("Authentication");
    assertThat(response).contains("fullName");
    assertThat(response).contains("Emran Hossain");
    assertThat(response).contains("RegisterRequest");
    assertThat(response).contains("RegisterResponse");
    assertThat(response).contains("Step-Up Auth (PSD2 SCA)");
    assertThat(response).contains("WWW-Authenticate: StepUp");
    assertThat(response).contains("/api/v1/resume-analysis/upload");
    assertThat(response).contains("/api/v1/resume-analysis/{fileId}/analyze");
    assertThat(response).contains("/api/v1/resume-analysis/{id}");
    assertThat(response).contains("/api/v1/resume-analysis/history");
    assertThat(response).contains("UploadResumeResponse");
    assertThat(response).contains("ResumeAnalysisResponse");
    assertThat(response).contains("ScoreDetailsDto");
    assertThat(response).contains("AiSuggestionDto");
    assertThat(response).contains("Resume Analysis & Async Queue Contract");
  }

  @Test
  @DisplayName("Swagger UI page is accessible")
  void swaggerUiAccessible() throws Exception {
    mockMvc
        .perform(get("/swagger-ui/index.html"))
        .andExpect(status().isOk());
  }
}
