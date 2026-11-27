package com.opticareer.app.modules.analysis.application;

import java.util.UUID;

/**
 * Port checking whether the user holds an active paid subscription eligible for AI-enhanced suggestions.
 */
public interface SubscriptionCheckerPort {
    boolean isPremium(UUID ownerId);
}
