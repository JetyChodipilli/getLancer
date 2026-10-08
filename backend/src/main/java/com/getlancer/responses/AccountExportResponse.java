package com.getlancer.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit account export contract. Newly added database columns cannot appear here.
 * JsonNode components deliberately retain legacy serialization of bounded domain consent/source
 * snapshots; the selected columns are fixed, and source catalogue keys/files are pinned by
 * ComponentService. Hosting manifests and audit entries have their own concrete projections.
 */
public record AccountExportResponse(Account account,
    Profile profile,
    List<Products> products,
    List<Requests> requests,
    List<SavedProducts> savedProducts,
    List<SavedComponents> savedComponents,
    List<LegalAcceptances> legalAcceptances,
    List<RepositoryVerifications> repositoryVerifications,
    List<EarnedCapacityAwards> earnedCapacityAwards,
    List<TeamStaffing> teamStaffing,
    List<TeamMemberships> teamMemberships,
    List<TeamInvitations> teamInvitations,
    List<TeamApplications> teamApplications,
    List<TeamRequests> teamRequests,
    List<TeamProductConsent> teamProductConsent,
    List<BusinessMemberships> businessMemberships,
    List<BusinessInvitations> businessInvitations,
    List<BusinessRequests> businessRequests,
    List<SavedTalent> savedTalent,
    List<RequestShortlist> requestShortlist,
    List<CommercialConsents> commercialConsents,
    List<DeliveryDisputes> deliveryDisputes,
    List<MilestonePayments> milestonePayments,
    List<SourceTemplates> sourceTemplates,
    List<SourceReleases> sourceReleases,
    List<SourcePurchases> sourcePurchases,
    List<SourceDisputes> sourceDisputes,
    List<MaintenanceConsents> maintenanceConsents,
    List<MaintenanceBilling> maintenanceBilling,
    List<MaintenancePeriods> maintenancePeriods,
    List<MaintenanceRefunds> maintenanceRefunds,
    List<MaintenanceDisputes> maintenanceDisputes,
    List<MaintenanceEvents> maintenanceEvents,
    List<MaintenanceLedger> maintenanceLedger,
    List<MaintenanceRequests> maintenanceRequests,
    List<MaintenanceActions> maintenanceActions,
    List<Components> components,
    List<ComponentRelease> componentReleases,
    List<CollegeContext> collegeContext,
    List<ComponentSlotPurchases> componentSlotPurchases,
    List<ComponentSlotLedger> componentSlotLedger,
    HostedDemos hostedDemos) {
  public static AccountExportResponse from(Map<String, Object> source, ObjectMapper mapper) {
    return new AccountExportResponse(Account.from(ResponseRows.row(source, "account"), mapper),
        Profile.from(ResponseRows.row(source, "profile"), mapper),
        ResponseRows.rows(source, "products", row -> Products.from(row, mapper)),
        ResponseRows.rows(source, "requests", row -> Requests.from(row, mapper)),
        ResponseRows.rows(source, "savedProducts", row -> SavedProducts.from(row, mapper)),
        ResponseRows.rows(source, "savedComponents", row -> SavedComponents.from(row)),
        ResponseRows.rows(source, "legalAcceptances", row -> LegalAcceptances.from(row, mapper)),
        ResponseRows.rows(source, "repositoryVerifications", row -> RepositoryVerifications.from(row, mapper)),
        ResponseRows.rows(source, "earnedCapacityAwards", row -> EarnedCapacityAwards.from(row, mapper)),
        ResponseRows.rows(source, "teamStaffing", row -> TeamStaffing.from(row, mapper)),
        ResponseRows.rows(source, "teamMemberships", row -> TeamMemberships.from(row, mapper)),
        ResponseRows.rows(source, "teamInvitations", row -> TeamInvitations.from(row, mapper)),
        ResponseRows.rows(source, "teamApplications", row -> TeamApplications.from(row, mapper)),
        ResponseRows.rows(source, "teamRequests", row -> TeamRequests.from(row, mapper)),
        ResponseRows.rows(source, "teamProductConsent", row -> TeamProductConsent.from(row, mapper)),
        ResponseRows.rows(source, "businessMemberships", row -> BusinessMemberships.from(row, mapper)),
        ResponseRows.rows(source, "businessInvitations", row -> BusinessInvitations.from(row, mapper)),
        ResponseRows.rows(source, "businessRequests", row -> BusinessRequests.from(row, mapper)),
        ResponseRows.rows(source, "savedTalent", row -> SavedTalent.from(row, mapper)),
        ResponseRows.rows(source, "requestShortlist", row -> RequestShortlist.from(row, mapper)),
        ResponseRows.rows(source, "commercialConsents", row -> CommercialConsents.from(row, mapper)),
        ResponseRows.rows(source, "deliveryDisputes", row -> DeliveryDisputes.from(row, mapper)),
        ResponseRows.rows(source, "milestonePayments", row -> MilestonePayments.from(row, mapper)),
        ResponseRows.rows(source, "sourceTemplates", row -> SourceTemplates.from(row, mapper)),
        ResponseRows.rows(source, "sourceReleases", row -> SourceReleases.from(row, mapper)),
        ResponseRows.rows(source, "sourcePurchases", row -> SourcePurchases.from(row, mapper)),
        ResponseRows.rows(source, "sourceDisputes", row -> SourceDisputes.from(row, mapper)),
        ResponseRows.rows(source, "maintenanceConsents", row -> MaintenanceConsents.from(row, mapper)),
        ResponseRows.rows(source, "maintenanceBilling", row -> MaintenanceBilling.from(row, mapper)),
        ResponseRows.rows(source, "maintenancePeriods", row -> MaintenancePeriods.from(row, mapper)),
        ResponseRows.rows(source, "maintenanceRefunds", row -> MaintenanceRefunds.from(row, mapper)),
        ResponseRows.rows(source, "maintenanceDisputes", row -> MaintenanceDisputes.from(row, mapper)),
        ResponseRows.rows(source, "maintenanceEvents", row -> MaintenanceEvents.from(row, mapper)),
        ResponseRows.rows(source, "maintenanceLedger", row -> MaintenanceLedger.from(row, mapper)),
        ResponseRows.rows(source, "maintenanceRequests", row -> MaintenanceRequests.from(row, mapper)),
        ResponseRows.rows(source, "maintenanceActions", row -> MaintenanceActions.from(row, mapper)),
        ResponseRows.rows(source, "components", row -> Components.from(row, mapper)),
        ResponseRows.rows(source, "componentReleases", row -> ComponentRelease.from(row, mapper)),
        ResponseRows.rows(source, "collegeContext", row -> CollegeContext.from(row, mapper)),
        ResponseRows.rows(source, "componentSlotPurchases", row -> ComponentSlotPurchases.from(row, mapper)),
        ResponseRows.rows(source, "componentSlotLedger", row -> ComponentSlotLedger.from(row, mapper)),
        HostedDemos.from(ResponseRows.row(source, "hostedDemos"), mapper));
  }

  public record Account(UUID id,
      String email,
      @JsonProperty("email_verified_at") Timestamp email_verified_at,
      @JsonProperty("created_at") Timestamp created_at) {
    public static Account from(Map<String, Object> row, ObjectMapper mapper) {
      return new Account(ResponseRows.uuid(row, "id"),
          ResponseRows.string(row, "email"),
          ResponseRows.timestamp(row, "email_verified_at"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record Profile(@JsonProperty("user_id") UUID user_id,
      String slug,
      @JsonProperty("display_name") String display_name,
      String headline,
      String bio,
      String technology,
      String category,
      @JsonProperty("availability_status") String availability_status,
      @JsonProperty("booked_until") Date booked_until,
      @JsonProperty("github_url") String github_url,
      @JsonProperty("linkedin_url") String linkedin_url,
      @JsonProperty("approval_status") String approval_status,
      @JsonProperty("moderation_reason") String moderation_reason,
      @JsonProperty("updated_at") Timestamp updated_at,
      @JsonProperty("website_url") String website_url,
      String country,
      @JsonProperty("time_zone") String time_zone,
      String languages,
      @JsonProperty("availability_confirmed_at") Timestamp availability_confirmed_at) {
    public static Profile from(Map<String, Object> row) { return from(row, null); }

    public static Profile from(Map<String, Object> row, ObjectMapper mapper) {
      return new Profile(ResponseRows.uuid(row, "user_id"),
          ResponseRows.string(row, "slug"),
          ResponseRows.string(row, "display_name"),
          ResponseRows.string(row, "headline"),
          ResponseRows.string(row, "bio"),
          ResponseRows.string(row, "technology"),
          ResponseRows.string(row, "category"),
          ResponseRows.string(row, "availability_status"),
          ResponseRows.date(row, "booked_until"),
          ResponseRows.string(row, "github_url"),
          ResponseRows.string(row, "linkedin_url"),
          ResponseRows.string(row, "approval_status"),
          ResponseRows.string(row, "moderation_reason"),
          ResponseRows.timestamp(row, "updated_at"),
          ResponseRows.string(row, "website_url"),
          ResponseRows.string(row, "country"),
          ResponseRows.string(row, "time_zone"),
          ResponseRows.string(row, "languages"),
          ResponseRows.timestamp(row, "availability_confirmed_at"));
    }
  }

  public record Products(UUID id,
      String slug,
      String title,
      String summary,
      String description,
      @JsonProperty("contribution_text") String contribution_text,
      @JsonProperty("approval_status") String approval_status,
      @JsonProperty("lifecycle_status") String lifecycle_status,
      @JsonProperty("created_at") Timestamp created_at) {
    public static Products from(Map<String, Object> row, ObjectMapper mapper) {
      return new Products(ResponseRows.uuid(row, "id"),
          ResponseRows.string(row, "slug"),
          ResponseRows.string(row, "title"),
          ResponseRows.string(row, "summary"),
          ResponseRows.string(row, "description"),
          ResponseRows.string(row, "contribution_text"),
          ResponseRows.string(row, "approval_status"),
          ResponseRows.string(row, "lifecycle_status"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record Requests(UUID id,
      @JsonProperty("client_email") String client_email,
      @JsonProperty("client_name") String client_name,
      String description,
      @JsonProperty("budget_band") String budget_band,
      @JsonProperty("timeline_band") String timeline_band,
      @JsonProperty("current_status") String current_status,
      @JsonProperty("created_at") Timestamp created_at) {
    public static Requests from(Map<String, Object> row, ObjectMapper mapper) {
      return new Requests(ResponseRows.uuid(row, "id"),
          ResponseRows.string(row, "client_email"),
          ResponseRows.string(row, "client_name"),
          ResponseRows.string(row, "description"),
          ResponseRows.string(row, "budget_band"),
          ResponseRows.string(row, "timeline_band"),
          ResponseRows.string(row, "current_status"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record SavedComponents(String slug, @JsonProperty("created_at") Timestamp created_at){
    static SavedComponents from(Map<String,Object> row){return new SavedComponents(ResponseRows.string(row,"slug"),ResponseRows.timestamp(row,"created_at"));}
  }
  public record SavedProducts(@JsonProperty("product_id") UUID product_id,
      @JsonProperty("created_at") Timestamp created_at) {
    public static SavedProducts from(Map<String, Object> row, ObjectMapper mapper) {
      return new SavedProducts(ResponseRows.uuid(row, "product_id"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record LegalAcceptances(@JsonProperty("document_version") String document_version,
      @JsonProperty("accepted_at") Timestamp accepted_at) {
    public static LegalAcceptances from(Map<String, Object> row, ObjectMapper mapper) {
      return new LegalAcceptances(ResponseRows.string(row, "document_version"),
          ResponseRows.timestamp(row, "accepted_at"));
    }
  }

  public record RepositoryVerifications(@JsonProperty("product_id") UUID product_id,
      @JsonProperty("repository_url") String repository_url,
      String status,
      @JsonProperty("requested_at") Timestamp requested_at,
      @JsonProperty("expires_at") Timestamp expires_at,
      @JsonProperty("reviewed_at") Timestamp reviewed_at,
      String reason) {
    public static RepositoryVerifications from(Map<String, Object> row, ObjectMapper mapper) {
      return new RepositoryVerifications(ResponseRows.uuid(row, "product_id"),
          ResponseRows.string(row, "repository_url"),
          ResponseRows.string(row, "status"),
          ResponseRows.timestamp(row, "requested_at"),
          ResponseRows.timestamp(row, "expires_at"),
          ResponseRows.timestamp(row, "reviewed_at"),
          ResponseRows.string(row, "reason"));
    }
  }

  public record EarnedCapacityAwards(@JsonProperty("inquiry_id") UUID inquiry_id,
      String reason,
      @JsonProperty("created_at") Timestamp created_at) {
    public static EarnedCapacityAwards from(Map<String, Object> row, ObjectMapper mapper) {
      return new EarnedCapacityAwards(ResponseRows.uuid(row, "inquiry_id"),
          ResponseRows.string(row, "reason"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record TeamStaffing(UUID id,
      @JsonProperty("team_id") UUID team_id,
      @JsonProperty("project_label") String project_label,
      String skills,
      @JsonProperty("ends_at") Timestamp ends_at,
      String status,
      @JsonProperty("created_at") Timestamp created_at) {
    public static TeamStaffing from(Map<String, Object> row, ObjectMapper mapper) {
      return new TeamStaffing(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "team_id"),
          ResponseRows.string(row, "project_label"),
          ResponseRows.string(row, "skills"),
          ResponseRows.timestamp(row, "ends_at"),
          ResponseRows.string(row, "status"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record TeamMemberships(@JsonProperty("team_id") UUID team_id,
      String role,
      @JsonProperty("membership_type") String membership_type,
      @JsonProperty("expires_at") Timestamp expires_at,
      @JsonProperty("project_label") String project_label,
      String name) {
    public static TeamMemberships from(Map<String, Object> row, ObjectMapper mapper) {
      return new TeamMemberships(ResponseRows.uuid(row, "team_id"),
          ResponseRows.string(row, "role"),
          ResponseRows.string(row, "membership_type"),
          ResponseRows.timestamp(row, "expires_at"),
          ResponseRows.string(row, "project_label"),
          ResponseRows.string(row, "name"));
    }
  }

  public record TeamInvitations(UUID id,
      @JsonProperty("team_id") UUID team_id,
      String role,
      @JsonProperty("membership_type") String membership_type,
      String status,
      @JsonProperty("expires_at") Timestamp expires_at,
      @JsonProperty("project_label") String project_label) {
    public static TeamInvitations from(Map<String, Object> row, ObjectMapper mapper) {
      return new TeamInvitations(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "team_id"),
          ResponseRows.string(row, "role"),
          ResponseRows.string(row, "membership_type"),
          ResponseRows.string(row, "status"),
          ResponseRows.timestamp(row, "expires_at"),
          ResponseRows.string(row, "project_label"));
    }
  }

  public record TeamApplications(UUID id,
      @JsonProperty("team_id") UUID team_id,
      @JsonProperty("role_id") UUID role_id,
      String message,
      String status) {
    public static TeamApplications from(Map<String, Object> row, ObjectMapper mapper) {
      return new TeamApplications(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "team_id"),
          ResponseRows.uuid(row, "role_id"),
          ResponseRows.string(row, "message"),
          ResponseRows.string(row, "status"));
    }
  }

  public record TeamRequests(UUID id,
      @JsonProperty("team_id") UUID team_id,
      String title,
      String description,
      String budget,
      String timeline,
      String status) {
    public static TeamRequests from(Map<String, Object> row, ObjectMapper mapper) {
      return new TeamRequests(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "team_id"),
          ResponseRows.string(row, "title"),
          ResponseRows.string(row, "description"),
          ResponseRows.string(row, "budget"),
          ResponseRows.string(row, "timeline"),
          ResponseRows.string(row, "status"));
    }
  }

  public record TeamProductConsent(@JsonProperty("team_id") UUID team_id,
      @JsonProperty("product_id") UUID product_id,
      @JsonProperty("created_at") Timestamp created_at) {
    public static TeamProductConsent from(Map<String, Object> row, ObjectMapper mapper) {
      return new TeamProductConsent(ResponseRows.uuid(row, "team_id"),
          ResponseRows.uuid(row, "product_id"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record BusinessMemberships(@JsonProperty("business_id") UUID business_id,
      String role,
      String name) {
    public static BusinessMemberships from(Map<String, Object> row, ObjectMapper mapper) {
      return new BusinessMemberships(ResponseRows.uuid(row, "business_id"),
          ResponseRows.string(row, "role"),
          ResponseRows.string(row, "name"));
    }
  }

  public record BusinessInvitations(UUID id,
      @JsonProperty("business_id") UUID business_id,
      String status,
      @JsonProperty("expires_at") Timestamp expires_at) {
    public static BusinessInvitations from(Map<String, Object> row, ObjectMapper mapper) {
      return new BusinessInvitations(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "business_id"),
          ResponseRows.string(row, "status"),
          ResponseRows.timestamp(row, "expires_at"));
    }
  }

  public record BusinessRequests(UUID id,
      @JsonProperty("business_id") UUID business_id,
      String title,
      String description,
      String category,
      String technology,
      String budget,
      String timeline,
      String status) {
    public static BusinessRequests from(Map<String, Object> row, ObjectMapper mapper) {
      return new BusinessRequests(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "business_id"),
          ResponseRows.string(row, "title"),
          ResponseRows.string(row, "description"),
          ResponseRows.string(row, "category"),
          ResponseRows.string(row, "technology"),
          ResponseRows.string(row, "budget"),
          ResponseRows.string(row, "timeline"),
          ResponseRows.string(row, "status"));
    }
  }

  public record SavedTalent(UUID id,
      @JsonProperty("list_id") UUID list_id,
      String kind,
      @JsonProperty("builder_id") UUID builder_id,
      @JsonProperty("team_id") UUID team_id) {
    public static SavedTalent from(Map<String, Object> row, ObjectMapper mapper) {
      return new SavedTalent(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "list_id"),
          ResponseRows.string(row, "kind"),
          ResponseRows.uuid(row, "builder_id"),
          ResponseRows.uuid(row, "team_id"));
    }
  }

  public record RequestShortlist(UUID id,
      @JsonProperty("request_id") UUID request_id,
      String kind,
      @JsonProperty("builder_id") UUID builder_id,
      @JsonProperty("team_id") UUID team_id,
      String reason,
      String source) {
    public static RequestShortlist from(Map<String, Object> row, ObjectMapper mapper) {
      return new RequestShortlist(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "request_id"),
          ResponseRows.string(row, "kind"),
          ResponseRows.uuid(row, "builder_id"),
          ResponseRows.uuid(row, "team_id"),
          ResponseRows.string(row, "reason"),
          ResponseRows.string(row, "source"));
    }
  }

  public record CommercialConsents(UUID id,
      @JsonProperty("engagement_id") UUID engagement_id,
      Integer revision,
      String status,
      String scope,
      String terms,
      @JsonProperty("amount_minor") Long amount_minor,
      String currency,
      JsonNode milestones,
      @JsonProperty("sent_at") Timestamp sent_at,
      @JsonProperty("accepted_at") Timestamp accepted_at) {
    public static CommercialConsents from(Map<String, Object> row, ObjectMapper mapper) {
      return new CommercialConsents(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "engagement_id"),
          ResponseRows.integer32(row, "revision"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "scope"),
          ResponseRows.string(row, "terms"),
          ResponseRows.integer64(row, "amount_minor"),
          ResponseRows.string(row, "currency"),
          ResponseRows.json(row, "milestones", mapper),
          ResponseRows.timestamp(row, "sent_at"),
          ResponseRows.timestamp(row, "accepted_at"));
    }
  }

  public record DeliveryDisputes(UUID id,
      @JsonProperty("engagement_id") UUID engagement_id,
      String reason,
      String status,
      String resolution,
      @JsonProperty("resolution_reason") String resolution_reason,
      @JsonProperty("created_at") Timestamp created_at,
      @JsonProperty("resolved_at") Timestamp resolved_at) {
    public static DeliveryDisputes from(Map<String, Object> row, ObjectMapper mapper) {
      return new DeliveryDisputes(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "engagement_id"),
          ResponseRows.string(row, "reason"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "resolution"),
          ResponseRows.string(row, "resolution_reason"),
          ResponseRows.timestamp(row, "created_at"),
          ResponseRows.timestamp(row, "resolved_at"));
    }
  }

  public record MilestonePayments(UUID id,
      @JsonProperty("milestone_id") UUID milestone_id,
      @JsonProperty("amount_minor") Long amount_minor,
      String currency,
      String mode,
      String status,
      @JsonProperty("order_id") String order_id,
      @JsonProperty("payment_id") String payment_id,
      @JsonProperty("refunded_minor") Long refunded_minor,
      @JsonProperty("transfer_status") String transfer_status,
      @JsonProperty("settlement_status") String settlement_status,
      @JsonProperty("created_at") Timestamp created_at) {
    public static MilestonePayments from(Map<String, Object> row, ObjectMapper mapper) {
      return new MilestonePayments(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "milestone_id"),
          ResponseRows.integer64(row, "amount_minor"),
          ResponseRows.string(row, "currency"),
          ResponseRows.string(row, "mode"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "order_id"),
          ResponseRows.string(row, "payment_id"),
          ResponseRows.integer64(row, "refunded_minor"),
          ResponseRows.string(row, "transfer_status"),
          ResponseRows.string(row, "settlement_status"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record SourceTemplates(UUID id,
      @JsonProperty("product_id") UUID product_id,
      String slug,
      String title,
      String summary,
      String description,
      @JsonProperty("price_minor") Long price_minor,
      String currency,
      @JsonProperty("license_terms") String license_terms,
      String status,
      @JsonProperty("created_at") Timestamp created_at) {
    public static SourceTemplates from(Map<String, Object> row, ObjectMapper mapper) {
      return new SourceTemplates(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "product_id"),
          ResponseRows.string(row, "slug"),
          ResponseRows.string(row, "title"),
          ResponseRows.string(row, "summary"),
          ResponseRows.string(row, "description"),
          ResponseRows.integer64(row, "price_minor"),
          ResponseRows.string(row, "currency"),
          ResponseRows.string(row, "license_terms"),
          ResponseRows.string(row, "status"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record SourceReleases(UUID id,
      @JsonProperty("template_id") UUID template_id,
      String version,
      @JsonProperty("release_notes") String release_notes,
      String sha256,
      @JsonProperty("size_bytes") Integer size_bytes,
      @JsonProperty("entry_count") Integer entry_count,
      @JsonProperty("license_terms") String license_terms,
      @JsonProperty("manifest_files") String manifest_files,
      @JsonProperty("rights_consented_at") Timestamp rights_consented_at,
      String status,
      @JsonProperty("review_reason") String review_reason,
      @JsonProperty("created_at") Timestamp created_at) {
    public static SourceReleases from(Map<String, Object> row, ObjectMapper mapper) {
      return new SourceReleases(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "template_id"),
          ResponseRows.string(row, "version"),
          ResponseRows.string(row, "release_notes"),
          ResponseRows.string(row, "sha256"),
          ResponseRows.integer32(row, "size_bytes"),
          ResponseRows.integer32(row, "entry_count"),
          ResponseRows.string(row, "license_terms"),
          ResponseRows.string(row, "manifest_files"),
          ResponseRows.timestamp(row, "rights_consented_at"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "review_reason"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record SourcePurchases(UUID id,
      @JsonProperty("template_id") UUID template_id,
      @JsonProperty("version_id") UUID version_id,
      String title,
      String version,
      @JsonProperty("license_terms") String license_terms,
      String sha256,
      @JsonProperty("amount_minor") Long amount_minor,
      String currency,
      String mode,
      String status,
      @JsonProperty("refunded_minor") Long refunded_minor,
      @JsonProperty("entitlement_revoked") Boolean entitlement_revoked,
      @JsonProperty("license_consented_at") Timestamp license_consented_at,
      @JsonProperty("created_at") Timestamp created_at) {
    public static SourcePurchases from(Map<String, Object> row, ObjectMapper mapper) {
      return new SourcePurchases(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "template_id"),
          ResponseRows.uuid(row, "version_id"),
          ResponseRows.string(row, "title"),
          ResponseRows.string(row, "version"),
          ResponseRows.string(row, "license_terms"),
          ResponseRows.string(row, "sha256"),
          ResponseRows.integer64(row, "amount_minor"),
          ResponseRows.string(row, "currency"),
          ResponseRows.string(row, "mode"),
          ResponseRows.string(row, "status"),
          ResponseRows.integer64(row, "refunded_minor"),
          ResponseRows.bool(row, "entitlement_revoked"),
          ResponseRows.timestamp(row, "license_consented_at"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record SourceDisputes(UUID id,
      @JsonProperty("purchase_id") UUID purchase_id,
      String reason,
      String status,
      @JsonProperty("resolution_reason") String resolution_reason,
      @JsonProperty("created_at") Timestamp created_at,
      @JsonProperty("resolved_at") Timestamp resolved_at) {
    public static SourceDisputes from(Map<String, Object> row, ObjectMapper mapper) {
      return new SourceDisputes(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "purchase_id"),
          ResponseRows.string(row, "reason"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "resolution_reason"),
          ResponseRows.timestamp(row, "created_at"),
          ResponseRows.timestamp(row, "resolved_at"));
    }
  }

  public record MaintenanceConsents(UUID id,
      @JsonProperty("engagement_id") UUID engagement_id,
      Integer revision,
      String status,
      String title,
      String scope,
      String terms,
      @JsonProperty("amount_minor") Long amount_minor,
      String currency,
      @JsonProperty("requests_per_cycle") Integer requests_per_cycle,
      @JsonProperty("response_hours") Integer response_hours,
      @JsonProperty("total_cycles") Integer total_cycles,
      String digest,
      @JsonProperty("seller_consented_at") Timestamp seller_consented_at,
      @JsonProperty("buyer_consented_at") Timestamp buyer_consented_at,
      @JsonProperty("created_at") Timestamp created_at) {
    public static MaintenanceConsents from(Map<String, Object> row, ObjectMapper mapper) {
      return new MaintenanceConsents(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "engagement_id"),
          ResponseRows.integer32(row, "revision"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "title"),
          ResponseRows.string(row, "scope"),
          ResponseRows.string(row, "terms"),
          ResponseRows.integer64(row, "amount_minor"),
          ResponseRows.string(row, "currency"),
          ResponseRows.integer32(row, "requests_per_cycle"),
          ResponseRows.integer32(row, "response_hours"),
          ResponseRows.integer32(row, "total_cycles"),
          ResponseRows.string(row, "digest"),
          ResponseRows.timestamp(row, "seller_consented_at"),
          ResponseRows.timestamp(row, "buyer_consented_at"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record MaintenanceBilling(UUID id,
      @JsonProperty("offer_id") UUID offer_id,
      @JsonProperty("amount_minor") Long amount_minor,
      String currency,
      String mode,
      String status,
      @JsonProperty("creation_step") String creation_step,
      @JsonProperty("plan_state") String plan_state,
      @JsonProperty("subscription_state") String subscription_state,
      @JsonProperty("provider_plan_id") String provider_plan_id,
      @JsonProperty("provider_subscription_id") String provider_subscription_id,
      @JsonProperty("cancel_requested") Boolean cancel_requested,
      @JsonProperty("cancel_confirmed") Boolean cancel_confirmed,
      @JsonProperty("cancel_state") String cancel_state,
      @JsonProperty("local_hold") Boolean local_hold,
      @JsonProperty("reconciled_at") Timestamp reconciled_at,
      @JsonProperty("billing_consented_at") Timestamp billing_consented_at,
      @JsonProperty("created_at") Timestamp created_at) {
    public static MaintenanceBilling from(Map<String, Object> row, ObjectMapper mapper) {
      return new MaintenanceBilling(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "offer_id"),
          ResponseRows.integer64(row, "amount_minor"),
          ResponseRows.string(row, "currency"),
          ResponseRows.string(row, "mode"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "creation_step"),
          ResponseRows.string(row, "plan_state"),
          ResponseRows.string(row, "subscription_state"),
          ResponseRows.string(row, "provider_plan_id"),
          ResponseRows.string(row, "provider_subscription_id"),
          ResponseRows.bool(row, "cancel_requested"),
          ResponseRows.bool(row, "cancel_confirmed"),
          ResponseRows.string(row, "cancel_state"),
          ResponseRows.bool(row, "local_hold"),
          ResponseRows.timestamp(row, "reconciled_at"),
          ResponseRows.timestamp(row, "billing_consented_at"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record MaintenancePeriods(UUID id,
      @JsonProperty("subscription_id") UUID subscription_id,
      @JsonProperty("provider_invoice_id") String provider_invoice_id,
      @JsonProperty("payment_id") String payment_id,
      @JsonProperty("order_id") String order_id,
      @JsonProperty("amount_minor") Long amount_minor,
      String currency,
      @JsonProperty("period_start") Timestamp period_start,
      @JsonProperty("period_end") Timestamp period_end,
      String status,
      @JsonProperty("refunded_minor") Long refunded_minor,
      @JsonProperty("pending_refund") Boolean pending_refund,
      @JsonProperty("transfer_state") String transfer_state,
      @JsonProperty("transfer_id") String transfer_id,
      @JsonProperty("reversed_minor") Long reversed_minor,
      @JsonProperty("transfer_status") String transfer_status,
      @JsonProperty("dispute_status") String dispute_status,
      @JsonProperty("reconciled_at") Timestamp reconciled_at) {
    public static MaintenancePeriods from(Map<String, Object> row, ObjectMapper mapper) {
      return new MaintenancePeriods(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "subscription_id"),
          ResponseRows.string(row, "provider_invoice_id"),
          ResponseRows.string(row, "payment_id"),
          ResponseRows.string(row, "order_id"),
          ResponseRows.integer64(row, "amount_minor"),
          ResponseRows.string(row, "currency"),
          ResponseRows.timestamp(row, "period_start"),
          ResponseRows.timestamp(row, "period_end"),
          ResponseRows.string(row, "status"),
          ResponseRows.integer64(row, "refunded_minor"),
          ResponseRows.bool(row, "pending_refund"),
          ResponseRows.string(row, "transfer_state"),
          ResponseRows.string(row, "transfer_id"),
          ResponseRows.integer64(row, "reversed_minor"),
          ResponseRows.string(row, "transfer_status"),
          ResponseRows.string(row, "dispute_status"),
          ResponseRows.timestamp(row, "reconciled_at"));
    }
  }

  public record MaintenanceRefunds(String id,
      @JsonProperty("period_id") UUID period_id,
      @JsonProperty("amount_minor") Long amount_minor,
      String status,
      @JsonProperty("created_at") Timestamp created_at,
      @JsonProperty("updated_at") Timestamp updated_at) {
    public static MaintenanceRefunds from(Map<String, Object> row, ObjectMapper mapper) {
      return new MaintenanceRefunds(ResponseRows.string(row, "id"),
          ResponseRows.uuid(row, "period_id"),
          ResponseRows.integer64(row, "amount_minor"),
          ResponseRows.string(row, "status"),
          ResponseRows.timestamp(row, "created_at"),
          ResponseRows.timestamp(row, "updated_at"));
    }
  }

  public record MaintenanceDisputes(String id,
      @JsonProperty("period_id") UUID period_id,
      String status,
      @JsonProperty("deducted_minor") Long deducted_minor,
      @JsonProperty("updated_at") Timestamp updated_at) {
    public static MaintenanceDisputes from(Map<String, Object> row, ObjectMapper mapper) {
      return new MaintenanceDisputes(ResponseRows.string(row, "id"),
          ResponseRows.uuid(row, "period_id"),
          ResponseRows.string(row, "status"),
          ResponseRows.integer64(row, "deducted_minor"),
          ResponseRows.timestamp(row, "updated_at"));
    }
  }

  public record MaintenanceEvents(@JsonProperty("event_id") String event_id,
      @JsonProperty("payload_hash") String payload_hash,
      @JsonProperty("event_kind") String event_kind,
      @JsonProperty("received_at") Timestamp received_at,
      @JsonProperty("processed_at") Timestamp processed_at,
      Integer attempts) {
    public static MaintenanceEvents from(Map<String, Object> row, ObjectMapper mapper) {
      return new MaintenanceEvents(ResponseRows.string(row, "event_id"),
          ResponseRows.string(row, "payload_hash"),
          ResponseRows.string(row, "event_kind"),
          ResponseRows.timestamp(row, "received_at"),
          ResponseRows.timestamp(row, "processed_at"),
          ResponseRows.integer32(row, "attempts"));
    }
  }

  public record MaintenanceLedger(UUID id,
      @JsonProperty("period_id") UUID period_id,
      String kind,
      @JsonProperty("amount_minor") Long amount_minor,
      String currency,
      @JsonProperty("created_at") Timestamp created_at) {
    public static MaintenanceLedger from(Map<String, Object> row, ObjectMapper mapper) {
      return new MaintenanceLedger(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "period_id"),
          ResponseRows.string(row, "kind"),
          ResponseRows.integer64(row, "amount_minor"),
          ResponseRows.string(row, "currency"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record MaintenanceRequests(UUID id,
      @JsonProperty("subscription_id") UUID subscription_id,
      @JsonProperty("period_id") UUID period_id,
      String title,
      String description,
      String status,
      @JsonProperty("delivery_note") String delivery_note,
      @JsonProperty("delivery_url") String delivery_url,
      @JsonProperty("response_due_at") Timestamp response_due_at,
      @JsonProperty("created_at") Timestamp created_at,
      @JsonProperty("resolved_at") Timestamp resolved_at) {
    public static MaintenanceRequests from(Map<String, Object> row, ObjectMapper mapper) {
      return new MaintenanceRequests(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "subscription_id"),
          ResponseRows.uuid(row, "period_id"),
          ResponseRows.string(row, "title"),
          ResponseRows.string(row, "description"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "delivery_note"),
          ResponseRows.string(row, "delivery_url"),
          ResponseRows.timestamp(row, "response_due_at"),
          ResponseRows.timestamp(row, "created_at"),
          ResponseRows.timestamp(row, "resolved_at"));
    }
  }

  public record MaintenanceActions(UUID id,
      @JsonProperty("offer_id") UUID offer_id,
      String kind,
      String detail,
      @JsonProperty("created_at") Timestamp created_at) {
    public static MaintenanceActions from(Map<String, Object> row, ObjectMapper mapper) {
      return new MaintenanceActions(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "offer_id"),
          ResponseRows.string(row, "kind"),
          ResponseRows.string(row, "detail"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record Components(UUID id,
      @JsonProperty("owner_id") UUID owner_id,
      @JsonProperty("recipe_slug") String recipe_slug,
      String slug,
      String title,
      String summary,
      String contribution,
      Long revision,
      String status,
      @JsonProperty("review_reason") String review_reason,
      @JsonProperty("published_at") Timestamp published_at,
      @JsonProperty("published_source") JsonNode published_source,
      @JsonProperty("published_context") JsonNode published_context,
      @JsonProperty("draft_source") JsonNode draft_source,
      @JsonProperty("submitted_source") JsonNode submitted_source,
      @JsonProperty("submitted_context") JsonNode submitted_context,
      @JsonProperty("withdrawn_at") Timestamp withdrawn_at,
      @JsonProperty("created_at") Timestamp created_at,
      @JsonProperty("updated_at") Timestamp updated_at) {
    public static Components from(Map<String, Object> row, ObjectMapper mapper) {
      return new Components(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "owner_id"),
          ResponseRows.string(row, "recipe_slug"),
          ResponseRows.string(row, "slug"),
          ResponseRows.string(row, "title"),
          ResponseRows.string(row, "summary"),
          ResponseRows.string(row, "contribution"),
          ResponseRows.integer64(row, "revision"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "review_reason"),
          ResponseRows.timestamp(row, "published_at"),
          componentSnapshot(row, "published_source", mapper, true),
          componentSnapshot(row, "published_context", mapper, false),
          componentSnapshot(row, "draft_source", mapper, true),
          componentSnapshot(row, "submitted_source", mapper, true),
          componentSnapshot(row, "submitted_context", mapper, false),
          ResponseRows.timestamp(row, "withdrawn_at"),
          ResponseRows.timestamp(row, "created_at"),
          ResponseRows.timestamp(row, "updated_at"));
    }
  }

  public record ComponentRelease(@JsonProperty("component_id") UUID component_id,
      Long revision, JsonNode source, JsonNode context,
      @JsonProperty("source_sha256") String source_sha256,
      @JsonProperty("published_at") Timestamp published_at) {
    public static ComponentRelease from(Map<String,Object> row,ObjectMapper mapper) {
      return new ComponentRelease(ResponseRows.uuid(row,"component_id"),ResponseRows.integer64(row,"revision"),
          componentSnapshot(row,"source",mapper,true),componentSnapshot(row,"context",mapper,false),
          ResponseRows.string(row,"source_sha256"),ResponseRows.timestamp(row,"published_at"));
    }
  }

  /** Export useful JSON snapshots rather than the JDBC JSON wrapper or duplicate ZIP bytes. */
  private static JsonNode componentSnapshot(Map<String,Object> row,String key,ObjectMapper mapper,boolean source) {
    Object value=row.get(key);
    if(value==null)return mapper.nullNode();
    try {
      JsonNode snapshot=value instanceof Map<?,?> || value instanceof JsonNode
          ? mapper.valueToTree(value) : mapper.readTree(value.toString());
      if(source && snapshot instanceof com.fasterxml.jackson.databind.node.ObjectNode object)object.remove("archiveBase64");
      return snapshot;
    }catch(java.io.IOException e){throw new IllegalStateException("Invalid component export snapshot",e);}
  }

  public record CollegeContext(@JsonProperty("product_id") UUID product_id,
      String category,
      String language,
      String problem,
      String outcome,
      String prerequisites,
      String contribution,
      String institution,
      @JsonProperty("academic_year") String academic_year,
      String branch,
      @JsonProperty("share_academic_details") Boolean share_academic_details,
      Long revision,
      String status,
      @JsonProperty("review_reason") String review_reason,
      @JsonProperty("updated_at") Timestamp updated_at) {
    public static CollegeContext from(Map<String, Object> row, ObjectMapper mapper) {
      return new CollegeContext(ResponseRows.uuid(row, "product_id"),
          ResponseRows.string(row, "category"),
          ResponseRows.string(row, "language"),
          ResponseRows.string(row, "problem"),
          ResponseRows.string(row, "outcome"),
          ResponseRows.string(row, "prerequisites"),
          ResponseRows.string(row, "contribution"),
          ResponseRows.string(row, "institution"),
          ResponseRows.string(row, "academic_year"),
          ResponseRows.string(row, "branch"),
          ResponseRows.bool(row, "share_academic_details"),
          ResponseRows.integer64(row, "revision"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "review_reason"),
          ResponseRows.timestamp(row, "updated_at"));
    }
  }

  public record ComponentSlotPurchases(UUID id,
      String pool,
      @JsonProperty("amount_minor") Long amount_minor,
      String currency,
      String mode,
      String status,
      @JsonProperty("order_id") String order_id,
      @JsonProperty("payment_id") String payment_id,
      @JsonProperty("refunded_minor") Long refunded_minor,
      @JsonProperty("dispute_status") String dispute_status,
      @JsonProperty("created_at") Timestamp created_at) {
    public static ComponentSlotPurchases from(Map<String, Object> row, ObjectMapper mapper) {
      return new ComponentSlotPurchases(ResponseRows.uuid(row, "id"),
          ResponseRows.string(row, "pool"),
          ResponseRows.integer64(row, "amount_minor"),
          ResponseRows.string(row, "currency"),
          ResponseRows.string(row, "mode"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "order_id"),
          ResponseRows.string(row, "payment_id"),
          ResponseRows.integer64(row, "refunded_minor"),
          ResponseRows.string(row, "dispute_status"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record ComponentSlotLedger(UUID id,
      @JsonProperty("purchase_id") UUID purchase_id,
      @JsonProperty("entry_key") String entry_key,
      String kind,
      @JsonProperty("amount_minor") Long amount_minor,
      @JsonProperty("created_at") Timestamp created_at) {
    public static ComponentSlotLedger from(Map<String, Object> row, ObjectMapper mapper) {
      return new ComponentSlotLedger(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "purchase_id"),
          ResponseRows.string(row, "entry_key"),
          ResponseRows.string(row, "kind"),
          ResponseRows.integer64(row, "amount_minor"),
          ResponseRows.timestamp(row, "created_at"));
    }
  }

  public record HostedDemos(List<HostedDemo> hosting) {
    public static HostedDemos from(Map<String, Object> row, ObjectMapper mapper) {
      return new HostedDemos(ResponseRows.rows(row, "hosting", entry -> HostedDemo.from(entry, mapper)));
    }
  }

  public record HostedDemo(UUID id,
      UUID productId,
      String productTitle,
      String title,
      String version,
      String status,
      String deploymentState,
      String desiredState,
      String archiveSha256,
      String manifestSha256,
      Long sizeBytes,
      Long expandedBytes,
      Integer fileCount,
      UUID deploymentId,
      String reviewReason,
      String rightsConsentAt,
      String expiresAt,
      String createdAt,
      String updatedAt,
      String url,
      List<HostedFile> files,
      List<HostedAudit> audit) {
    public static HostedDemo from(Map<String, Object> row, ObjectMapper mapper) {
      return new HostedDemo(ResponseRows.uuid(row, "id"),
          ResponseRows.uuid(row, "productId"),
          ResponseRows.string(row, "productTitle"),
          ResponseRows.string(row, "title"),
          ResponseRows.string(row, "version"),
          ResponseRows.string(row, "status"),
          ResponseRows.string(row, "deploymentState"),
          ResponseRows.string(row, "desiredState"),
          ResponseRows.string(row, "archiveSha256"),
          ResponseRows.string(row, "manifestSha256"),
          ResponseRows.integer64(row, "sizeBytes"),
          ResponseRows.integer64(row, "expandedBytes"),
          ResponseRows.integer32(row, "fileCount"),
          ResponseRows.uuid(row, "deploymentId"),
          ResponseRows.string(row, "reviewReason"),
          ResponseRows.string(row, "rightsConsentAt"),
          ResponseRows.string(row, "expiresAt"),
          ResponseRows.string(row, "createdAt"),
          ResponseRows.string(row, "updatedAt"),
          ResponseRows.string(row, "url"),
          ResponseRows.rows(row, "files", HostedFile::from),
          ResponseRows.rows(row, "audit", HostedAudit::from));
    }
  }

  public record HostedFile(String path, String sha256, Long sizeBytes) {
    public static HostedFile from(Map<String,Object> row) {
      return new HostedFile(ResponseRows.string(row,"path"),ResponseRows.string(row,"sha256"),ResponseRows.integer64(row,"sizeBytes"));
    }
  }
  public record HostedAudit(UUID id, String action, String detail, String createdAt) {
    public static HostedAudit from(Map<String,Object> row) {
      return new HostedAudit(ResponseRows.uuid(row,"id"),ResponseRows.string(row,"action"),ResponseRows.string(row,"detail"),ResponseRows.string(row,"createdAt"));
    }
  }

}
