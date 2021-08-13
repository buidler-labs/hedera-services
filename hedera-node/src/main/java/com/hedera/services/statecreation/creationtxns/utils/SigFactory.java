package com.hedera.services.statecreation.creationtxns.utils;

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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.proto.utils.SignatureGenerator;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.crypto.SignatureType;

import java.security.PrivateKey;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBodyBytes;

public class SigFactory {
	private final SigMapGenerator sigMapGen;
	public static final byte[] NONSENSE_RSA_SIG = "MOME".getBytes();
	public static final byte[] NONSENSE_ECDSA_SIG = "OUTGRABE".getBytes();

	public SigFactory() {
		this(SigMapGenerator.withUniquePrefixes());
	}
	public SigFactory(SigMapGenerator sigMapGen) {
		this.sigMapGen = sigMapGen;
	}

	public Transaction signWithSimpleKey(
			Transaction.Builder txn,
			List<Key> keys,
			KeyFactory factory
	) throws Throwable {
		SimpleSigning signing = new SimpleSigning(extractTransactionBodyBytes(txn), keys, factory);
		List<Map.Entry<byte[], byte[]>> sigs = signing.simplySigned();
		txn.setSigMap(sigMapGen.generate(sigs, signing.sigTypes()));

		return txn.build();
	}

	private static class SimpleSigning {
		private final byte[] data;
		private final KeyFactory factory ;
		private final Set<String> used = new HashSet<>();
		private final List<SignatureType> sigTypes = new ArrayList<>();
		private final List<Map.Entry<byte[], byte[]>> keySigs = new ArrayList<>();

		private List<Key> keys ;

		public SimpleSigning(byte[] data,
				List<Key> keys, KeyFactory factory) {
			this.data = data;
			this.keys = keys;
			this.factory = factory;
		}

		public Supplier<SignatureType> sigTypes() {
			return new Supplier<>() {
				private int i = 0;

				@Override
				public SignatureType get() {
					return sigTypes.get(i++);
				}
			};
		}

		public List<Map.Entry<byte[], byte[]>> simplySigned() throws Throwable {
			for (Key key : keys) {
				signIfNecessary(key);
			}
			return keySigs;
		}

		private void signIfNecessary(Key key) throws Throwable {
			String pubKeyHex = KeyFactory.asPubKeyHex(key);
			if (!used.contains(pubKeyHex)) {
				signFor(pubKeyHex, key);
				used.add(pubKeyHex);
			}
		}

		private void signFor(String pubKeyHex, Key key) throws Throwable {
			SignatureType sigType = sigTypeOf(key);
			if (sigType == SignatureType.ED25519) {
				PrivateKey signer = factory.lookupPrivateKey(pubKeyHex);
				byte[] sig = CommonUtils.unhex(SignatureGenerator.signBytes(data, signer));
				keySigs.add(new AbstractMap.SimpleEntry<>(key.getEd25519().toByteArray(), sig));
			} else if (sigType == SignatureType.ECDSA) {
				keySigs.add(new AbstractMap.SimpleEntry<>(key.getECDSA384().toByteArray(), NONSENSE_ECDSA_SIG));
			} else if (sigType == SignatureType.RSA) {
				keySigs.add(new AbstractMap.SimpleEntry<>(key.getRSA3072().toByteArray(), NONSENSE_RSA_SIG));
			}
			sigTypes.add(sigType);
		}

		private SignatureType sigTypeOf(Key key) {
			if (key.getEd25519() != ByteString.EMPTY) {
				return SignatureType.ED25519;
			} else if (key.getECDSA384() != ByteString.EMPTY) {
				return SignatureType.ECDSA;
			} else if (key.getRSA3072() != ByteString.EMPTY) {
				return SignatureType.RSA;
			}
			throw new AssertionError("Simple gRPC key has no public key!");
		}
	}
}
