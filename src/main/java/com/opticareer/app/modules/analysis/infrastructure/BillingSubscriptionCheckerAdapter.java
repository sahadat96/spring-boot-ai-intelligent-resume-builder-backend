package com.opticareer.app.modules.analysis.infrastructure;

import com.opticareer.app.modules.analysis.application.SubscriptionCheckerPort;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verifies whether a user has an active or trialing paid subscription in billing.subscriptions.
 */
@Component
public class BillingSubscriptionCheckerAdapter implements SubscriptionCheckerPort {

    private static final Logger log = LoggerFactory.getLogger(BillingSubscriptionCheckerAdapter.class);

    private final @Nullable JdbcTemplate jdbc;

    public BillingSubscriptionCheckerAdapter(@Nullable JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isPremium(UUID ownerId) {
        if (jdbc == null || ownerId == null) {
            return false;
        }
        try {
            Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(1)
                FROM billing.subscriptions
                WHERE user_id = ?
                  AND UPPER(status) IN ('ACTIVE', 'TRIALING')
                  AND current_period_end > now()
                """,
                Integer.class,
                ownerId
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("Subscription lookup unavailable for owner {}: {}", ownerId, e.getMessage());
            return false;
        }
    }
}
