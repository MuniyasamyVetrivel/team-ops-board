package com.teamops.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Leaves an entity, or one of its fields, out of the automatic record history ({@link EntityChangeAuditor}). Use it
 * only for machine data with no business meaning (sign-in timestamps, counters) or for whole entities that are logs
 * or per-user state themselves. Passwords, tokens and secrets are redacted by name already.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface AuditIgnore {

}
