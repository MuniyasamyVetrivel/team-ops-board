package com.teamops.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A money amount. Its changes are logged, but only users who can see finance ({@code FINANCE_VIEW}) see the values in
 * audit history; everyone else sees that it changed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AuditFinance {

}
