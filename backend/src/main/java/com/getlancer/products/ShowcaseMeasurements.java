package com.getlancer.products;

import com.getlancer.shared.Support;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ShowcaseMeasurements {
  private final JdbcTemplate db;
  public ShowcaseMeasurements(JdbcTemplate db) { this.db = db; }

  // This measurement survives the rejected activation transaction. The analytics
  // table has no product/user FK, so it does not wait on the parent's row locks.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void blocked(UUID builder, UUID product, int activeCount) {
    db.update("INSERT INTO analytics_events(id,event_name,entity_id,context) VALUES(?,'showcase_capacity_blocked',?,jsonb_build_object('builderId',?::text,'activeCount',?::integer))",
        Support.id(), product, builder.toString(), activeCount);
  }
}
