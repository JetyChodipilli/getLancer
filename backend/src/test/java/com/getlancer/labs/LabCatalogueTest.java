package com.getlancer.labs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Offline bounds tests; signed payload and current-source authority use PostgreSQL integration tests. */
class LabCatalogueTest {
  @Test void catalogueStopsAfterOneHundredVisibleEntries(){
    var harness=new Harness(1001,IntStream.range(0,1001).boxed().collect(Collectors.toSet()));
    var catalogue=harness.labs.catalogue();
    assertEquals(100,catalogue.items().size());assertEquals("candidate-0000",catalogue.items().get(0).id());assertEquals("candidate-0099",catalogue.items().get(99).id());
    assertEquals(100,harness.reviewed.size());assertTrue(catalogue.items().stream().allMatch(item->item.executionMode().equals("Source only")&&!item.availability().enabled()));
  }

  @Test void exhaustedCandidateBudgetFailsExplicitlyWhenAnotherIdentityRemains(){
    var harness=new Harness(1001,Set.of(1000));
    var failure=assertThrows(ApiError.class,harness.labs::catalogue);
    assertEquals(503,failure.status);assertEquals("LAB_CATALOGUE_SCAN_LIMIT",failure.code);
    assertEquals(1000,harness.reviewed.size());assertFalse(harness.reviewed.contains("candidate-1000"));assertTrue(harness.pages<=10);
  }

  @Test void exactCandidateCapAtInventoryEndReturnsItsValidPartialSnapshot(){
    var harness=new Harness(1000,Set.of(999));
    var catalogue=harness.labs.catalogue();
    assertEquals(1,catalogue.items().size());assertEquals("candidate-0999",catalogue.items().get(0).id());
    assertEquals(1000,harness.reviewed.size());assertTrue(harness.pages<=10);
  }

  private static final class Harness {
    final TreeMap<String,Boolean> identities=new TreeMap<>();
    final List<String> reviewed=new ArrayList<>();
    final LabService labs;
    int pages;

    Harness(int count,Set<Integer> visible){
      for(int i=0;i<count;i++)identities.put(String.format(Locale.ROOT,"candidate-%04d",i),visible.contains(i));
      var json=new ObjectMapper();var config=mock(LabConfiguration.class);
      when(config.projection()).thenReturn(new LabConfiguration.Runtime(false,"Offline source-only catalogue fixture."));
      when(config.sourceManifest(anyString(),anyString(),anyString(),same(json))).thenAnswer(call->{
        String id=call.getArgument(0);reviewed.add(id);
        if(!Boolean.TRUE.equals(identities.get(id)))throw new ApiError(503,"LAB_MANIFEST_UNAVAILABLE","Rejected fixture candidate.");
        return manifest(id);
      });
      JdbcTemplate db=mock(JdbcTemplate.class,call->{
        if(call.getMethod().getName().equals("toString"))return "Offline catalogue inventory";
        Object[] arguments=call.getArguments();String sql=(String)arguments[0];
        int start=arguments.length>1&&arguments[1] instanceof Class<?>?2:1;
        Object[] values=arguments.length==start+1&&arguments[start] instanceof Object[] nested?nested:Arrays.copyOfRange(arguments,start,arguments.length);
        if(call.getMethod().getName().equals("queryForList")){
          if(sql.startsWith("SELECT id FROM lab_manifests WHERE revoked_at IS NULL")){
            pages++;String after=(String)values[0];int limit=((Number)values[1]).intValue();
            return identities.tailMap(after,false).keySet().stream().limit(limit).toList();
          }
          if(sql.startsWith("SELECT id,payload_text,payload_sha256,signature")){
            String id=(String)values[0];
            return List.of(Map.of("id",id,"payload_text",id,"payload_sha256","a".repeat(64),"signature","unit-fixture","execution_approved",false));
          }
          if(sql.startsWith("SELECT c.owner_id,c.slug FROM component_entries"))return List.of(Map.of("owner_id",new UUID(0,1),"slug","offline-source"));
        }
        throw new AssertionError("Unexpected catalogue database operation: "+call.getMethod().getName()+" "+sql);
      });
      labs=new LabService(db,mock(Security.class),config,mock(LabProviderClient.class),json,mock(PlatformTransactionManager.class),new LabDataAiPolicy(false),new LabIotPolicy(false),new LabAdapterPolicy());
    }

    private static LabManifest manifest(String id){
      String hash="a".repeat(64);
      return new LabManifest(id,new UUID(0,1),1,hash,"sha256:"+hash,hash,hash,hash,"getlancer-lab-v1",
        "Catalogue fixture","An offline bounded catalogue fixture.","Fixture","Fixture","Offline test metadata; no executable runtime evidence.",
        64,1,Instant.now().plusSeconds(3600),hash,List.of(new LabManifest.Scenario("request","Fixture request","A bounded fixture request.",List.of(),List.of(new LabManifest.Operation("echo","Echo")))));
    }
  }
}
