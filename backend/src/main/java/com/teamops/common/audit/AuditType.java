package com.teamops.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The entity type an entity's audit entries are filed under, when it differs from the class name in upper snake case
 * (for example when the module's own audit entries already use another name).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditType {

	String value();

}
