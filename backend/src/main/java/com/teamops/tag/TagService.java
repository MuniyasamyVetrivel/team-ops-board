package com.teamops.tag;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.common.exception.ApiException;

import lombok.RequiredArgsConstructor;

/** Resolves tag names to tags, creating missing ones. Names are trimmed and lower-cased. */
@Service
@RequiredArgsConstructor
public class TagService {

	public static final int MAX_TAGS = 10;

	public static final int MAX_NAME_LENGTH = 40;

	private final TagRepository tagRepository;

	@Transactional(propagation = Propagation.MANDATORY)
	public Set<Tag> resolve(List<String> names) {
		Map<String, String> normalised = new LinkedHashMap<>();
		for (String name : names == null ? List.<String>of() : names) {
			String clean = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
			if (clean.isEmpty()) {
				continue;
			}
			if (clean.length() > MAX_NAME_LENGTH) {
				throw ApiException.badRequest("INVALID_TAG", "Tags can be at most " + MAX_NAME_LENGTH + " characters");
			}
			normalised.put(clean, clean);
		}
		if (normalised.size() > MAX_TAGS) {
			throw ApiException.badRequest("TOO_MANY_TAGS", "A task can have at most " + MAX_TAGS + " tags");
		}
		Set<Tag> tags = new HashSet<>(tagRepository.findByNameIn(normalised.keySet()));
		tags.forEach(tag -> normalised.remove(tag.getName().toLowerCase(Locale.ROOT)));
		for (String name : normalised.keySet()) {
			Tag tag = new Tag();
			tag.setName(name);
			tags.add(tagRepository.save(tag));
		}
		return tags;
	}

}
