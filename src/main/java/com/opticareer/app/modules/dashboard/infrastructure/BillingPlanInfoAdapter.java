package com.opticareer.app.modules.dashboard.infrastructure;

import com.opticareer.app.modules.dashboard.application.PlanInfoPort;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves user's active subscription plan from billing.subscriptions joined with billing.plans,
 * falling back cleanly to FREE / "Free plan" when no active paid subscription exists.
 */
@Component
public class BillingPlanInfoAdapter implements PlanInfoPort {

    private static final Logger log = LoggerFactory.getLogger(BillingPlanInfoAdapter.class);
    private static final PlanInfo FREE_PLAN = new PlanInfo("FREE", "Free plan");

    private final @Nullable JdbcTemplate jdbc;

    public BillingPlanInfoAdapter(@Nullable JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PlanInfo currentPlan(UUID ownerId) {
        if (jdbc == null || ownerId == null) {
            return FREE_PLAN;
        }
        try {
            var rows = jdbc.query(
                """
                SELECT s.plan_code, p.name
                FROM billing.subscriptions s
                LEFT JOIN billing.plans p ON p.code = s.plan_code
                WHERE s.user_id = ?
                  AND UPPER(s.status) IN ('ACTIVE', 'TRIALING')
                  AND s.current_period_end > now()
                ORDER BY s.current_period_end DESC
                LIMIT 1
                """,
                (rs, rowNum) -> {
                    String code = rs.getString("plan_code");
                    String name = rs.getString("name");
                    String cleanCode = code != null ? code.toUpperCase(Locale.ROOT) : "PRO";
                    String label = (name != null && !name.isBlank()) ? name : formatPlanLabel(cleanCode);
                    return new PlanInfo(cleanCode, label);
                },
                ownerId
            );
            return rows.isEmpty() ? FREE_PLAN : rows.get(0);
        } catch (Exception e) {
            log.debug("Billing plan lookup unavailable for owner {}: {}", ownerId, e.getMessage());
            return FREE_PLAN;
        }
    }

    private String formatPlanLabel(String code) {
        if ("FREE".equalsIgnoreCase(code)) return "Free plan";
        String lower = code.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1) + " plan";
    }
}
