package com.hedera.services.legacy.core.jproto;

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

import com.swirlds.common.CommonUtils;

public class JECDSA_384Key extends JKey {
	private byte[] ecdsa384Key;

	public JECDSA_384Key(byte[] ecdsa384Key) {
		this.ecdsa384Key = ecdsa384Key;
	}

	@Override
	public String toString() {
		return "<JECDSA_384Key: ECDSA_384Key hex=" + CommonUtils.hex(ecdsa384Key) + ">";
	}

	@Override
	public boolean isEmpty() {
		return ((null == ecdsa384Key) || (0 == ecdsa384Key.length));
	}

	public byte[] getECDSA384() {
		return ecdsa384Key;
	}

	@Override
	public boolean isValid() {
		return !isEmpty();
	}
}
