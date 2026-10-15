package com.teamops.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import com.teamops.auth.entity.RefreshToken;
import com.teamops.common.audit.AuditCatalog.Module;
import com.teamops.common.persistence.AuditedEntity;
import com.teamops.common.settings.AppSetting;
import com.teamops.knowledge.entity.KnowledgeArticle;
import com.teamops.marketing.lead.entity.MarketingLead;
import com.teamops.marketing.seo.entity.KeywordRanking;
import com.teamops.marketing.target.entity.TargetType;
import com.teamops.notification.entity.Notification;
import com.teamops.task.entity.Task;
import com.teamops.task.entity.TaskComment;

import jakarta.persistence.Entity;

/** Every entity has a record type, a module and the audit stamps; types match the modules' own audit entries. */
class RecordTypesTest {

	@Test
	void typesAreUpperSnakeClassNamesUnlessTheEntitySaysOtherwise() {
		assertThat(RecordTypes.typeOf(Task.class)).isEqualTo("TASK");
		assertThat(RecordTypes.typeOf(MarketingLead.class)).isEqualTo("MARKETING_LEAD");
		assertThat(RecordTypes.typeOf(TaskComment.class)).isEqualTo("TASK_COMMENT");
		// The names the modules already used for their own entries, so a record's history holds both.
		assertThat(RecordTypes.typeOf(KeywordRanking.class)).isEqualTo("SEO_RANKING");
		assertThat(RecordTypes.typeOf(TargetType.class)).isEqualTo("MARKETING_TARGET_TYPE");
		assertThat(RecordTypes.typeOf(KnowledgeArticle.class)).isEqualTo("ARTICLE");
		assertThat(RecordTypes.typeOf(AppSetting.class)).isEqualTo("SETTING");
		assertThat(RecordTypes.labelOf("MARKETING_LEAD")).isEqualTo("Marketing lead");
	}

	@Test
	void modulesFollowThePackage() {
		assertThat(RecordTypes.moduleOf(Task.class)).isEqualTo(Module.TASKS);
		assertThat(RecordTypes.moduleOf(MarketingLead.class)).isEqualTo(Module.LEADS);
		assertThat(RecordTypes.moduleOf(KeywordRanking.class)).isEqualTo(Module.SEO);
		assertThat(RecordTypes.moduleOf(AppSetting.class)).isEqualTo(Module.SETTINGS);
		assertThat(RecordTypes.moduleOf(String.class)).isNull();
	}

	@Test
	void everyEntityIsAuditedWithAModuleAndAUniqueTypeOrIsDeliberatelyLeftOut() throws Exception {
		Map<String, String> typeOwners = new HashMap<>();
		List<Class<?>> entities = entities();
		assertThat(entities).hasSizeGreaterThan(50);
		for (Class<?> entity : entities) {
			if (!RecordTypes.isAudited(entity)) {
				continue;
			}
			assertThat(RecordTypes.moduleOf(entity)).as(entity.getName() + " needs a module in RecordTypes")
				.isNotNull();
			assertThat(AuditedEntity.class.isAssignableFrom(entity))
				.as(entity.getName() + " needs created/updated at and by (extend BaseEntity or AuditedEntity)")
				.isTrue();
			String previous = typeOwners.put(RecordTypes.typeOf(entity), entity.getName());
			assertThat(previous).as("record type " + RecordTypes.typeOf(entity) + " is used twice").isNull();
		}
		assertThat(RecordTypes.isAudited(AuditLog.class)).isFalse();
		assertThat(RecordTypes.isAudited(RefreshToken.class)).isFalse();
		assertThat(RecordTypes.isAudited(Notification.class)).isFalse();
	}

	@Test
	void childRecordsNameTheirParent() {
		Task task = new Task() {
			@Override
			public Long getId() {
				return 42L;
			}
		};
		TaskComment comment = new TaskComment();
		comment.setTask(task);
		assertThat(comment.auditParent()).isEqualTo(new AuditRef("TASK", 42L));
		assertThat(new TaskComment().auditParent()).isNull();
	}

	private static List<Class<?>> entities() throws ClassNotFoundException {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
		List<Class<?>> result = new ArrayList<>();
		for (var candidate : scanner.findCandidateComponents("com.teamops")) {
			result.add(ClassUtils.forName(candidate.getBeanClassName(), RecordTypesTest.class.getClassLoader()));
		}
		return result;
	}

}
