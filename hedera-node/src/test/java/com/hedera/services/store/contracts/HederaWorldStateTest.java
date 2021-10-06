package com.hedera.services.store.contracts;

/*
 *   -
 *   ‌
 *   Hedera Services Node
 *   ​
 *   Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 *   ​
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *   ‍
 *
 *
 */

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.ethereum.core.AccountState;
import org.ethereum.db.ContractDetails;
import org.ethereum.db.ServicesRepositoryRoot;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HederaWorldStateTest {
	@Mock
	private EntityIdSource ids;
	@Mock
	private HederaLedger ledger;
	@Mock
	private ServicesRepositoryRoot repositoryRoot;

	private HederaWorldState subject;

	@BeforeEach
	void setUp() {
		subject = new HederaWorldState(ids, ledger, repositoryRoot);
	}

	@Test
	void persist() {
		/* default empty persist */
		var persistResult = subject.persist();
		assertEquals(0, persistResult.size());
	}

	@Test
	void customizeSponsoredAccounts() {
		/* happy path with 0 existing accounts */
		given(ledger.exists(any())).willReturn(true);
		subject.customizeSponsoredAccounts();
		verify(ledger, never()).customize(any(), any()); // will do 0 iterations

		/* happy path with 1 existing account */
		final var merkleAcc = mock(MerkleAccount.class);
		given(ledger.get(any())).willReturn(merkleAcc);
		given(merkleAcc.getMemo()).willReturn("memo");
		given(merkleAcc.getProxy()).willReturn(EntityId.MISSING_ENTITY_ID);
		given(merkleAcc.getAutoRenewSecs()).willReturn(100L);
		var updater = subject.updater();
		updater.getSponsorMap().put(Address.RIPEMD160, Address.RIPEMD160);
		updater.commit();
		subject.customizeSponsoredAccounts();
		verify(ledger).customize(any(), any());

		/* sad path with existing but not accessible account */
		updater.getSponsorMap().put(Address.RIPEMD160, Address.RIPEMD160);
		updater.commit();
		given(ledger.exists(any())).willReturn(false);
		assertFailsWith(() -> subject.customizeSponsoredAccounts(), ResponseCodeEnum.FAIL_INVALID);
	}

	@Test
	void newContractAddress() {
		final var sponsor = mock(Address.class);
		given(sponsor.toArrayUnsafe())
				.willReturn(new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1});
		given(ids.newContractId(any())).willReturn(ContractID.newBuilder().setContractNum(1).build());
		var addr = subject.newContractAddress(sponsor);
		assertNotEquals(addr, sponsor);
		assertEquals(1,
				accountParsedFromSolidityAddress(addr.toArrayUnsafe()).getAccountNum());
	}

	@Test
	void reclaimContractId() {
		subject.reclaimContractId();
		verify(ids).reclaimLastId();
	}

	@Test
	void updater() {
		var updater = subject.updater();
		assertNotNull(updater);
		assertTrue(updater instanceof HederaWorldState.Updater);
	}

	@Test
	void rootHash() {
		assertEquals(Hash.EMPTY, subject.rootHash());
		assertEquals(Hash.EMPTY, subject.frontierRootHash());
	}

	@Test
	void streamAccounts() {
		assertThrows(
				UnsupportedOperationException.class,
				() -> subject.streamAccounts(null, 10)
		);
	}

	@Test
	void get() {
		final var accState = mock(AccountState.class);
		given(accState.getProxyAccountRealm()).willReturn(0L);
		given(accState.getProxyAccountShard()).willReturn(0L);
		given(accState.getProxyAccountNum()).willReturn(1L);
		given(accState.getBalance()).willReturn(BigInteger.TEN);
		given(accState.getAutoRenewPeriod()).willReturn(100L);
		given(repositoryRoot.isExist(any())).willReturn(true);
		given(repositoryRoot.isDeleted(any())).willReturn(false);
		given(repositoryRoot.getAccountState(Address.RIPEMD160.toArray())).willReturn(accState);

		final var acc = subject.get(Address.RIPEMD160);
		assertNotNull(acc);
		assertEquals(Wei.of(10), acc.getBalance());
		assertEquals(1, acc.getProxyAccount().num());
		assertEquals(100L, acc.getAutoRenew());

		objectContractWorks(acc);

		/* non-existent accounts should resolve to null */
		given(repositoryRoot.isExist(any())).willReturn(false);
		var nonExistent = subject.get(Address.RIPEMD160);
		assertNull(nonExistent);
	}

	/* 
		Object contract of HederaWorldState.WorldStateAccount tests 
		Please note that the said class **cannot** be instantiated, thus - the test fragment is here	
	*/
	private void objectContractWorks(HederaWorldState.WorldStateAccount acc) {
		assertNotNull(acc.getAddress());
		assertNotNull(acc.getAddressHash());
		assertFalse(acc.hasCode());
		assertEquals(Bytes.EMPTY, acc.getCode());
		assertEquals(Hash.EMPTY, acc.getCodeHash());
		assertEquals(0, acc.getNonce());

		acc.setProxyAccount(EntityId.MISSING_ENTITY_ID);
		assertEquals(EntityId.MISSING_ENTITY_ID, acc.getProxyAccount());
		acc.setMemo("otherMemo");
		assertEquals("otherMemo", acc.getMemo());
		acc.setAutoRenew(10L);
		assertEquals(10L, acc.getAutoRenew());
		acc.setExpiry(10L);
		assertEquals(10L, acc.getExpiry());
		var k = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
		acc.setKey(k);
		assertEquals(k, acc.getKey());
		final var stringified = "AccountState" + "{" +
				"address=" + Address.RIPEMD160 + ", " +
				"nonce=" + 0 + ", " +
				"balance=" + Wei.of(10) + ", " +
				"codeHash=" + Hash.EMPTY + ", " +
				"}";
		assertEquals(stringified, acc.toString());

		var contractDetails = mock(ContractDetails.class);
		given(contractDetails.get(any())).willReturn(null);
		given(repositoryRoot.getContractDetails(any())).willReturn(contractDetails);
		assertEquals(
				Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
				acc.getOriginalStorageValue(UInt256.ONE)
		);
		assertThrows(UnsupportedOperationException.class,
				() -> acc.storageEntriesFrom(null, 10));

	}

	@Test
	void staticInnerUpdaterWorksAsExpected() {
		/* Please note that the subject of this test is the actual inner updater class */
		var actualSubject = subject.updater();
		assertNotNull(actualSubject.updater());
		assertEquals(0, actualSubject.getTouchedAccounts().size());

		/* delete branch */
		var mockedZeroAcc = mock(Address.class);
		given(repositoryRoot.getBalance(any())).willReturn(BigInteger.TEN);
		actualSubject.sponsorMap.put(Address.ZERO, mockedZeroAcc);
		actualSubject.deleteAccount(Address.ZERO);
		actualSubject.commit();
		verify(repositoryRoot).setDeleted(any(), anyBoolean());

		actualSubject.sponsorMap.put(Address.ZERO, mockedZeroAcc);
		actualSubject.revert();
		assertEquals(0, actualSubject.sponsorMap.size());
	}
}