package com.hedera.services.sigs;

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

import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hedera.services.sigs.order.SigningOrderResult.noKnownKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpansionTest {
	@Mock
	private PubKeyToSigBytes pkToSigFn;
	@Mock
	private SigRequirements keyOrderer;
	@Mock
	private PlatformTxnAccessor txnAccessor;
	@Mock
	private TxnScopedPlatformSigFactory sigFactory;
	@Mock
	private SwirldTransaction swirldTransaction;
	@Mock
	private TransactionSignature signature;

	private Expansion subject;

	@BeforeEach
	void setUp() {
		subject = new Expansion(txnAccessor, keyOrderer, pkToSigFn, sigFactory);
	}

	@Test
	void skipsUnusedFullKeySigsIfNotPresent() {
		setupDegenerateMocks();

		final var result = subject.execute();

		assertEquals(OK, result);
		verify(pkToSigFn, never()).forEachUnusedSigWithFullPrefix(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void appendsUnusedFullKeySignaturesToList() {
		final var pretendFullKey = "COMPLETE".getBytes(StandardCharsets.UTF_8);
		final var pretendFullSig = "NONSENSE".getBytes(StandardCharsets.UTF_8);

		given(sigFactory.create(pretendFullKey, pretendFullSig)).willReturn(signature);
		setupDegenerateMocks();
		given(pkToSigFn.hasAtLeastOneUnusedSigWithFullPrefix()).willReturn(true);
		willAnswer(inv -> {
			final var obs = (BiConsumer<byte[], byte[]>) inv.getArgument(0);
			obs.accept(pretendFullKey, pretendFullSig);
			return null;
		}).given(pkToSigFn).forEachUnusedSigWithFullPrefix(any());

		final var result = subject.execute();

		assertEquals(OK, result);
		verify(swirldTransaction).add(signature);
	}

	private void setupDegenerateMocks() {
		final var degenTxnBody = TransactionBody.getDefaultInstance();
		given(txnAccessor.getTxn()).willReturn(degenTxnBody);
		given(keyOrderer.keysForPayer(degenTxnBody, CODE_ORDER_RESULT_FACTORY)).willReturn(noKnownKeys());
		given(keyOrderer.keysForOtherParties(degenTxnBody, CODE_ORDER_RESULT_FACTORY)).willReturn(noKnownKeys());
		given(txnAccessor.getPlatformTxn()).willReturn(swirldTransaction);
	}
}
