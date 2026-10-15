package com.teamops.common.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.annotations.ValueGenerationType;

/**
 * Fills a user-id column with the signed-in user when the row is written ({@link CurrentActorGenerator}). Without
 * {@code onUpdate} it is a creator stamp: set on insert, and an id the code assigned first is kept.
 */
@ValueGenerationType(generatedBy = CurrentActorGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface CurrentActor {

	/** Also stamp the column on every update (an "updated by" column). */
	boolean onUpdate() default false;

}
