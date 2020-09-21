package com.hedera.services.bdd.suites.token;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenTransact;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenTransact.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TokenTransactSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenTransactSpecs.class);

	private static final long FLOAT = 1_000;
	private static final String A_TOKEN = "TokenA";
	private static final String B_TOKEN = "TokenB";
	private static final String FIRST_USER = "Client1";
	private static final String SECOND_USER = "Client2";
	private static final String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new TokenTransactSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						balancesChangeOnTokenTransfer(),
						accountsMustBeExplicitlyUnfrozenOnlyIfDefaultFreezeIsTrue(),
						senderSigsAreValid(),
						balancesAreChecked(),
						nonZeroNetTransfersRejected(),
						allRequiredSigsAreChecked(),
						txnsAreAtomic(),
				}
		);
	}

	public HapiApiSpec balancesAreChecked() {
		return defaultHapiSpec("BalancesAreChecked")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate("firstTreasury"),
						cryptoCreate("secondTreasury"),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialFloat(100)
								.treasury("firstTreasury")
				).then(
						tokenTransact(
								moving(100_000_000_000_000L, A_TOKEN)
										.between("firstTreasury", "beneficiary")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury")
								.hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE)
				);
	}

	public HapiApiSpec accountsMustBeExplicitlyUnfrozenOnlyIfDefaultFreezeIsTrue() {
		return defaultHapiSpec("AccountsMustBeExplicitlyUnfrozenOnlyIfDefaultFreezeIsTrue")
				.given(
						cryptoCreate("randomBeneficiary"),
						cryptoCreate("treasury"),
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						newKeyNamed("freezeKey")
				).when(
						tokenCreate(A_TOKEN)
								.treasury("treasury")
								.freezeKey("freezeKey")
								.freezeDefault(true),
						tokenAssociate("randomBeneficiary", A_TOKEN),
						tokenTransact(
								moving(100, A_TOKEN)
										.between("treasury", "randomBeneficiary")
						).hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
						/* and */
						tokenCreate(B_TOKEN)
								.treasury("treasury")
								.freezeDefault(false),
						tokenAssociate("randomBeneficiary", B_TOKEN),
						tokenTransact(
								moving(100, B_TOKEN)
										.between("treasury", "randomBeneficiary")
						).payingWith("payer").via("successfulTransfer")
				).then(
						getAccountBalance("randomBeneficiary")
								.logged()
								.hasTokenBalance(B_TOKEN, 100),
						getTxnRecord("successfulTransfer").logged()
				);
	}

	public HapiApiSpec allRequiredSigsAreChecked() {
		return defaultHapiSpec("SenderSigsAreChecked")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate("firstTreasury"),
						cryptoCreate("secondTreasury"),
						cryptoCreate("beneficiary").receiverSigRequired(true)
				).when(
						tokenCreate(A_TOKEN)
								.initialFloat(123)
								.treasury("firstTreasury"),
						tokenCreate(B_TOKEN)
								.initialFloat(234)
								.treasury("secondTreasury"),
						tokenAssociate("beneficiary", A_TOKEN, B_TOKEN)
				).then(
						tokenTransact(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury", "beneficiary")
								.hasKnownStatus(INVALID_SIGNATURE),
						tokenTransact(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury", "secondTreasury")
								.hasKnownStatus(INVALID_SIGNATURE),
						tokenTransact(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary")
						).payingWith("payer")
								.hasKnownStatus(SUCCESS)
				);
	}

	public HapiApiSpec senderSigsAreValid() {
		return defaultHapiSpec("SenderSigsAreValid")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate("firstTreasury"),
						cryptoCreate("secondTreasury"),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialFloat(123)
								.treasury("firstTreasury"),
						tokenCreate(B_TOKEN)
								.initialFloat(234)
								.treasury("secondTreasury"),
						tokenAssociate("beneficiary", A_TOKEN, B_TOKEN)
				).then(
						tokenTransact(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary")
						).payingWith("payer")
								.signedBy("firstTreasury", "payer")
								.hasKnownStatus(SUCCESS)
				);
	}

	public HapiApiSpec txnsAreAtomic() {
		return defaultHapiSpec("TxnsAreAtomic")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate("firstTreasury"),
						cryptoCreate("secondTreasury"),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialFloat(123)
								.treasury("firstTreasury"),
						tokenCreate(B_TOKEN)
								.initialFloat(50)
								.treasury("secondTreasury"),
						tokenAssociate("beneficiary", A_TOKEN, B_TOKEN),
						tokenTransact(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary")
						).hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE)
				).then(
						getAccountBalance("firstTreasury")
								.logged()
								.hasTokenBalance(A_TOKEN, 123),
						getAccountBalance("secondTreasury")
								.logged()
								.hasTokenBalance(B_TOKEN, 50),
						getAccountBalance("beneficiary").logged()
				);
	}

	public HapiApiSpec nonZeroNetTransfersRejected() {
		return defaultHapiSpec("NonZeroNetTransfersRejected")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate("firstTreasury"),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialFloat(100)
								.treasury("firstTreasury"),
						tokenAssociate("beneficiary", A_TOKEN)
				).then(
						tokenTransact(
								moving(1, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(1, A_TOKEN).from("firstTreasury")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury")
								.hasKnownStatus(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN)
				);
	}


	public HapiApiSpec balancesChangeOnTokenTransfer() {
		return defaultHapiSpec("BalancesChangeOnTokenTransfer")
				.given(
						cryptoCreate(FIRST_USER),
						cryptoCreate(SECOND_USER),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.initialFloat(FLOAT)
								.treasury(TOKEN_TREASURY),
						tokenCreate(B_TOKEN)
								.initialFloat(FLOAT)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(FIRST_USER, A_TOKEN),
						tokenAssociate(SECOND_USER, B_TOKEN)
				).when(
						tokenTransact(
								moving(100, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER),
								moving(100, B_TOKEN).between(TOKEN_TREASURY, SECOND_USER)
						)
				).then(
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(A_TOKEN, FLOAT - 100)
								.hasTokenBalance(B_TOKEN, FLOAT - 100),
						getAccountBalance(FIRST_USER)
								.hasTokenBalance(A_TOKEN, 100),
						getAccountBalance(SECOND_USER)
								.hasTokenBalance(B_TOKEN, 100)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
