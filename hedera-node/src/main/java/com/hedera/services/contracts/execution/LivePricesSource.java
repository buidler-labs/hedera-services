package com.hedera.services.contracts.execution;

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

import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.ToLongFunction;

import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;

@Singleton
public class LivePricesSource {
	private final HbarCentExchange exchange;
	private final UsagePricesProvider usagePrices;
	private final FeeMultiplierSource feeMultiplierSource;

	@Inject
	public LivePricesSource(
			final HbarCentExchange exchange,
			final UsagePricesProvider usagePrices,
			final FeeMultiplierSource feeMultiplierSource
	) {
		this.exchange = exchange;
		this.usagePrices = usagePrices;
		this.feeMultiplierSource = feeMultiplierSource;
	}

	public long currentGasPrice(final Instant now, final HederaFunctionality function) {
		return currentPrice(now, function, FeeComponents::getGas);
	}

	public long currentStorageByteHoursPrice(final Instant now, final HederaFunctionality function) {
		return currentPrice(now, function, FeeComponents::getSbh);
	}

	private long currentPrice(
			final Instant now,
			final HederaFunctionality function,
			final ToLongFunction<FeeComponents> resourcePriceFn
	) {
		final var timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
		final var prices = usagePrices.defaultPricesGiven(function, timestamp);

		/* Fee schedule prices are set in thousandths of a tinycent */
		long feeInTinyCents = resourcePriceFn.applyAsLong(prices.getServicedata()) / 1000;
		long feeInTinyBars = getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
		final var unscaledPrice = Math.max(1L, feeInTinyBars);

		final var maxMultiplier = Long.MAX_VALUE / feeInTinyBars;
		final var curMultiplier = feeMultiplierSource.currentMultiplier();
		if (curMultiplier > maxMultiplier) {
			return Long.MAX_VALUE;
		} else {
			return unscaledPrice * curMultiplier;
		}
	}
}
