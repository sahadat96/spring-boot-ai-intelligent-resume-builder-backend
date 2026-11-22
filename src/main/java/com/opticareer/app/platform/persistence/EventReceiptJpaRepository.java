package com.opticareer.app.platform.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventReceiptJpaRepository
    extends JpaRepository<EventReceiptEntity, EventReceiptId> {}
