package com.opticareer.app.platform.persistence;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/** Lease predicates need wall time rather than PostgreSQL's transaction-start timestamp. */
public final class PostgresFunctions implements FunctionContributor {
  @Override
  public void contributeFunctions(FunctionContributions functions) {
    functions
        .getFunctionRegistry()
        .registerNamed(
            "clock_timestamp",
            functions
                .getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.INSTANT));
  }
}
