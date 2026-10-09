package com.getlancer.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.getlancer.commerce.CommerceRepository;
import com.getlancer.hosting.HostingConfiguration;
import com.getlancer.hosting.HostingRepository;
import com.getlancer.maintenance.MaintenanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.env.MockEnvironment;

/** Real PostgreSQL identities, all migrations, and production runtime permission failures. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseRoleGuardIntegrationTest {
  private static final String SCHEMA = "getlancer_roles_test";
  private String url, admin, adminPassword;
  private final String runtimePassword = "synthetic-runtime-" + UUID.randomUUID();
  private final String migrationPassword = "synthetic-migration-" + UUID.randomUUID();
  private final String backupPassword = "synthetic-backup-" + UUID.randomUUID();
  private final String readonlyPassword = "synthetic-readonly-" + UUID.randomUUID();
  private JdbcTemplate runtime;

  @BeforeAll void prepare() throws Exception {
    url = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5432/getlancer_test");
    admin = System.getenv().getOrDefault("TEST_DB_USERNAME", "postgres");
    adminPassword = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");
    assertEquals("true", System.getenv("TEST_DATABASE_RESET"), "Role tests require explicit disposable database authorization");
    assertTrue(url.matches("jdbc:postgresql://[^/?#]+/getlancer_test(?:\\?[^#]*)?"), "Role tests require the disposable getlancer_test database");
    adminSql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    adminSql("DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon NOLOGIN; END IF; IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated NOLOGIN; END IF; END $$");
    provision("00_roles.sql");
    adminSql("ALTER ROLE getlancer_runtime LOGIN PASSWORD '" + runtimePassword + "'");
    adminSql("ALTER ROLE getlancer_migration LOGIN PASSWORD '" + migrationPassword + "'");
    adminSql("ALTER ROLE getlancer_backup LOGIN PASSWORD '" + backupPassword + "'");
    adminSql("ALTER ROLE getlancer_readonly LOGIN PASSWORD '" + readonlyPassword + "'");
    Flyway.configure().dataSource(url, "getlancer_migration", migrationPassword).schemas(SCHEMA)
        .defaultSchema(SCHEMA).createSchemas(false).load().migrate();
    provision("10_permissions.sql"); provision("10_permissions.sql"); // Idempotent on real migrations.
    runtime = jdbc("getlancer_runtime", runtimePassword);
  }

  @AfterAll void cleanup() throws Exception {
    if (url != null && "true".equals(System.getenv("TEST_DATABASE_RESET"))) {
      adminSql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
      adminSql("ALTER ROLE getlancer_runtime NOLOGIN PASSWORD NULL");
      adminSql("ALTER ROLE getlancer_migration NOLOGIN PASSWORD NULL");
      adminSql("ALTER ROLE getlancer_backup NOLOGIN PASSWORD NULL");
      adminSql("ALTER ROLE getlancer_readonly NOLOGIN PASSWORD NULL");
    }
  }

  @BeforeEach void provisionedRuntimeStartsSafe() {
    assertDoesNotThrow(() -> guard(runtime));
  }

  private JdbcTemplate jdbc(String user, String password) {
    DriverManagerDataSource source = new DriverManagerDataSource(url, user, password);
    source.setConnectionProperties(new java.util.Properties() {{ setProperty("currentSchema", SCHEMA); }});
    return new JdbcTemplate(source);
  }
  private void guard(JdbcTemplate connection) {
    new DatabaseRoleGuard(connection, new MockEnvironment().withProperty("app.environment", "production")
        .withProperty("spring.datasource.hikari.schema", SCHEMA)).run(null);
  }
  private void assertRejected(JdbcTemplate connection, String check) {
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> guard(connection));
    assertTrue(failure.getMessage().endsWith("(check: " + check + ")"), failure.getMessage());
  }
  private void restoreProvisionedPermissions() throws Exception {
    // ALTER OWNER rewrites ACLs; moving ownership back alone does not restore runtime grants.
    provision("00_roles.sql");
    provision("10_permissions.sql");
    assertDoesNotThrow(() -> guard(runtime));
  }
  private void adminSql(String sql) throws Exception {
    try (Connection connection = DriverManager.getConnection(url, admin, adminPassword); Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
  private void provision(String name) throws Exception {
    Path root = Files.exists(Path.of("ops/database")) ? Path.of("ops/database") : Path.of("../ops/database");
    try (Connection connection = DriverManager.getConnection(url, admin, adminPassword); Statement statement = connection.createStatement()) {
      statement.execute("SET getlancer.app_schema='" + SCHEMA + "'");
      statement.execute(Files.readString(root.resolve(name)));
    }
  }

  @Test void reviewedRuntimeCanExecuteApplicationOperationsAndReadRlsTables() throws Exception {
    assertDoesNotThrow(() -> guard(runtime));
    List<String> tables = runtime.queryForList("SELECT tablename FROM pg_tables WHERE schemaname=? AND tablename<>'flyway_schema_history'", String.class, SCHEMA);
    assertTrue(tables.size() >= 90);
    for (String table : tables) assertDoesNotThrow(() -> runtime.queryForObject("SELECT count(*) FROM " + table, Integer.class), table);
    UUID user = UUID.randomUUID();
    runtime.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,?)", user, user + "@example.test", "synthetic-hash");
    runtime.update("INSERT INTO user_roles(user_id,role) VALUES(?,'CLIENT')", user);
    runtime.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", UUID.randomUUID().toString(), user);
    assertEquals(1, runtime.update("UPDATE users SET email_verified_at=now() WHERE id=?", user));
    assertEquals(1, runtime.update("DELETE FROM sessions WHERE user_id=?", user));
    runtime.update("INSERT INTO component_audit(id,kind,detail) VALUES(?,'ROLE_TEST','Synthetic permission fixture')", UUID.randomUUID());
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("UPDATE component_audit SET detail='changed'"));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("DELETE FROM component_audit"));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("DELETE FROM payment_ledger"));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.execute("TRUNCATE component_audit"));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.execute("CREATE TABLE forbidden_ddl(id int)"));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.execute("ALTER TABLE users DISABLE ROW LEVEL SECURITY"));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.execute("SET ROLE getlancer_migration"));
    for (String readRole : List.of("getlancer_backup", "getlancer_readonly")) {
      JdbcTemplate reader = jdbc(readRole, readRole.equals("getlancer_backup") ? backupPassword : readonlyPassword);
      assertDoesNotThrow(() -> reader.queryForObject("SELECT count(*) FROM users", Integer.class));
      assertDoesNotThrow(() -> reader.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class));
      assertThrows(org.springframework.dao.DataAccessException.class, () -> reader.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,?)", UUID.randomUUID(), "denied@example.test", "synthetic-hash"));
      assertThrows(org.springframework.dao.DataAccessException.class, () -> reader.execute("CREATE TABLE forbidden_reader_ddl(id int)"));
      assertEquals(Boolean.TRUE, runtime.queryForObject("SELECT has_table_privilege(?,?,'SELECT')", Boolean.class, readRole, SCHEMA+".users"));
      assertEquals(Boolean.FALSE, runtime.queryForObject("SELECT has_table_privilege(?,?,'INSERT')", Boolean.class, readRole, SCHEMA+".users"));
    }
    for (String directRole : List.of("anon", "authenticated")) {
      try (Connection connection = DriverManager.getConnection(url, admin, adminPassword); Statement statement = connection.createStatement()) {
        statement.execute("SET ROLE " + directRole);
        assertThrows(java.sql.SQLException.class, () -> statement.executeQuery("SELECT count(*) FROM " + SCHEMA + ".users"));
      }
    }
  }

  @Test void componentReleaseAndBookmarkPrivilegesRemainNarrow() throws Exception {
    UUID user = UUID.randomUUID(), component = UUID.randomUUID();
    runtime.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,?)", user, user+"@example.test", "synthetic-hash");
    runtime.update("INSERT INTO component_entries(id,owner_id,recipe_slug,slug,title,summary,contribution) VALUES(?,?,'portfolio-card',?,'Role fixture','Synthetic summary','Synthetic contribution')", component,user,"role-release-"+component);
    runtime.update("INSERT INTO component_releases(component_id,revision,source,context,source_sha256) VALUES(?,1,'{}','{}',?)",component,"a".repeat(64));
    assertEquals(1,runtime.queryForObject("SELECT count(*) FROM component_releases WHERE component_id=?",Integer.class,component));
    assertThrows(org.springframework.dao.DataAccessException.class,()->runtime.update("UPDATE component_releases SET source_sha256=? WHERE component_id=?","b".repeat(64),component));
    assertThrows(org.springframework.dao.DataAccessException.class,()->runtime.update("DELETE FROM component_releases WHERE component_id=?",component));
    UUID preview=UUID.randomUUID();
    runtime.update("INSERT INTO component_previews(deployment_id,component_id,revision,expires_at,archive_sha256,manifest_sha256) VALUES(?,?,1,now()+interval '7 days',?,?)",preview,component,"a".repeat(64),"b".repeat(64));
    assertEquals(1,runtime.update("UPDATE component_previews SET state='READY' WHERE deployment_id=?",preview));
    assertThrows(org.springframework.dao.DataAccessException.class,()->runtime.update("UPDATE component_previews SET expires_at=now()+interval '8 days' WHERE deployment_id=?",preview));
    assertThrows(org.springframework.dao.DataAccessException.class,()->runtime.update("DELETE FROM component_previews WHERE deployment_id=?",preview));
    assertEquals(1,runtime.update("UPDATE component_previews SET state='REVOKED' WHERE deployment_id=?",preview));
    assertThrows(org.springframework.dao.DataAccessException.class,()->runtime.update("UPDATE component_previews SET state='READY' WHERE deployment_id=?",preview));
    runtime.update("INSERT INTO saved_components(user_id,slug) VALUES(?,'portfolio-card') ON CONFLICT DO NOTHING",user);
    assertEquals(0,runtime.update("INSERT INTO saved_components(user_id,slug) VALUES(?,'portfolio-card') ON CONFLICT DO NOTHING",user));
    assertEquals(1,runtime.queryForObject("SELECT count(*) FROM saved_components WHERE user_id=?",Integer.class,user));
    assertThrows(org.springframework.dao.DataAccessException.class,()->runtime.update("UPDATE saved_components SET slug='changed' WHERE user_id=?",user));
    assertEquals(1,runtime.update("DELETE FROM saved_components WHERE user_id=?",user));
    for(String directRole:List.of("anon","authenticated")) {
      try(Connection connection=DriverManager.getConnection(url,admin,adminPassword);Statement statement=connection.createStatement()) {
        statement.execute("SET ROLE "+directRole);
        for(String table:List.of("saved_components","component_releases","component_previews"))
          assertThrows(java.sql.SQLException.class,()->statement.executeQuery("SELECT count(*) FROM "+SCHEMA+"."+table));
      }
    }
    try {
      adminSql("GRANT UPDATE ON "+SCHEMA+".component_releases TO getlancer_runtime");
      assertRejected(runtime,"immutable_mutation");
    } finally { provision("10_permissions.sql"); }
  }

  @Test void labCertificationAndAuditPrivilegesRemainNarrow() throws Exception {
    String id="role-manifest-"+UUID.randomUUID();
    JdbcTemplate migration = jdbc("getlancer_migration",migrationPassword);
    migration.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES(?,'{}',?,'fixture')",id,"a".repeat(64));
    TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(runtime.getDataSource()));
    transactions.executeWithoutResult(tx -> assertEquals(1,runtime.queryForList("SELECT id FROM lab_manifests WHERE id=? FOR SHARE",id).size()));
    assertThrows(org.springframework.dao.DataAccessException.class,()->runtime.update("INSERT INTO lab_manifests(id,payload_text,payload_sha256,signature) VALUES(?,'{}',?,'fixture')",id+"-forbidden","a".repeat(64)));
    assertThrows(org.springframework.dao.DataAccessException.class,()->runtime.update("UPDATE lab_manifests SET id=id WHERE id=?",id));
    assertThrows(org.springframework.dao.DataAccessException.class,()->runtime.update("DELETE FROM lab_manifests WHERE id=?",id));
    for(String table:List.of("lab_events","lab_operator_audit")) {
      assertEquals(Boolean.FALSE,runtime.queryForObject("SELECT has_any_column_privilege(current_user,?,'UPDATE') OR has_table_privilege(current_user,?,'DELETE')",Boolean.class,SCHEMA+"."+table,SCHEMA+"."+table));
    }
    try {
      adminSql("GRANT INSERT ON "+SCHEMA+".lab_manifests TO getlancer_runtime");
      assertRejected(runtime,"manifest_certification_permissions");
    } finally { provision("10_permissions.sql"); }
    for(String directRole:List.of("anon","authenticated")) {
      try(Connection connection=DriverManager.getConnection(url,admin,adminPassword);Statement statement=connection.createStatement()) {
        statement.execute("SET ROLE "+directRole);
        for(String table:List.of("lab_manifests","lab_runs","lab_requests","lab_events","lab_outbox","lab_runtime_settings","lab_operator_audit"))
          assertThrows(java.sql.SQLException.class,()->statement.executeQuery("SELECT count(*) FROM "+SCHEMA+"."+table));
      }
    }
  }

  @Test void runtimeCanLockAuthenticationAndMembershipRowsWithoutBeingAbleToUpdateThem() throws Exception {
    UUID user = UUID.randomUUID(), business = UUID.randomUUID(), product = UUID.randomUUID(), request = UUID.randomUUID(), engagement = UUID.randomUUID();
    String token = UUID.randomUUID().toString();
    runtime.update("INSERT INTO users(id,email,password_hash,email_verified_at) VALUES(?,?,?,now())", user, user+"@example.test", "synthetic-hash");
    runtime.update("INSERT INTO user_roles(user_id,role) VALUES(?,'DEVELOPER')", user);
    runtime.update("INSERT INTO developer_profiles(user_id,slug,approval_status) VALUES(?,?,'APPROVED')", user, "role-lock-"+user);
    runtime.update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,now()+interval '1 hour')", token,user);
    runtime.update("INSERT INTO businesses(id,name,summary,owner_id) VALUES(?,'Role test business','Synthetic business fixture',?)", business,user);
    runtime.update("INSERT INTO business_members(business_id,user_id,role) VALUES(?,?,'OWNER')", business,user);
    runtime.update("INSERT INTO business_requests(id,business_id,created_by,title,description,category,technology,budget,timeline) VALUES(?,?,?,'Role lock request','Synthetic request description','Inventory','Java','Synthetic budget','Synthetic timeline')",request,business,user);
    runtime.update("INSERT INTO delivery_engagements(id,business_request_id,business_id,builder_user_id,title,created_by) VALUES(?,?,?,?,'Role lock engagement',?)",engagement,request,business,user,user);
    runtime.update("INSERT INTO products(id,owner_user_id,slug,title,summary,description,project_type,category,technology,contribution_text,approval_status,lifecycle_status) VALUES(?,?,?,'Role test product','Synthetic summary','Synthetic description','SAAS','Inventory','Java','Synthetic contribution','APPROVED','ACTIVE')", product,user,"role-lock-"+product);
    TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(runtime.getDataSource()));
    transactions.executeWithoutResult(tx -> {
      assertEquals(1, runtime.queryForList("SELECT u.id FROM users u JOIN user_roles r ON r.user_id=u.id JOIN sessions s ON s.user_id=u.id JOIN business_members m ON m.user_id=u.id WHERE u.id=? AND m.business_id=? FOR SHARE OF u,r,s,m", user,business).size());
      assertEquals(1, runtime.queryForList("SELECT user_id FROM user_roles WHERE user_id=? FOR SHARE",user).size());
      assertEquals(1, runtime.queryForList("SELECT token_hash FROM sessions WHERE token_hash=? FOR SHARE",token).size());
      assertEquals(1, runtime.queryForList("SELECT user_id FROM business_members WHERE business_id=? FOR SHARE",business).size());
      new HostingRepository(runtime,new HostingConfiguration(false,"","","","",7,5000,"https://app.example.test"),new ObjectMapper()).lockActor(user);
      new CommerceRepository(runtime).sellerEligible(user,product,false);
      new MaintenanceRepository(runtime).lockActor(user);
      new MaintenanceRepository(runtime).lockConsentParties(engagement,user,user,user);
    });
    for (String table : List.of("user_roles","sessions","business_members"))
      assertEquals(Boolean.FALSE,runtime.queryForObject("SELECT has_table_privilege(current_user,?,'UPDATE')",Boolean.class,SCHEMA+"."+table));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("UPDATE user_roles SET user_id=user_id WHERE user_id=?",user));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("UPDATE sessions SET token_hash=token_hash WHERE token_hash=?",token));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("UPDATE business_members SET business_id=business_id WHERE business_id=?",business));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("UPDATE user_roles SET role='ADMIN' WHERE user_id=?",user));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("UPDATE sessions SET mfa_verified=true WHERE token_hash=?",token));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("UPDATE business_members SET role='HIRING_MANAGER' WHERE business_id=?",business));
    UUID event = UUID.randomUUID();
    runtime.update("INSERT INTO security_audit_events(id,event,result,request_id) VALUES(?,'ROLE_LOCK_TEST','SUCCESS',?)",event,UUID.randomUUID().toString());
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("UPDATE security_audit_events SET result='FAILURE' WHERE id=?",event));
    assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.update("DELETE FROM security_audit_events WHERE id=?",event));
    try {
      adminSql("REVOKE UPDATE(user_id) ON "+SCHEMA+".user_roles FROM getlancer_runtime");
      assertRejected(runtime,"locking_permissions");
      assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.queryForList("SELECT user_id FROM user_roles WHERE user_id=? FOR SHARE",user));
    } finally { provision("10_permissions.sql"); }
    try {
      adminSql("GRANT UPDATE(role) ON "+SCHEMA+".user_roles TO getlancer_runtime");
      assertRejected(runtime,"locking_permissions");
    } finally { provision("10_permissions.sql"); }
    assertEquals(Boolean.FALSE,runtime.queryForObject("SELECT has_column_privilege(current_user,?,'role','UPDATE')",Boolean.class,SCHEMA+".user_roles"));
    try {
      adminSql("ALTER POLICY getlancer_service_update ON "+SCHEMA+".sessions WITH CHECK(true)");
      assertRejected(runtime,"locking_permissions");
    } finally { provision("10_permissions.sql"); }
    try {
      adminSql("CREATE POLICY forbidden_update_fixture ON "+SCHEMA+".sessions FOR UPDATE TO PUBLIC USING(true) WITH CHECK(true)");
      assertRejected(runtime,"locking_permissions");
    } finally { adminSql("DROP POLICY forbidden_update_fixture ON "+SCHEMA+".sessions"); }
    assertDoesNotThrow(() -> guard(runtime));
  }

  @Test void newlyCreatedMigrationFunctionsHaveNoPublicOrServiceExecuteBeforePermissionRerun() {
    JdbcTemplate migration = jdbc("getlancer_migration",migrationPassword);
    try {
      migration.execute("CREATE FUNCTION future_execute_fixture() RETURNS integer LANGUAGE sql AS 'SELECT 1'");
      assertEquals(1,migration.queryForObject("SELECT future_execute_fixture()",Integer.class));
      Long functionOid = runtime.queryForObject("""
          SELECT p.oid::bigint FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
          WHERE n.nspname=? AND p.proname='future_execute_fixture' AND p.pronargs=0
          """,Long.class,SCHEMA);
      assertNotNull(functionOid);
      // Inspect the ACL by OID, independently of schema USAGE/name resolution.
      assertEquals(Boolean.FALSE,runtime.queryForObject("""
          SELECT EXISTS(SELECT 1 FROM pg_proc p,
            LATERAL aclexplode(COALESCE(p.proacl,acldefault('f',p.proowner))) function_acl
            WHERE p.oid=?::oid AND function_acl.grantee=0 AND function_acl.privilege_type='EXECUTE')
          """,Boolean.class,functionOid));
      for (String role : List.of("getlancer_runtime","getlancer_backup","getlancer_readonly","anon","authenticated"))
        assertEquals(Boolean.FALSE,runtime.queryForObject("SELECT has_function_privilege(?,?::oid,'EXECUTE')",Boolean.class,role,functionOid),role);
      assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.queryForObject("SELECT future_execute_fixture()",Integer.class));
    } finally { migration.execute("DROP FUNCTION future_execute_fixture()"); }
  }

  @Test void everyElevatedActualRoleAttributeAndOwnershipFailsHostedGuard() throws Exception {
    assertRejected(jdbc(admin, adminPassword),"runtime_identity");
    assertRejected(jdbc("getlancer_migration", migrationPassword),"runtime_identity");
    for (String attribute : List.of("SUPERUSER", "CREATEDB", "CREATEROLE", "REPLICATION", "BYPASSRLS")) {
      String check = attribute.equals("SUPERUSER") ? "rolsuper" : "rol"+attribute.toLowerCase(java.util.Locale.ROOT);
      try { adminSql("ALTER ROLE getlancer_runtime " + attribute); assertRejected(runtime,check); }
      finally { adminSql("ALTER ROLE getlancer_runtime NO" + attribute); }
      assertDoesNotThrow(() -> guard(runtime));
    }
    try { adminSql("GRANT getlancer_migration TO getlancer_runtime"); assertRejected(runtime,"membership"); }
    finally { adminSql("REVOKE getlancer_migration FROM getlancer_runtime"); }
    assertDoesNotThrow(() -> guard(runtime));
    try { adminSql("GRANT getlancer_runtime TO anon"); assertRejected(runtime,"membership"); }
    finally { adminSql("REVOKE getlancer_runtime FROM anon"); }
    assertDoesNotThrow(() -> guard(runtime));
    try { adminSql("ALTER TABLE " + SCHEMA + ".users OWNER TO getlancer_runtime"); assertRejected(runtime,"object_owner"); }
    finally {
      adminSql("ALTER TABLE " + SCHEMA + ".users OWNER TO getlancer_migration");
      restoreProvisionedPermissions();
    }
    try { adminSql("GRANT CREATE ON SCHEMA " + SCHEMA + " TO getlancer_runtime"); assertRejected(runtime,"schema_ddl"); }
    finally { adminSql("REVOKE CREATE ON SCHEMA " + SCHEMA + " FROM getlancer_runtime"); }
    assertDoesNotThrow(() -> guard(runtime));
    try { adminSql("REVOKE USAGE ON SCHEMA " + SCHEMA + " FROM getlancer_runtime"); assertRejected(runtime,"schema_usage"); }
    finally { restoreProvisionedPermissions(); }
    try { adminSql("ALTER SCHEMA " + SCHEMA + " OWNER TO getlancer_runtime"); assertRejected(runtime,"schema_ddl"); }
    finally {
      adminSql("ALTER SCHEMA " + SCHEMA + " OWNER TO getlancer_migration");
      restoreProvisionedPermissions();
    }
    try { adminSql("GRANT TEMP ON DATABASE getlancer_test TO getlancer_runtime"); assertRejected(runtime,"temporary_create"); }
    finally { adminSql("REVOKE TEMP ON DATABASE getlancer_test FROM getlancer_runtime"); }
    assertDoesNotThrow(() -> guard(runtime));
    String databaseOwner = jdbc(admin, adminPassword).queryForObject("SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='getlancer_test'", String.class);
    try { adminSql("ALTER DATABASE getlancer_test OWNER TO getlancer_runtime"); assertRejected(runtime,"database_owner"); }
    finally {
      adminSql("ALTER DATABASE getlancer_test OWNER TO \"" + databaseOwner.replace("\"", "\"\"") + "\"");
      restoreProvisionedPermissions();
    }
    try { adminSql("GRANT UPDATE ON " + SCHEMA + ".payment_ledger TO getlancer_runtime"); assertRejected(runtime,"immutable_mutation"); }
    finally { adminSql("REVOKE UPDATE ON " + SCHEMA + ".payment_ledger FROM getlancer_runtime"); }
    assertDoesNotThrow(() -> guard(runtime));
    JdbcTemplate migration = jdbc("getlancer_migration", migrationPassword);
    try {
      migration.execute("CREATE FUNCTION dangerous_role_fixture() RETURNS integer LANGUAGE sql SECURITY DEFINER AS 'SELECT 1'");
      migration.execute("GRANT EXECUTE ON FUNCTION dangerous_role_fixture() TO getlancer_runtime");
      assertRejected(runtime,"security_definer_execute");
    } finally { migration.execute("DROP FUNCTION dangerous_role_fixture()"); }
    assertDoesNotThrow(() -> guard(runtime));
  }

  @Test void newMigrationTablesStayDeniedAndRequirePermissionReviewBeforeStartup() throws Exception {
    JdbcTemplate migration = jdbc("getlancer_migration", migrationPassword);
    try {
      migration.execute("CREATE TABLE future_role_fixture(id uuid PRIMARY KEY)");
      assertThrows(org.springframework.dao.DataAccessException.class, () -> runtime.queryForObject("SELECT count(*) FROM future_role_fixture", Integer.class));
      assertRejected(runtime,"table_permissions");
      assertThrows(Exception.class, () -> provision("10_permissions.sql"));
      assertDoesNotThrow(() -> runtime.queryForObject("SELECT count(*) FROM users", Integer.class));
    } finally { migration.execute("DROP TABLE future_role_fixture"); }
    provision("10_permissions.sql"); assertDoesNotThrow(() -> guard(runtime));
  }
}
