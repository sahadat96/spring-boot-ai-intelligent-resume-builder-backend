package com.opticareer.app.modules.dashboard.application;

import java.util.UUID;

public interface PlanInfoPort {

    record PlanInfo(String code, String label) {}

    PlanInfo currentPlan(UUID ownerId);
}
