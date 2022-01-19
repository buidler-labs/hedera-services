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
import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MintLogicTest {
	private final long amount = 123L;
	private final TokenID grpcId = IdUtils.asToken("1.2.3");
	private final Id id = new Id(1, 2, 3);
	private final Id treasuryId = new Id(2, 4, 6);
	private final Account treasury = new Account(treasuryId);

	@Mock
	private Token token;
	@Mock
	private TypedTokenStore store;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private OptionValidator validator;
	@Mock
	private AccountStore accountStore;

	private TokenRelationship treasuryRel;
	private TransactionBody tokenMintTxn;

	private MintLogic subject;

	@BeforeEach
	private void setup() {
		subject = new MintLogic(validator, store, accountStore);
	}

	@Test
	void validatesMintCap() {
		// setup:
		final long curTotal = 100L;
		final long unacceptableTotal = 101L;

		givenValidUniqueTxnCtx();
		given(accessor.getTxn()).willReturn(tokenMintTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
		given(store.currentMintedNfts()).willReturn(curTotal);
		given(token.getId()).willReturn(id);
		given(store.loadToken(id)).willReturn(token);
		given(validator.isPermissibleTotalNfts(unacceptableTotal)).willReturn(false);

		// expect:
		assertFailsWith(() -> subject.mint(
						token.getId(),
						txnCtx.accessor().getTxn().getTokenMint().getMetadataCount(),
						txnCtx.accessor().getTxn().getTokenMint().getAmount(),
						txnCtx.accessor().getTxn().getTokenMint().getMetadataList(),
						Instant.now()),
				MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED);
	}

	@Test
	void followsHappyPath() {
		// setup:
		treasuryRel = new TokenRelationship(token, treasury);

		givenValidTxnCtx();
		given(accessor.getTxn()).willReturn(tokenMintTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(store.loadToken(id)).willReturn(token);
		given(token.getTreasury()).willReturn(treasury);
		given(store.loadTokenRelationship(token, treasury)).willReturn(treasuryRel);
		given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
		given(token.getId()).willReturn(id);

		// when:
		subject.mint(token.getId(),
				txnCtx.accessor().getTxn().getTokenMint().getMetadataCount(),
				txnCtx.accessor().getTxn().getTokenMint().getAmount(),
				txnCtx.accessor().getTxn().getTokenMint().getMetadataList(),
				Instant.now());

		// then:
		verify(token).mint(treasuryRel, amount, false);
		verify(store).commitToken(token);
		verify(store).commitTokenRelationships(List.of(treasuryRel));
	}

	@Test
	void followsUniqueHappyPath() {
		// setup:
		final long curTotal = 99L;
		final long acceptableTotal = 100L;
		treasuryRel = new TokenRelationship(token, treasury);

		givenValidUniqueTxnCtx();
		given(accessor.getTxn()).willReturn(tokenMintTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(token.getTreasury()).willReturn(treasury);
		given(store.loadToken(id)).willReturn(token);
		given(token.getId()).willReturn(id);
		given(store.loadTokenRelationship(token, treasury)).willReturn(treasuryRel);
		given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
		given(store.currentMintedNfts()).willReturn(curTotal);
		given(validator.isPermissibleTotalNfts(acceptableTotal)).willReturn(true);
		// when:
		subject.mint(token.getId(),
				txnCtx.accessor().getTxn().getTokenMint().getMetadataCount(),
				txnCtx.accessor().getTxn().getTokenMint().getAmount(),
				txnCtx.accessor().getTxn().getTokenMint().getMetadataList(),
				Instant.now());

		// then:
		verify(token).mint(any(OwnershipTracker.class), eq(treasuryRel), any(List.class), any(RichInstant.class));
		verify(store).commitToken(token);
		verify(store).commitTokenRelationships(List.of(treasuryRel));
		verify(store).commitTrackers(any(OwnershipTracker.class));
		verify(accountStore).commitAccount(any(Account.class));
	}

	private void givenValidUniqueTxnCtx() {
		tokenMintTxn = TransactionBody.newBuilder()
				.setTokenMint(TokenMintTransactionBody.newBuilder()
						.setToken(grpcId)
						.addAllMetadata(List.of(ByteString.copyFromUtf8("memo"))))
				.build();
	}

	private void givenValidTxnCtx() {
		tokenMintTxn = TransactionBody.newBuilder()
				.setTokenMint(TokenMintTransactionBody.newBuilder()
						.setToken(grpcId)
						.setAmount(amount))
				.build();
	}
}
