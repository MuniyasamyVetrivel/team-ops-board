package com.teamops.vendor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.teamops.common.audit.AuditService;
import com.teamops.common.config.BusinessCalendar;
import com.teamops.common.exception.ApiException;
import com.teamops.common.security.AuthenticatedUser;
import com.teamops.common.web.ClientInfo;
import com.teamops.recyclebin.service.RecycleBinService;
import com.teamops.support.SliceAuth;
import com.teamops.vendor.dto.VendorDtos.SaveContact;
import com.teamops.vendor.dto.VendorDtos.SaveVendor;
import com.teamops.vendor.entity.Vendor;
import com.teamops.vendor.entity.VendorContact;
import com.teamops.vendor.repository.VendorContractRepository;
import com.teamops.vendor.repository.VendorRepository;
import com.teamops.vendor.service.VendorLinkSource.Link;
import com.teamops.vendor.service.VendorLinkSource.Links;

/** Contacts follow the request, deletes respect contracts and other modules' links, links respect who is looking. */
class VendorServiceTest {

	private final VendorRepository vendors = mock(VendorRepository.class);

	private final VendorContractRepository contracts = mock(VendorContractRepository.class);

	private final RecycleBinService recycleBin = mock(RecycleBinService.class);

	private final AuditService audit = mock(AuditService.class);

	private final BusinessCalendar calendar = new BusinessCalendar(
			Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneOffset.UTC), "Asia/Kolkata");

	/** A licence source the way Phase X8 will add one: finance records, visible only with FINANCE_VIEW. */
	private final VendorLinkSource licences = new VendorLinkSource() {

		@Override
		public String kind() {
			return "LICENCES";
		}

		@Override
		public String label() {
			return "Tools and licences";
		}

		@Override
		public boolean visibleTo(AuthenticatedUser viewer) {
			return viewer.hasPermission("FINANCE_VIEW");
		}

		@Override
		public Links linksFor(Long vendorId, int limit, AuthenticatedUser viewer) {
			return new Links(3, List.of(new Link(1L, "Jira Cloud", "18 of 20 seats", "ACTIVE")));
		}

		@Override
		public long countUsing(Long vendorId) {
			return vendorId == 5L ? 3 : 0;
		}

	};

	private final VendorService service = new VendorService(vendors, contracts, List.of(licences), audit, recycleBin,
			calendar);

	@Test
	void contactsAreUpdatedByIdRemovedWhenLeftOutAndAddedInOrder() {
		Vendor vendor = new Vendor();
		VendorContact priya = contact(vendor, 11L, "Priya");
		VendorContact arun = contact(vendor, 12L, "Arun");
		vendor.getContacts().addAll(List.of(priya, arun));

		VendorService.replaceContacts(vendor, List.of(new SaveContact(null, " Meera ", "Billing", " Meera@Example.COM ", ""),
				new SaveContact(11L, "Priya Shah", " Account Executive ", null, "+61 2 9000 0000")));

		assertThat(vendor.getContacts()).extracting(VendorContact::getName).containsExactly("Meera", "Priya Shah");
		assertThat(vendor.getContacts().get(0).getEmail()).isEqualTo("meera@example.com");
		assertThat(vendor.getContacts().get(0).getPhone()).isNull();
		assertThat(vendor.getContacts().get(1)).isSameAs(priya);
		assertThat(priya.getRole()).isEqualTo("Account Executive");
		assertThat(vendor.getContacts()).doesNotContain(arun);
	}

	@Test
	void aContactOfAnotherVendorIsRefused() {
		Vendor vendor = new Vendor();
		assertThatThrownBy(() -> VendorService.replaceContacts(vendor, List.of(new SaveContact(99L, "Someone", null, null,
				null)))).isInstanceOf(ApiException.class).hasMessageContaining("does not belong");
	}

	@Test
	void aVendorWithContractsOrLinkedRecordsStaysOutOfTheBin() {
		Vendor withContracts = vendor(4L, "Microsoft");
		when(vendors.findById(4L)).thenReturn(Optional.of(withContracts));
		when(contracts.existsByVendorId(4L)).thenReturn(true);
		assertThatThrownBy(() -> service.delete(4L, SliceAuth.SUPER_ADMIN, ClientInfo.unknown()))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("VENDOR_HAS_CONTRACTS"));

		Vendor usedByLicences = vendor(5L, "Atlassian");
		when(vendors.findById(5L)).thenReturn(Optional.of(usedByLicences));
		assertThatThrownBy(() -> service.delete(5L, SliceAuth.SUPER_ADMIN, ClientInfo.unknown()))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getCode()).isEqualTo("VENDOR_IN_USE");
				assertThat(ex.getMessage()).contains("3 tools and licences");
			});
		verify(recycleBin, never()).moveToBin(any(), any());

		Vendor unused = vendor(6L, "Canva");
		when(vendors.findById(6L)).thenReturn(Optional.of(unused));
		service.delete(6L, SliceAuth.SUPER_ADMIN, ClientInfo.unknown());
		verify(recycleBin).moveToBin(unused, SliceAuth.SUPER_ADMIN);
	}

	@Test
	void linkedRecordsAreListedOnlyToThoseTheirModuleShowsThemTo() {
		when(vendors.findById(5L)).thenReturn(Optional.of(vendor(5L, "Atlassian")));

		assertThat(service.links(5L, SliceAuth.SUPER_ADMIN)).singleElement().satisfies(group -> {
			assertThat(group.kind()).isEqualTo("LICENCES");
			assertThat(group.total()).isEqualTo(3);
			assertThat(group.items()).extracting(Link::title).containsExactly("Jira Cloud");
		});
		AuthenticatedUser vendorViewer = new AuthenticatedUser(14L, "hr@teamops.local", "HR", 2L,
				Set.of("EMPLOYEE"), Set.of("VENDOR_VIEW"));
		assertThat(service.links(5L, vendorViewer)).isEmpty();
	}

	@Test
	void websitesMustBeWebAddresses() {
		when(vendors.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
		SaveVendor request = new SaveVendor("Canva", null, null, null, null, "canva.com", null, null, null, null, null,
				null);
		assertThatThrownBy(() -> service.create(request, SliceAuth.SUPER_ADMIN, ClientInfo.unknown()))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_WEBSITE"));
		assertThat(VendorText.isWebsite("https://www.canva.com/pricing")).isTrue();
		assertThat(VendorText.isWebsite("javascript:alert(1)")).isFalse();
	}

	private static Vendor vendor(Long id, String name) {
		Vendor vendor = new Vendor();
		ReflectionTestUtils.setField(vendor, "id", id);
		vendor.setName(name);
		return vendor;
	}

	private static VendorContact contact(Vendor vendor, Long id, String name) {
		VendorContact contact = new VendorContact();
		contact.setId(id);
		contact.setVendor(vendor);
		contact.setName(name);
		return contact;
	}

}
