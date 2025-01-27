package com.hedera.services.state.expiry;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.expiry.renewal.RenewalProcess;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.SequenceNumber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Supplier;

@Singleton
public class EntityAutoRenewal {
	private static final Logger log = LogManager.getLogger(EntityAutoRenewal.class);

	private final long firstEntityToScan;
	private final RenewalProcess renewalProcess;
	private final NetworkCtxManager networkCtxManager;
	private final GlobalDynamicProperties dynamicProps;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final Supplier<SequenceNumber> seqNo;

	@Inject
	public EntityAutoRenewal(
			HederaNumbers hederaNumbers,
			RenewalProcess renewalProcess,
			GlobalDynamicProperties dynamicProps,
			NetworkCtxManager networkCtxManager,
			Supplier<MerkleNetworkContext> networkCtx,
			Supplier<SequenceNumber> seqNo
	) {
		this.seqNo = seqNo;
		this.networkCtx = networkCtx;
		this.networkCtxManager = networkCtxManager;
		this.renewalProcess = renewalProcess;
		this.dynamicProps = dynamicProps;

		this.firstEntityToScan = hederaNumbers.numReservedSystemEntities() + 1;
	}

	public void execute(Instant instantNow) {
		if (!dynamicProps.autoRenewEnabled()) {
			return;
		}

		final long wrapNum = seqNo.get().current();
		if (wrapNum == firstEntityToScan) {
			/* No non-system entities in the system, can abort */
			return;
		}

		final var curNetworkCtx = networkCtx.get();
		final int maxEntitiesToTouch = dynamicProps.autoRenewMaxNumberOfEntitiesToRenewOrDelete();
		final int maxEntitiesToScan = dynamicProps.autoRenewNumberOfEntitiesToScan();
		if (networkCtxManager.currentTxnIsFirstInConsensusSecond()) {
			curNetworkCtx.clearAutoRenewSummaryCounts();
		}

		renewalProcess.beginRenewalCycle(instantNow);

		int i = 1, entitiesTouched = 0;
		long scanNum = curNetworkCtx.lastScannedEntity();

		log.debug("Auto-renew scan beginning at {}, wrapping at {}", scanNum, wrapNum);
		for (; i <= maxEntitiesToScan; i++) {
			scanNum++;
			if (scanNum >= wrapNum) {
				scanNum = firstEntityToScan;
			}
			if (renewalProcess.process(scanNum)) {
				entitiesTouched++;
			}
			if (entitiesTouched >= maxEntitiesToTouch) {
				/* Allow consistent calculation of num scanned below. */
				i++;
				break;
			}
		}
		renewalProcess.endRenewalCycle();
		curNetworkCtx.updateAutoRenewSummaryCounts(i - 1, entitiesTouched);
		curNetworkCtx.updateLastScannedEntity(scanNum);
		log.debug("Auto-renew scan finished at {} with {}/{} scanned/touched (Total this second: {}/{})",
				scanNum, i - 1, entitiesTouched,
				curNetworkCtx.getEntitiesScannedThisSecond(), curNetworkCtx.getEntitiesTouchedThisSecond());
	}
}
