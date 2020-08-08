package com.hedera.services.security.ops;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.security.ops.SystemOpAuthorization.IMPERMISSIBLE;
import static com.hedera.services.security.ops.SystemOpAuthorization.UNNECESSARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(JUnitPlatform.class)
class SystemOpPoliciesTest {
	SystemOpPolicies subject = new SystemOpPolicies(new MockEntityNumbers());

	@Test
	public void genesisCanUpdateEverything() {
		// expect:
		assertTrue(subject.canUpdate(2, 1));
		assertTrue(subject.canUpdate(2, 2));
		assertTrue(subject.canUpdate(2, 1001));
	}

	@Test
	public void sysAdminCanUpdateOnlyManagedAccounts() {
		// expect:
		assertFalse(subject.canUpdate(50, 50));
		assertFalse(subject.canUpdate(50, 81));
		assertTrue(subject.canUpdate(50, 51));
		assertTrue(subject.canUpdate(50, 80));
	}

	@Test
	public void sysAdminCanUpdateKnownSystemFiles() {
		// expect:
		assertTrue(subject.canUpdate(50, 101));
		assertTrue(subject.canUpdate(50, 102));
		assertTrue(subject.canUpdate(50, 111));
		assertTrue(subject.canUpdate(50, 112));
		assertTrue(subject.canUpdate(50, 121));
		assertTrue(subject.canUpdate(50, 122));
	}

	@Test
	public void specialAdminsCanUpdateThemselves() {
		// expect:
		assertTrue(subject.canUpdate(55, 55));
		assertTrue(subject.canUpdate(56, 56));
		assertTrue(subject.canUpdate(57, 57));
		assertTrue(subject.canUpdate(58, 58));
		assertTrue(subject.canUpdate(59, 59));
		assertTrue(subject.canUpdate(60, 60));
	}

	@Test
	public void addressBookAdminCanUpdateExpected() {
		// expect:
		assertTrue(subject.canUpdate(55, 101));
		assertTrue(subject.canUpdate(55, 102));
		assertTrue(subject.canUpdate(55, 121));
		assertTrue(subject.canUpdate(55, 122));
		assertFalse(subject.canUpdate(55, 111));
	}

	@Test
	public void unspecializedSystemAccountCanUpdateOnlyItself() {
		// expect:
		for (long i = 1; i <= 1_000; i++) {
			assertEquals((i == 58), subject.canUpdate(58, i));
		}
	}

	@Test
	public void feeSchedulesAdminCanUpdateExpected() {
		// expect:
		assertTrue(subject.canUpdate(56, 111));
		assertFalse(subject.canUpdate(56, 112));
		assertFalse(subject.canUpdate(56, 121));
	}

	@Test
	public void exchangeRatesAdminCanUpdateExpected() {
		// expect:
		assertTrue(subject.canUpdate(57, 112));
		assertTrue(subject.canUpdate(57, 121));
		assertTrue(subject.canUpdate(57, 122));
		assertFalse(subject.canUpdate(57, 111));
	}

	@Test
	public void systemFilesCannotBeDeleted() throws InvalidProtocolBufferException {
		// given:
		var txn = superuserTxn()
				.setFileDelete(FileDeleteTransactionBody
						.newBuilder()
						.setFileID(file(100)));

		// expect:
		assertEquals(subject.check(accessor(txn)), IMPERMISSIBLE);
	}

	@Test
	public void civilianFilesAreDeletable() throws InvalidProtocolBufferException {
		// given:
		var txn = superuserTxn()
				.setFileDelete(FileDeleteTransactionBody
						.newBuilder()
						.setFileID(file(1001)));

		// expect:
		assertEquals(subject.check(accessor(txn)), UNNECESSARY);
	}

	@Test
	public void systemContractsCannotBeDeleted() throws InvalidProtocolBufferException {
		// given:
		var txn = superuserTxn()
				.setContractDeleteInstance(ContractDeleteTransactionBody
						.newBuilder()
						.setContractID(contract(100)));

		// expect:
		assertEquals(subject.check(accessor(txn)), IMPERMISSIBLE);
	}

	@Test
	public void civilianContractsAreDeletable() throws InvalidProtocolBufferException {
		// given:
		var txn = superuserTxn()
				.setContractDeleteInstance(ContractDeleteTransactionBody
						.newBuilder()
						.setContractID(contract(1001)));

		// expect:
		assertEquals(subject.check(accessor(txn)), UNNECESSARY);
	}

	@Test
	public void systemAccountsCannotBeDeleted() throws InvalidProtocolBufferException {
		// given:
		var txn = superuserTxn()
				.setCryptoDelete(CryptoDeleteTransactionBody
						.newBuilder()
						.setDeleteAccountID(account(100)));

		// expect:
		assertEquals(subject.check(accessor(txn)), IMPERMISSIBLE);
	}

	@Test
	public void civilianAccountsAreDeletable() throws InvalidProtocolBufferException {
		// given:
		var txn = civilianTxn()
				.setCryptoDelete(CryptoDeleteTransactionBody
						.newBuilder()
						.setDeleteAccountID(account(1001)));

		// expect:
		assertEquals(subject.check(accessor(txn)), UNNECESSARY);
	}

	private SignedTxnAccessor accessor(TransactionBody.Builder txn) throws InvalidProtocolBufferException {
		return new SignedTxnAccessor(Transaction.newBuilder().setBody(txn).build());
	}

	private TransactionBody.Builder civilianTxn() {
		return txnWithPayer(75231);
	}

	private TransactionBody.Builder superuserTxn() {
		return txnWithPayer(2);
	}

	private TransactionBody.Builder txnWithPayer(long num) {
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setAccountID(account(2)));

	}

	private ContractID contract(long num) {
		return IdUtils.asContract(String.format("0.0.%d", num));
	}

	private FileID file(long num) {
		return IdUtils.asFile(String.format("0.0.%d", num));
	}

	private AccountID account(long num) {
		return IdUtils.asAccount(String.format("0.0.%d", num));
	}
}