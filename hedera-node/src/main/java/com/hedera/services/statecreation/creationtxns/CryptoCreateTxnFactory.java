package com.hedera.services.statecreation.creationtxns;

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

import com.hedera.services.statecreation.creationtxns.utils.KeyFactory;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.OptionalLong;
import java.util.Optional;


public class CryptoCreateTxnFactory extends CreateTxnFactory<CryptoCreateTxnFactory> {
	public static final Duration DEFAULT_AUTO_RENEW_PERIOD = Duration.newBuilder().setSeconds(90 * 86_400L).build();

	private Optional<Boolean> receiverSigRequired = Optional.empty();
	private Duration autoRenewPeriod = DEFAULT_AUTO_RENEW_PERIOD;
	private OptionalLong balance = OptionalLong.empty();
	private Optional<String> memo = Optional.of("Default memo");

	private static Key newAccountKey = KeyFactory.getKey();

	private CryptoCreateTxnFactory() {}
	public static CryptoCreateTxnFactory newSignedCryptoCreate() {
		return new CryptoCreateTxnFactory();
	}

	@Override
	protected CryptoCreateTxnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {

		CryptoCreateTransactionBody.Builder op = CryptoCreateTransactionBody.newBuilder()
				.setKey(newAccountKey)
				.setAutoRenewPeriod(autoRenewPeriod)
				.setMemo(memo.get())
				.setReceiverSigRequired(receiverSigRequired.get());

		balance.ifPresent(op::setInitialBalance);
		txn.setCryptoCreateAccount(op);
	}

	public CryptoCreateTxnFactory balance(long amount) {
		this.balance = OptionalLong.of(amount);
		return this;
	}

	public CryptoCreateTxnFactory receiverSigRequired(boolean isRequired) {
		this.receiverSigRequired = Optional.of(isRequired);
		return this;
	}

	public CryptoCreateTxnFactory memo(String memo) {
		this.memo = Optional.of(memo);
		return this;
	}
}