package com.getlancer.teams;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.getlancer.products.ProductService;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

class TeamsTest {
  final JdbcTemplate db = mock(JdbcTemplate.class);
  final Security security = mock(Security.class);
  final TeamService c =
      spy(new TeamService(db, security, mock(ProductService.class), new TeamRepository(db)));
  final MockHttpServletRequest request = new MockHttpServletRequest();
  final UUID team = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      target = UUID.randomUUID(),
      record = UUID.randomUUID();

  TeamsTest() {
    when(security.user(request)).thenReturn(actor);
    doNothing().when(c).lock(team);
    doNothing().when(c).active(team);
    doNothing().when(c).audit(any(), any(), anyString(), anyString(), anyString());
  }

  Map<String, Object> row(Object... pairs) {
    Map<String, Object> x = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) x.put((String) pairs[i], pairs[i + 1]);
    return x;
  }

  void capability(String role) {
    doReturn(role).when(c).member(team, actor);
  }

  @Test
  void permissionMatrixHasNoCrossRolePrivileges() {
    for (String role : TeamPolicy.ROLES)
      for (String capability : List.of("owner", "recruit", "commercial", "staff")) {
        boolean expected =
            role.equals("OWNER")
                || role.equals(
                    Map.of(
                            "owner",
                            "OWNER",
                            "recruit",
                            "RECRUITER",
                            "commercial",
                            "BUSINESS_MANAGER",
                            "staff",
                            "PROJECT_MANAGER")
                        .get(capability));
        assertEquals(expected, TeamPolicy.permits(role, capability), role + ":" + capability);
      }
    assertFalse(TeamPolicy.permits(null, "commercial"));
  }

  @Test
  void recruiterCannotInvitePrivilegedRoles() {
    for (String role : TeamPolicy.ROLES) {
      if (role.equals("MEMBER"))
        assertDoesNotThrow(() -> TeamPolicy.invitationRole("RECRUITER", role));
      else assertThrows(ApiError.class, () -> TeamPolicy.invitationRole("RECRUITER", role));
    }
    assertThrows(ApiError.class, () -> TeamPolicy.invitationRole("OWNER", "OWNER"));
    assertDoesNotThrow(() -> TeamPolicy.invitationRole("OWNER", "BUSINESS_MANAGER"));
  }

  @Test
  void expiryMustBeFutureAndRequiredForContract() {
    assertThrows(ApiError.class, () -> TeamPolicy.future(null, true));
    assertThrows(ApiError.class, () -> TeamPolicy.future("not-a-date", true));
    assertThrows(
        ApiError.class, () -> TeamPolicy.future(Instant.now().minusSeconds(10).toString(), true));
    assertNull(TeamPolicy.future(null, false));
    assertNotNull(TeamPolicy.future(Instant.now().plusSeconds(3600).toString(), true));
  }

  @Test
  void leadLifecycleCannotSkipProposalOrReopenTerminalOutcomes() {
    assertThrows(ApiError.class, () -> TeamPolicy.leadTransition("NEW", "WON"));
    for (String terminal : List.of("WON", "LOST", "DECLINED"))
      assertThrows(ApiError.class, () -> TeamPolicy.leadTransition(terminal, "NEW"));
    for (String[] transition :
        new String[][] {
          {"NEW", "NEEDS_INFORMATION"},
          {"NEEDS_INFORMATION", "INTERESTED"},
          {"INTERESTED", "PROPOSAL_SENT"},
          {"PROPOSAL_SENT", "WON"}
        }) assertDoesNotThrow(() -> TeamPolicy.leadTransition(transition[0], transition[1]));
  }

  @Test
  void decisionsCannotReprocessAnInvitedOrRejectedApplication() {
    for (String state : List.of("INVITED", "REJECTED"))
      assertThrows(ApiError.class, () -> TeamPolicy.applicationTransition(state, "INVITED"));
    assertDoesNotThrow(() -> TeamPolicy.applicationTransition("APPLIED", "SHORTLISTED"));
    assertDoesNotThrow(() -> TeamPolicy.applicationTransition("SHORTLISTED", "INVITED"));
  }

  @Test
  void expiredMembershipFailsClosed() {
    when(db.queryForList(contains("SELECT m.role"), eq(team), eq(actor))).thenReturn(List.of());
    assertEquals(403, assertThrows(ApiError.class, () -> c.member(team, actor)).status);
    assertTrue(TeamRepository.ACTIVE.contains("m.expires_at>now()"));
    assertTrue(TeamRepository.ACTIVE.contains("d.approval_status='APPROVED'"));
  }

  @Test
  void workspaceFiltersPrivateCollectionsAndActivity() {
    capability("MEMBER");
    doReturn(row("id", team)).when(c).team(team);
    doReturn(List.of()).when(c).members(team);
    doReturn(List.of()).when(c).projects(team);
    when(db.queryForList(contains("FROM team_activity"), eq(team)))
        .thenReturn(
            List.of(
                row("scope", "commercial", "detail", "Secret pricing"),
                row("scope", "recruit", "detail", "Private application"),
                row("scope", "member", "detail", "Profile updated")));
    var result = c.workspace(team, request);
    for (String field : List.of("leads", "applications", "invitations", "staffing"))
      assertEquals(List.of(), result.get(field));
    assertEquals(List.of(Map.of("detail", "Profile updated")), result.get("activity"));
    verify(c, never()).leads(anyString(), anyBoolean(), any(Object[].class));
  }

  @Test
  void publicMembersDoNotRevealStaffingProjectOrExpiry() {
    doReturn(
            List.of(
                row(
                    "userId",
                    target,
                    "projectLabel",
                    "Confidential acquisition",
                    "expiresAt",
                    Instant.now().plusSeconds(3600))))
        .when(c)
        .members(team);
    var member = c.publicMembers(team).get(0);
    assertNull(member.get("projectLabel"));
    assertNull(member.get("expiresAt"));
    assertEquals(target, member.get("userId"));
  }

  @Test
  void clientRequestsDoNotRevealNotesOrInternalFollowUp() {
    when(db.queryForList(eq(TeamRepository.LEAD + "WHERE l.client_id=?"), eq(actor)))
        .thenReturn(List.of(row("id", record, "assigneeId", target, "followUpAt", Instant.now())));
    var lead = c.leads("WHERE l.client_id=?", false, actor).get(0);
    assertNull(lead.get("assigneeId"));
    assertNull(lead.get("followUpAt"));
    assertEquals(List.of(), lead.get("notes"));
    verify(db, never()).queryForList(contains("team_lead_notes"), any(Object[].class));
  }

  @Test
  void acceptInvitationWritesMembershipOnlyAfterRecipientConsent() {
    doReturn(row("team_id", team))
        .when(c)
        .one("SELECT team_id FROM team_invitations WHERE id=? AND user_id=?", record, actor);
    doReturn(
            row(
                "status",
                "PENDING",
                "valid",
                true,
                "role",
                "MEMBER",
                "membership_type",
                "CONTRACT",
                "expires_at",
                Timestamp.from(Instant.now().plusSeconds(3600)),
                "project_label",
                "Build"))
        .when(c)
        .one(contains("FOR UPDATE"), eq(record), eq(actor));
    doReturn(null).when(c).role(team, actor);
    doNothing().when(c).builder(actor);
    doReturn(row("status", "ACCEPTED")).when(c).invitation(record);
    assertEquals("ACCEPTED", c.respond(record, Map.of("action", "ACCEPT"), request).get("status"));
    verify(db)
        .update(
            startsWith("INSERT INTO team_members"),
            eq(team),
            eq(actor),
            eq("MEMBER"),
            eq("CONTRACT"),
            any(Timestamp.class),
            eq("Build"));
  }

  @Test
  void expiredInvitationCannotCreateMembership() {
    doReturn(row("team_id", team))
        .when(c)
        .one("SELECT team_id FROM team_invitations WHERE id=? AND user_id=?", record, actor);
    doReturn(row("status", "PENDING", "valid", false))
        .when(c)
        .one(contains("FOR UPDATE"), eq(record), eq(actor));
    assertEquals(
        409,
        assertThrows(ApiError.class, () -> c.respond(record, Map.of("action", "ACCEPT"), request))
            .status);
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void declineInvitationDoesNotCreateMembership() {
    doReturn(row("team_id", team))
        .when(c)
        .one("SELECT team_id FROM team_invitations WHERE id=? AND user_id=?", record, actor);
    doReturn(row("status", "PENDING", "valid", true))
        .when(c)
        .one(contains("FOR UPDATE"), eq(record), eq(actor));
    doReturn(row("status", "DECLINED")).when(c).invitation(record);
    c.respond(record, Map.of("action", "DECLINE"), request);
    verify(db, never()).update(startsWith("INSERT INTO team_members"), any(Object[].class));
    verify(db).update("UPDATE team_invitations SET status=? WHERE id=?", "DECLINED", record);
  }

  @Test
  void ownerEscalationAndLastOwnerRemovalAreBlocked() {
    capability("OWNER");
    doReturn("MEMBER").when(c).member(team, target);
    assertThrows(
        ApiError.class, () -> c.changeRole(team, target, Map.of("role", "OWNER"), request));
    doReturn(row("role", "OWNER"))
        .when(c)
        .one("SELECT role FROM team_members WHERE team_id=? AND user_id=?", team, actor);
    assertThrows(ApiError.class, () -> c.remove(team, actor, request));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void activeMemberCannotSendSelfInterestEvenIfBuilderApprovalWasRevoked() {
    when(security.principal(request))
        .thenReturn(row("id", actor, "email_verified_at", Instant.now()));
    when(db.queryForObject(
            startsWith("SELECT count(*) FROM team_members"),
            eq(Integer.class),
            eq(team),
            eq(actor)))
        .thenReturn(1);
    assertThrows(ApiError.class, () -> c.lead(team, Map.of(), request));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void unverifiedClientCannotSendInterest() {
    when(security.principal(request)).thenReturn(row("id", actor));
    assertEquals(
        "EMAIL_NOT_VERIFIED",
        assertThrows(ApiError.class, () -> c.lead(team, Map.of(), request)).code);
  }

  @Test
  void leadCreationUsesExactlySevenBoundColumns() {
    when(security.principal(request))
        .thenReturn(row("id", actor, "email_verified_at", Instant.now()));
    when(db.queryForObject(
            startsWith("SELECT count(*) FROM team_members"),
            eq(Integer.class),
            eq(team),
            eq(actor)))
        .thenReturn(0);
    doReturn(List.of(row("status", "NEW")))
        .when(c)
        .leads(eq("WHERE l.id=?"), eq(false), any(UUID.class));
    c.lead(
        team,
        Map.of(
            "title",
            "Build a portal",
            "description",
            "A customer portal for our clients",
            "budget",
            "10k",
            "timeline",
            "October"),
        request);
    verify(db)
        .update(
            eq(
                "INSERT INTO team_leads(id,team_id,client_id,title,description,budget,timeline)"
                    + " VALUES(?,?,?,?,?,?,?)"),
            any(UUID.class),
            eq(team),
            eq(actor),
            eq("Build a portal"),
            eq("A customer portal for our clients"),
            eq("10k"),
            eq("October"));
  }

  @Test
  void memberCannotReadCandidatesOrManageCommercialLead() {
    capability("MEMBER");
    assertThrows(ApiError.class, () -> c.candidates(team, "", request));
    assertThrows(
        ApiError.class, () -> c.updateLead(team, record, Map.of("status", "WON"), request));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void staffingCannotOutlastContract() {
    capability("PROJECT_MANAGER");
    doReturn("MEMBER").when(c).member(team, target);
    doReturn(row("expires_at", Timestamp.from(Instant.now().plusSeconds(1800))))
        .when(c)
        .one("SELECT expires_at FROM team_members WHERE team_id=? AND user_id=?", team, target);
    assertThrows(
        ApiError.class,
        () ->
            c.addStaffing(
                team,
                Map.of(
                    "userId",
                    target.toString(),
                    "endsAt",
                    Instant.now().plusSeconds(3600).toString()),
                request));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void expiredOrCompletedStaffingCannotReactivate() {
    capability("PROJECT_MANAGER");
    doReturn("MEMBER").when(c).member(team, target);
    for (var state :
        List.of(
            row("user_id", target, "valid", false, "status", "ACTIVE"),
            row("user_id", target, "valid", true, "status", "COMPLETED"))) {
      doReturn(state)
          .when(c)
          .one(
              "SELECT id,team_id,user_id,project_label,skills,ends_at,status,created_at,ends_at>now() AS valid FROM team_staffing WHERE team_id=? AND id=?",
              team,
              record);
      assertEquals("INVALID_STATE_TRANSITION", assertThrows(
          ApiError.class,
          () -> c.updateStaffing(team, record, Map.of("status", "ACTIVE"), request)).code);
    }
  }

  @Test
  void consentOwnerMayRevokeProjectAfterMembershipExpires() {
    doReturn(row("consented_by", actor))
        .when(c)
        .one(
            "SELECT consented_by FROM team_projects WHERE team_id=? AND product_id=?",
            team,
            record);
    assertEquals(Map.of("ok", true), c.removeProject(team, record, request));
    verify(c, never()).member(team, actor);
    verify(db).update("DELETE FROM team_projects WHERE team_id=? AND product_id=?", team, record);
  }

  @Test
  void moderationRequiresAdminSecurityBoundary() {
    when(security.admin(request)).thenThrow(new ApiError(403, "FORBIDDEN", "MFA required"));
    assertThrows(
        ApiError.class,
        () ->
            c.moderate(
                team,
                Map.of("status", "SUSPENDED", "reason", "Investigated abuse report"),
                request));
    verify(db, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void suspendedTeamIsNotPublic() {
    doReturn(row("status", "SUSPENDED")).when(c).team(team);
    assertEquals(404, assertThrows(ApiError.class, () -> c.detail(team)).status);
  }
}
