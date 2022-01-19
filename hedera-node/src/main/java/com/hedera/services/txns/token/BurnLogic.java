package com.hedera.services.txns.token;

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

import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class BurnLogic {
	private final TypedTokenStore tokenStore;
	private final AccountStore accountStore;

	@Inject
	public BurnLogic(final TypedTokenStore tokenStore, final AccountStore accountStore) {
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
	}

	public void burn(
			final Id targetId,
			final long amount,
			final List<Long> serialNumbersList
	) {
		/* --- Load the models --- */
		final var token = tokenStore.loadToken(targetId);
		final var treasuryRel = tokenStore.loadTokenRelationship(token, token.getTreasury());
		final var ownershipTracker = new OwnershipTracker();
		/* --- Do the business logic --- */
		if (token.getType().equals(TokenType.FUNGIBLE_COMMON)) {
			token.burn(treasuryRel, amount);
		} else {
			tokenStore.loadUniqueTokens(token, serialNumbersList);
			token.burn(ownershipTracker, treasuryRel, serialNumbersList);
		}
		/* --- Persist the updated models --- */
		tokenStore.commitToken(token);
		tokenStore.commitTokenRelationships(List.of(treasuryRel));
		tokenStore.commitTrackers(ownershipTracker);
		accountStore.commitAccount(token.getTreasury());
	}
}