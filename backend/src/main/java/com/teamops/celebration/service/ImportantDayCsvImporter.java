package com.teamops.celebration.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.teamops.celebration.entity.DayRecurrence;
import com.teamops.celebration.entity.ImportantDay;
import com.teamops.celebration.entity.ImportantDayCategory;
import com.teamops.celebration.entity.Region;
import com.teamops.celebration.repository.ImportantDayRepository;
import com.teamops.common.csv.CsvColumn;
import com.teamops.common.csv.CsvImporter;
import com.teamops.common.csv.RowReader;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Imports important days, e.g. next year's festival calendar. The columns match the important days export. A day
 * already on record (same name, date and region) is an error.
 */
@Component
@RequiredArgsConstructor
public class ImportantDayCsvImporter implements CsvImporter<ImportantDayCsvImporter.Row> {

	public static final String TYPE = "important-days";

	private static final List<CsvColumn> COLUMNS = List.of(
			CsvColumn.required("name", "Name of the day", "Diwali"),
			CsvColumn.required("date", "YYYY-MM-DD (a yearly day's first year)", "2026-11-08"),
			CsvColumn.required("category", "FESTIVAL, NATIONAL_DAY, INDUSTRY_DAY, COMPANY_MILESTONE or OTHER", "FESTIVAL"),
			CsvColumn.required("region", "AUSTRALIA, INDIA or GLOBAL", "INDIA"),
			CsvColumn.optional("repeat", "YEARLY (same date every year) or ONE_OFF (default)", "ONE_OFF"),
			CsvColumn.optional("description", "Up to 2000 characters", "Festival of lights"),
			CsvColumn.optional("marketing_notes", "Up to 5000 characters", "Greeting creative and LinkedIn post"));

	private final ImportantDayRepository repository;

	private final UserRepository userRepository;

	public record Row(String name, LocalDate date, ImportantDayCategory category, Region region,
			DayRecurrence recurrence, String description, String marketingNotes) {
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public String label() {
		return "Important days";
	}

	@Override
	public String description() {
		return "Festivals, national and industry days and company milestones. Days already on record are rejected.";
	}

	@Override
	public String permission() {
		return CelebrationAccess.EDIT_PERMISSION;
	}

	@Override
	public List<CsvColumn> columns() {
		return COLUMNS;
	}

	@Override
	public ImportSession<Row> open(AuthenticatedUser actor) {
		return new ImportSession<>() {

			@Override
			public Row parse(RowReader row) {
				String name = row.text("name", 150);
				LocalDate date = row.date("date");
				ImportantDayCategory category = row.choice("category", ImportantDayCategory.class);
				Region region = row.choice("region", Region.class);
				DayRecurrence recurrence = row.choice("repeat", DayRecurrence.class);
				String description = row.text("description", 2000);
				String notes = row.text("marketing_notes", 5000);
				if (!row.valid()) {
					return null;
				}
				if (repository.existsByNameAndDateAndRegion(name, date, region)) {
					row.error("name", name + " is already on " + date + " for " + region.label());
					return null;
				}
				return new Row(name, date, category, region, recurrence == null ? DayRecurrence.ONE_OFF : recurrence,
						description, notes);
			}

			@Override
			public String key(Row record) {
				return record.name().toLowerCase(Locale.ROOT) + "|" + record.date() + "|" + record.region();
			}

			@Override
			public int commit(List<Row> records) {
				for (Row record : records) {
					ImportantDay day = new ImportantDay();
					day.setName(record.name());
					day.setDate(record.date());
					day.setCategory(record.category());
					day.setRegion(record.region());
					day.setRecurrence(record.recurrence());
					day.setDescription(record.description());
					day.setMarketingNotes(record.marketingNotes());
					day.setCreatedBy(userRepository.getReferenceById(actor.id()));
					repository.save(day);
				}
				return records.size();
			}

		};
	}

}
