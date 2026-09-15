package com.getlancer;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileAndBootstrapTest {
 @Test void optionalFieldsSurviveOlderClientUpdates() {
  var previous=Map.<String,Object>of("country","IN","time_zone","Asia/Kolkata","languages","English, Telugu");
  var details=ProfileDetails.read(Map.of(),previous);
  assertEquals("IN",details.country()); assertFalse(details.changed(previous));
  assertEquals("",ProfileDetails.read(Map.of("languages",""),previous).languages());
 }
 @Test void invalidCountryZoneAndBookedDateHaveFieldErrors() {
  for(var input:List.of(Map.<String,Object>of("country","ZZ"),Map.<String,Object>of("timeZone","Mars/Olympus")))
   assertFalse(assertThrows(ApiError.class,()->ProfileDetails.read(input,Map.of())).fieldErrors.isEmpty());
  var details=ProfileDetails.read(Map.of("timeZone","Asia/Kolkata"),Map.of());
  assertEquals("bookedUntil",assertThrows(ApiError.class,()->details.bookedUntil(Map.of("bookedUntil","2026-99-99"),"BOOKED_UNTIL")).fieldErrors.keySet().iterator().next());
  assertThrows(ApiError.class,()->details.bookedUntil(Map.of("bookedUntil","2000-01-01"),"BOOKED_UNTIL"));
  assertNotNull(details.bookedUntil(Map.of("bookedUntil",LocalDate.now().plusYears(1).toString()),"BOOKED_UNTIL"));
  assertNull(details.bookedUntil(Map.of(),"AVAILABLE_NOW"));
 }
 @Test void integrationGuardRejectsNormalDatabaseBeforeStartup() {
  var env=new MockEnvironment().withProperty("TEST_DATABASE_RESET","true").withProperty("spring.flyway.default-schema","getlancer_test").withProperty("spring.datasource.url","jdbc:postgresql://localhost:5432/getLancer");
  assertThrows(IllegalStateException.class,()->TestDatabaseGuard.validate(env));
  env.withProperty("spring.datasource.url","jdbc:postgresql://localhost:5432/getlancer_test");
  assertDoesNotThrow(()->TestDatabaseGuard.validate(env));
  env.withProperty("TEST_DATABASE_RESET","false");
  assertThrows(IllegalStateException.class,()->TestDatabaseGuard.validate(env));
 }
 @Test void existingAdminNeverHasCredentialsReset() {
  var db=mock(JdbcTemplate.class);
  when(db.queryForList("SELECT u.email FROM users u JOIN user_roles r ON r.user_id=u.id WHERE r.role='ADMIN'",String.class)).thenReturn(List.of("admin@example.com"));
  new Bootstrap(db,"admin@example.com","","","local","http://localhost:3000",false).run(null);
  verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void adminEmailCollisionDoesNotElevateAnExistingAccount() {
  var db=mock(JdbcTemplate.class);
  when(db.queryForObject("SELECT count(*) FROM users WHERE email=?",Integer.class,"admin@example.com")).thenReturn(1);
  assertThrows(IllegalStateException.class,()->new Bootstrap(db,"admin@example.com","test-password-long-enough","JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP","local","http://localhost:3000",false).run(null));
  verify(db,never()).update(anyString(),any(Object[].class));
 }
}
