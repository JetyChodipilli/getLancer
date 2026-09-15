package com.getlancer;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class V1SafeguardsTest {
 @Test void administratorCannotDeleteOwnAccount(){
  var db=mock(JdbcTemplate.class);var security=mock(Security.class);var request=new MockHttpServletRequest();UUID id=UUID.randomUUID();when(security.user(request)).thenReturn(id);when(security.role(id,"ADMIN")).thenReturn(true);
  var auth=new AuthController(db,security,mock(Mail.class),true,"admin@example.com");var error=assertThrows(ApiError.class,()->auth.deletion(request));assertEquals("ADMIN_ACCOUNT_PROTECTED",error.code);verifyNoInteractions(db);
 }
 @Test void administratorCannotBeSuspended(){var db=mock(JdbcTemplate.class);var security=mock(Security.class);var request=new MockHttpServletRequest();UUID id=UUID.randomUUID();when(security.admin(request)).thenReturn(id);when(security.role(id,"ADMIN")).thenReturn(true);var controller=new AdminController(db,security,mock(ProductsController.class),mock(Mail.class));assertEquals("ADMIN_ACCOUNT_PROTECTED",assertThrows(ApiError.class,()->controller.accountAction(id,"suspend",Map.of("reason","Testing protection"),request)).code);verifyNoInteractions(db);}
 @Test void restrictedInquiriesCannotAdvance(){for(String state:List.of("QUARANTINED","BLOCKED"))assertEquals("INQUIRY_RESTRICTED",assertThrows(ApiError.class,()->InquiryPolicy.requireClear(Map.of("moderation_status",state))).code);assertDoesNotThrow(()->InquiryPolicy.requireClear(Map.of("moderation_status","CLEAR")));}
 @Test void suspendedSenderCannotConfirmOrSubmit(){var db=mock(JdbcTemplate.class);when(db.queryForObject(anyString(),eq(Integer.class),eq("blocked@example.com"))).thenReturn(1);assertEquals("ACCOUNT_UNAVAILABLE",assertThrows(ApiError.class,()->InquiryPolicy.requireSender(db,"blocked@example.com")).code);}
 @Test void paginationRejectsNegativeOverflowAndZeroSize(){for(String[] pair:new String[][]{{"page","-1"},{"page","999999999999"},{"size","0"},{"size","101"}}){var request=new MockHttpServletRequest();request.setParameter(pair[0],pair[1]);assertThrows(ApiError.class,()->Pages.query(mock(JdbcTemplate.class),request,"SELECT id FROM products"));}}
 @Test void paginationOnlyReturnsPageAndIndicatesMore(){var request=new MockHttpServletRequest();request.setParameter("size","1");var db=mock(JdbcTemplate.class);when(db.queryForList("SELECT id FROM products ORDER BY id LIMIT ? OFFSET ?",2,0)).thenReturn(List.of(Map.of("id",1),Map.of("id",2)));when(db.queryForObject(eq("SELECT count(*) FROM (SELECT id FROM products ORDER BY id) page_count"),eq(Long.class),any(Object[].class))).thenReturn(2L);var page=Pages.query(db,request,"SELECT id FROM products ORDER BY id");assertEquals(2L,page.get("totalItems"));assertEquals(2L,page.get("totalPages"));assertEquals(true,page.get("hasMore"));assertEquals(List.of(Map.of("id",1)),page.get("items"));}
 @Test void privateGrantsRequireVerifiedEmail(){var db=mock(JdbcTemplate.class);assertFalse(PrivateProjects.granted(db,UUID.randomUUID(),Map.of("email","client@example.com")));verifyNoInteractions(db);}
 @Test void invalidTransitionsUseConflictStatus(){var response=new Errors().invalid(new IllegalArgumentException("INVALID_STATE_TRANSITION"));assertEquals(409,response.getStatusCode().value());}
 @Test void validationIdentifiesField(){var error=assertThrows(ApiError.class,()->Support.text(Map.of("title",""),"title",3,120));assertTrue(error.fieldErrors.containsKey("title"));}
 @Test void productionCannotUsePreviewDefaults(){var env=new MockEnvironment().withProperty("app.environment","production");assertThrows(IllegalStateException.class,()->new ProductionConfiguration(env).run(null));}
 @Test void localConfigurationStillWorks(){assertDoesNotThrow(()->new ProductionConfiguration(new MockEnvironment()).run(null));}
 @Test void badCapacityConfigurationFailsEarly(){assertThrows(IllegalStateException.class,()->new ProductionConfiguration(new MockEnvironment().withProperty("app.rate-limit","0")).run(null));}
}
