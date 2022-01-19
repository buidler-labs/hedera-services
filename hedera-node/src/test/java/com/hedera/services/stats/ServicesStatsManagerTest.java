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
import com.hedera.services.utils.SleepingPause;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;


@ExtendWith(MockitoExtension.class)
class ServicesStatsManagerTest {
	private final long updateIntervalMs = 1_234;

	@Mock
	private Pause pause;
	@Mock
	private Platform platform;
	@Mock
	private Function<Runnable, Thread> threads;
	@Mock
	private HapiOpCounters counters;
	@Mock
	private MiscRunningAvgs runningAvgs;
	@Mock
	private MiscSpeedometers miscSpeedometers;
	@Mock
	private HapiOpSpeedometers speedometers;
	@Mock
	private NodeLocalProperties properties;
	@Mock
	private VirtualMap<ContractKey, ContractValue> storage;
	@Mock
	private VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecode;

	ServicesStatsManager subject;

	@BeforeEach
	void setup() {
		ServicesStatsManager.loopFactory = threads;
		ServicesStatsManager.pause = pause;

		given(platform.getSelfId()).willReturn(new NodeId(false, 123L));
		given(properties.statsHapiOpsSpeedometerUpdateIntervalMs()).willReturn(updateIntervalMs);

		subject = new ServicesStatsManager(
				counters, runningAvgs, miscSpeedometers, speedometers,
				properties,
				() -> storage, () -> bytecode);
	}


	@AfterEach
	public void cleanup() throws Exception {
		ServicesStatsManager.pause = SleepingPause.SLEEPING_PAUSE;
		ServicesStatsManager.loopFactory = runnable -> new Thread(() -> {
			while (true) {
				runnable.run();
			}
		});
	}

	@Test
	void initsAsExpected() {
		// setup:
		Thread thread = mock(Thread.class);
		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

		given(pause.forMs(anyLong())).willReturn(true);
		given(threads.apply(captor.capture())).willReturn(thread);

		// when:
		subject.initializeFor(platform);

		// then:
		verify(counters).registerWith(platform);
		verify(speedometers).registerWith(platform);
		verify(miscSpeedometers).registerWith(platform);
		verify(runningAvgs).registerWith(platform);
		verify(platform).appStatInit();
		// and:
		verify(thread).start();
		verify(thread).setName(String.format(ServicesStatsManager.SPEEDOMETER_UPDATE_THREAD_NAME_TPL, 123L));
		// and when:
		captor.getValue().run();
		// then:
		verify(pause).forMs(updateIntervalMs);
		verify(speedometers).updateAll();
		verify(storage).registerStatistics(any());
		verify(bytecode).registerStatistics(any());
	}
}
