package com.teamops.vendor.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.teamops.common.exception.ApiException;
import com.teamops.recyclebin.service.RecycleBinHandler;
import com.teamops.vendor.entity.VendorContract;
import com.teamops.vendor.repository.VendorRepository;

/**
 * Vendor records in the recycle bin. A vendor keeps its contacts (they go with its permanent delete through the
 * foreign key cascade). A contract is named after its vendor, comes back only while its vendor is live, and keeps its
 * documents until it is purged, when the stored files are removed too.
 */
@Configuration
class VendorRecycleHandlers {

	@Bean
	RecycleBinHandler<VendorContract> vendorContractRecycleHandler(VendorRepository vendors,
			ContractDocumentService documents) {
		return new RecycleBinHandler<>() {

			@Override
			public Class<VendorContract> type() {
				return VendorContract.class;
			}

			@Override
			public String label(VendorContract contract) {
				return contract.getVendor().getName() + " – " + contract.getTitle();
			}

			@Override
			public void checkRestore(VendorContract contract) {
				if (!vendors.existsById(contract.getVendor().getId())) {
					throw ApiException.conflict("RESTORE_CONFLICT",
							"This contract's vendor is in the recycle bin. Restore the vendor first.");
				}
			}

			@Override
			public void beforePurge(VendorContract contract) {
				documents.removeAll(contract.getId());
			}

		};
	}

}
