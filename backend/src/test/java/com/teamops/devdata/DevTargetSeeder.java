package com.teamops.devdata;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.config.BusinessCalendar;
import com.teamops.department.entity.Department;
import com.teamops.department.repository.DepartmentRepository;
import com.teamops.marketing.common.MarketingPeriod;
import com.teamops.marketing.target.entity.MarketingTarget;
import com.teamops.marketing.target.entity.TargetType;
import com.teamops.marketing.target.repository.MarketingTargetRepository;
import com.teamops.marketing.target.repository.TargetTypeRepository;
import com.teamops.user.entity.User;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Monthly targets for the two months before this one, this month and next month (brief sections 33 and 73–75:
 * Website Leads 200/185, 220/210, 250/200; Blogs 12/9; Backlinks 50 with 22 live). Keywords in Top 10 and Landing
 * Pages Created compute their actuals from the SEO data; the other actuals are entered by hand until their modules
 * exist. Runs only while there are no targets.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.dev-seed.enabled")
@RequiredArgsConstructor
class DevTargetSeeder {

	/** Target and actual (null = none) per month offset: -2, -1, 0 (this month), +1 (planned). */
	private static final List<Seed> SEEDS = List.of(
			new Seed("WEBSITE_LEADS", new String[][] { { "200", "185" }, { "220", "210" }, { "250", "200" }, { "260", null } }),
			new Seed("BLOGS_PUBLISHED", new String[][] { { "10", "10" }, { "12", "11" }, { "12", "9" }, { "12", null } }),
			new Seed("BACKLINKS", new String[][] { { "40", "38" }, { "45", "41" }, { "50", "22" }, null }),
			new Seed("KEYWORDS_TOP10", new String[][] { { "3", null }, { "4", null }, { "6", null }, { "7", null } }),
			new Seed("LANDING_PAGES_CREATED", new String[][] { null, null, { "2", null }, null }),
			new Seed("MARKETING_PROSPECTS", new String[][] { null, { "35", "36" }, { "40", "31" }, null }));

	private final TargetTypeRepository typeRepository;

	private final MarketingTargetRepository targetRepository;

	private final DepartmentRepository departmentRepository;

	private final UserRepository userRepository;

	private final BusinessCalendar calendar;

	/** @return the number of targets created (0 when targets already exist) */
	@Transactional
	public int seedIfEmpty() {
		if (targetRepository.count() > 0) {
			return 0;
		}
		Department marketing = departmentRepository.findByCode("DM").orElseThrow();
		User owner = userRepository.findByEmailIgnoreCase("priya.menon@teamops.local").orElse(null);
		Map<String, TargetType> types = typeRepository.findAll()
			.stream()
			.collect(Collectors.toMap(TargetType::getCode, Function.identity()));
		MarketingPeriod current = MarketingPeriod.of(calendar.today());
		int created = 0;
		for (Seed seed : SEEDS) {
			TargetType type = types.get(seed.code());
			if (type == null) {
				continue;
			}
			for (int i = 0; i < seed.months().length; i++) {
				String[] values = seed.months()[i];
				if (values == null) {
					continue;
				}
				MarketingPeriod period = current.plusMonths(i - 2);
				MarketingTarget target = new MarketingTarget();
				target.setType(type);
				target.setMonth(period.month());
				target.setYear(period.year());
				target.setTargetValue(new BigDecimal(values[0]));
				target.setActualValue(values[1] == null ? null : new BigDecimal(values[1]));
				target.setOwner(owner);
				target.setDepartment(marketing);
				targetRepository.save(target);
				created++;
			}
		}
		return created;
	}

	record Seed(String code, String[][] months) {
	}

}
