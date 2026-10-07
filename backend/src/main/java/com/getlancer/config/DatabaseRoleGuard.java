package com.getlancer.config;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.springframework.context.annotation.DependsOn;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Checks the connected PostgreSQL identity, rather than trusting a configured username. */
@Component
@DependsOn("productionConfiguration")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DatabaseRoleGuard implements ApplicationRunner {
  private final JdbcTemplate db;
  private final Environment env;

  public DatabaseRoleGuard(JdbcTemplate db, Environment env) { this.db = db; this.env = env; }

  @PostConstruct
  void beforeServing() { run(null); }

  public void run(ApplicationArguments args) {
    if (!java.util.Set.of("staging", "production").contains(env.getProperty("app.environment", ""))) return;
    validate(env.getProperty("spring.datasource.hikari.schema", ""));
  }

  void validate(String schema) {
    if (!schema.matches("[a-z][a-z0-9_]{0,62}") || schema.equals("public")) reject("schema_name");
    Map<String, Object> role = db.queryForMap("""
        SELECT current_user AS name, session_user AS login, r.rolsuper, r.rolcreatedb,
          r.rolcreaterole, r.rolreplication, r.rolbypassrls,
          EXISTS(SELECT 1 FROM pg_auth_members m WHERE m.member=r.oid OR m.roleid=r.oid) AS membership,
          EXISTS(SELECT 1 FROM pg_database d WHERE d.datname=current_database() AND d.datdba=r.oid) AS database_owner,
          has_database_privilege(current_user,current_database(),'CREATE') AS database_create,
          has_database_privilege(current_user,current_database(),'TEMP') AS temporary_create,
          EXISTS(SELECT 1 FROM pg_namespace n WHERE left(n.nspname,3)<>'pg_' AND n.nspname<>'information_schema'
            AND (n.nspowner=r.oid OR has_schema_privilege(current_user,n.oid,'CREATE'))) AS schema_ddl,
          EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE left(n.nspname,3)<>'pg_' AND n.nspname<>'information_schema' AND c.relowner=r.oid) AS object_owner
        FROM pg_roles r WHERE r.rolname=current_user
        """);
    if (!"getlancer_runtime".equals(role.get("name")) || !role.get("name").equals(role.get("login"))) reject("runtime_identity");
    for (String key : new String[] {"rolsuper", "rolcreatedb", "rolcreaterole", "rolreplication", "rolbypassrls", "membership", "database_owner", "database_create", "temporary_create", "schema_ddl", "object_owner"})
      if (Boolean.TRUE.equals(role.get(key))) reject(key);
    if (!Boolean.TRUE.equals(db.queryForObject("SELECT has_schema_privilege(current_user,?,'USAGE')", Boolean.class, schema))) reject("schema_usage");
    Integer unsafe = db.queryForObject("""
        SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname=? AND c.relkind IN ('r','p') AND c.relname<>'flyway_schema_history'
          AND (NOT c.relrowsecurity OR NOT has_table_privilege(current_user,c.oid,'SELECT') OR has_table_privilege(current_user,c.oid,'TRUNCATE')
            OR has_table_privilege(current_user,c.oid,'TRIGGER') OR has_table_privilege(current_user,c.oid,'REFERENCES')
            OR NOT EXISTS(SELECT 1 FROM pg_policy p WHERE p.polrelid=c.oid AND p.polname='getlancer_service_select'
              AND (SELECT oid FROM pg_roles WHERE rolname='getlancer_runtime')=ANY(p.polroles)))
        """, Integer.class, schema);
    if (unsafe == null || unsafe != 0) reject("table_permissions");
    Integer mutation = db.queryForObject("""
        SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname=? AND c.relname IN ('security_audit_events','inquiry_events','moderation_actions',
          'delivery_activity','payment_ledger','payment_account_audit','commerce_ledger','commerce_audit',
          'maintenance_ledger','maintenance_audit','hosting_audit','component_audit','component_slot_ledger',
          'component_slot_events','publishing_capacity_grants')
          AND (has_table_privilege(current_user,c.oid,'UPDATE') OR has_any_column_privilege(current_user,c.oid,'UPDATE')
            OR has_table_privilege(current_user,c.oid,'DELETE'))
        """, Integer.class, schema);
    Integer unsafeLocks = db.queryForObject("""
        SELECT count(*) FROM (VALUES ('user_roles','user_id'),('sessions','token_hash'),
          ('business_members','business_id')) AS lock_requirement(table_name,column_name)
        LEFT JOIN pg_namespace n ON n.nspname=?
        LEFT JOIN pg_class c ON c.relnamespace=n.oid AND c.relname=lock_requirement.table_name AND c.relkind='r'
        WHERE c.oid IS NULL OR has_table_privilege(current_user,c.oid,'UPDATE')
          OR NOT has_column_privilege(current_user,c.oid,lock_requirement.column_name,'UPDATE')
          OR EXISTS(SELECT 1 FROM pg_attribute a WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
            AND a.attname<>lock_requirement.column_name AND has_column_privilege(current_user,c.oid,a.attnum,'UPDATE'))
          OR NOT EXISTS(SELECT 1 FROM pg_policy p WHERE p.polrelid=c.oid
            AND p.polname='getlancer_service_update' AND p.polcmd='w' AND p.polpermissive
            AND p.polroles=ARRAY[(SELECT oid FROM pg_roles WHERE rolname=current_user)]
            AND pg_get_expr(p.polqual,p.polrelid)='true' AND pg_get_expr(p.polwithcheck,p.polrelid)='false')
          OR EXISTS(SELECT 1 FROM pg_policy p WHERE p.polrelid=c.oid AND p.polpermissive
            AND p.polcmd IN ('*','w') AND p.polname<>'getlancer_service_update'
            AND (0::oid=ANY(p.polroles) OR (SELECT oid FROM pg_roles WHERE rolname=current_user)=ANY(p.polroles)))
        """, Integer.class, schema);
    Boolean exists = db.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, schema+".users");
    Integer dangerousFunctions = db.queryForObject("""
        SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE left(n.nspname,3)<>'pg_' AND n.nspname<>'information_schema'
          AND p.prosecdef AND has_function_privilege(current_user,p.oid,'EXECUTE')
        """, Integer.class);
    if (mutation == null || mutation != 0) reject("immutable_mutation");
    if (!Boolean.TRUE.equals(exists)) reject("users_table");
    if (dangerousFunctions == null || dangerousFunctions != 0) reject("security_definer_execute");
    if (unsafeLocks == null || unsafeLocks != 0) reject("locking_permissions");
  }

  private static void reject(String check) {
    // Only fixed check names are reported; connection settings, role values and secrets are excluded.
    throw new IllegalStateException("Unsafe PostgreSQL runtime role or schema permissions; apply reviewed database provisioning before deployment (check: " + check + ")");
  }
}
