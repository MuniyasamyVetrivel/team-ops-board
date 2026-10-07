package com.teamops.user.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamops.user.dto.PermissionResponse;
import com.teamops.user.dto.RoleResponse;
import com.teamops.user.repository.PermissionRepository;
import com.teamops.user.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/** Read-only role and permission catalogue for the access editor. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessCatalogService {

	private final RoleRepository roleRepository;

	private final PermissionRepository permissionRepository;

	public List<RoleResponse> roles() {
		return roleRepository.findAll().stream().map(RoleResponse::of).sorted(Comparator.comparing(RoleResponse::id)).toList();
	}

	public List<PermissionResponse> permissions() {
		return permissionRepository.findAll()
			.stream()
			.map(PermissionResponse::of)
			.sorted(Comparator.comparing(PermissionResponse::module).thenComparing(PermissionResponse::code))
			.toList();
	}

}
