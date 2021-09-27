package com.hedera.services.bdd.suites.contract.operations;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.math.BigInteger;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;

public class GlobalPropertiesSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(GlobalPropertiesSuite.class);

	public static void main(String... args) {
		new GlobalPropertiesSuite().runSuiteAsync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				chainIdWorks(),
				baseFeeWorks(),
				coinbaseWorks(),
				gasLimitWorks()
		);
	}

	private HapiApiSpec chainIdWorks() {
		return defaultHapiSpec("chainIdWorks")
				.given(
						fileCreate("globalProps").path(ContractResources.GLOBAL_PROPERTIES),
						contractCreate("globalPropsContract").bytecode("globalProps")
				).when(
						contractCall("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_CHAIN_ID_ABI).via(
								"chainId")
				).then(
						getTxnRecord("chainId").logged().hasPriority(
								recordWith().contractCallResult(
										resultWith().resultThruAbi(
												ContractResources.GLOBAL_PROPERTIES_CHAIN_ID_ABI,
												isLiteralResult(
														new Object[]{BigInteger.valueOf(293)}
												)
										)
								)
						)
				);
	}

	private HapiApiSpec baseFeeWorks() {
		return defaultHapiSpec("baseFeeWorks")
				.given(
						fileCreate("globalProps").path(ContractResources.GLOBAL_PROPERTIES),
						contractCreate("globalPropsContract").bytecode("globalProps")
				).when(
						contractCall("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_BASE_FEE_ABI).via(
								"baseFee")
				).then(
						getTxnRecord("baseFee").logged().hasPriority(
								recordWith().contractCallResult(
										resultWith().resultThruAbi(
												ContractResources.GLOBAL_PROPERTIES_BASE_FEE_ABI,
												isLiteralResult(
														new Object[]{BigInteger.valueOf(0)}
												)
										)
								)
						)
				);
	}

	private HapiApiSpec coinbaseWorks() {
		return defaultHapiSpec("coinbaseWorks")
				.given(
						fileCreate("globalProps").path(ContractResources.GLOBAL_PROPERTIES),
						contractCreate("globalPropsContract").bytecode("globalProps")
				).when(
						contractCall("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_COINBASE_ABI)
								.via("coinbase")
				).then(
						withOpContext((spec, opLog) -> {
							var record = getTxnRecord("coinbase");
							allRunFor(spec, record);
							final var result = record.getResponseRecord().getContractCallResult();
							Assertions.assertEquals(result.getContractCallResult(),
									parsedToByteString(DEFAULT_PROPS.fundingAccount().getAccountNum()));
						})
				);
	}

	private HapiApiSpec gasLimitWorks() {
		final var gasLimit = Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("contracts.maxGas"));
		return defaultHapiSpec("gasLimitWorks")
				.given(
						fileCreate("globalProps").path(ContractResources.GLOBAL_PROPERTIES),
						contractCreate("globalPropsContract").bytecode("globalProps")
				).when(
						contractCall("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_GASLIMIT_ABI).via(
								"gasLimit").gas(gasLimit)
				).then(
						getTxnRecord("gasLimit").logged().hasPriority(
								recordWith().contractCallResult(
										resultWith().resultThruAbi(
												ContractResources.GLOBAL_PROPERTIES_GASLIMIT_ABI,
												isLiteralResult(
														new Object[]{BigInteger.valueOf(gasLimit)}
												)
										)
								)
						)
				);
	}
}