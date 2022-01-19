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

import com.google.protobuf.ByteString;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED;

@Singleton
public class MintLogic {
	private final OptionValidator validator;
	private final TypedTokenStore tokenStore;
	private final AccountStore accountStore;

	@Inject
	public MintLogic(OptionValidator validator, TypedTokenStore tokenStore, AccountStore accountStore) {
		this.validator = validator;
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
	}

	public void mint(final Id targetId,
					 int metaDataCount,
					 long amount,
					 List<ByteString> metaDataList,
					 Instant consensusTime) {

		/* --- Load the model objects --- */
		final var token = tokenStore.loadToken(targetId);
		validateMinting(validator, token, metaDataCount, tokenStore);
		final var treasuryRel = tokenStore.loadTokenRelationship(token, token.getTreasury());

		/* --- Instantiate change trackers --- */
		final var ownershipTracker = new OwnershipTracker();

		/* --- Do the business logic --- */
		if (token.getType() == TokenType.FUNGIBLE_COMMON) {
			token.mint(treasuryRel, amount, false);
		} else {
			token.mint(ownershipTracker, treasuryRel, metaDataList, fromJava(consensusTime));
		}

		/* --- Persist the updated models --- */
		tokenStore.commitToken(token);
		tokenStore.commitTokenRelationships(List.of(treasuryRel));
		tokenStore.commitTrackers(ownershipTracker);
		accountStore.commitAccount(token.getTreasury());
	}

	private void validateMinting(OptionValidator validator,
								 Token token,
								 int metaDataCount,
								 TypedTokenStore tokenStore) {
		if (token.getType() == NON_FUNGIBLE_UNIQUE) {
			final var proposedTotal = tokenStore.currentMintedNfts() + metaDataCount;
			validateTrue(validator.isPermissibleTotalNfts(proposedTotal), MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED);
		}
	}
}
