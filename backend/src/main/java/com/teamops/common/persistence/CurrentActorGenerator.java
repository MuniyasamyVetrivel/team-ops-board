package com.teamops.common.persistence;

import java.lang.reflect.Member;
import java.util.EnumSet;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;

import com.teamops.common.audit.AuditActor;

/** Generates {@link CurrentActor} values: the signed-in user's id, or null for the system. */
public class CurrentActorGenerator implements BeforeExecutionGenerator {

	private final EnumSet<EventType> eventTypes;

	public CurrentActorGenerator(CurrentActor annotation, Member member, GeneratorCreationContext context) {
		this.eventTypes = annotation.onUpdate() ? EnumSet.of(EventType.INSERT, EventType.UPDATE)
				: EnumSet.of(EventType.INSERT);
	}

	@Override
	public EnumSet<EventType> getEventTypes() {
		return eventTypes;
	}

	@Override
	public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
			EventType eventType) {
		if (eventType == EventType.INSERT && currentValue != null) {
			return currentValue;
		}
		return AuditActor.currentUserId();
	}

}
