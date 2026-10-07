package com.teamops.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (task reminders now; recurring marketing activities later). Off with APP_SCHEDULING_ENABLED=false. */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "app.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig {

}
