package com.hedera.services.stats;

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

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.Pause;
import com.swirlds.common.Platform;
import com.swirlds.virtualmap.VirtualMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.utils.SleepingPause.SLEEPING_PAUSE;

@Singleton
public class ServicesStatsManager {
	static Pause pause = SLEEPING_PAUSE;
	static Function<Runnable, Thread> loopFactory = loop -> new Thread(() -> {
		while (true) {
			loop.run();
		}
	});

	static final String SPEEDOMETER_UPDATE_THREAD_NAME_TPL = "SpeedometerUpdateThread%d";

	private final HapiOpCounters opCounters;
	private final MiscRunningAvgs runningAvgs;
	private final MiscSpeedometers speedometers;
	private final HapiOpSpeedometers opSpeedometers;
	private final NodeLocalProperties properties;
	private final Supplier<VirtualMap<ContractKey, ContractValue>> storage;
	private final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode;

	@Inject
	public ServicesStatsManager(
			final HapiOpCounters opCounters,
			final MiscRunningAvgs runningAvgs,
			final MiscSpeedometers speedometers,
			final HapiOpSpeedometers opSpeedometers,
			final NodeLocalProperties properties,
			final Supplier<VirtualMap<ContractKey, ContractValue>> storage,
			final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode
	) {
		this.storage = storage;
		this.bytecode = bytecode;
		this.properties = properties;
		this.opCounters = opCounters;
		this.runningAvgs = runningAvgs;
		this.speedometers = speedometers;
		this.opSpeedometers = opSpeedometers;
	}

	public void initializeFor(final Platform platform) {
		opCounters.registerWith(platform);
		runningAvgs.registerWith(platform);
		speedometers.registerWith(platform);
		opSpeedometers.registerWith(platform);
		storage.get().registerStatistics(platform::addAppStatEntry);
		bytecode.get().registerStatistics(platform::addAppStatEntry);

		platform.appStatInit();

		var updateThread = loopFactory.apply(() -> {
			pause.forMs(properties.statsHapiOpsSpeedometerUpdateIntervalMs());
			opSpeedometers.updateAll();
		});
		updateThread.setName(String.format(SPEEDOMETER_UPDATE_THREAD_NAME_TPL, platform.getSelfId().getId()));
		updateThread.start();
	}
}
