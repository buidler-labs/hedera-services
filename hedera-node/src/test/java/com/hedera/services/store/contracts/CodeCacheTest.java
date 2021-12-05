package com.hedera.services.store.contracts;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.properties.NodeLocalProperties;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CodeCacheTest {
    @Mock
    NodeLocalProperties properties;
    @Mock
    MutableEntityAccess entityAccess;

    CodeCache codeCache;

    @BeforeEach
    void setup() {
        codeCache = new CodeCache(properties, entityAccess);
    }

    @Test
    void successfulCreate() {
        assertNotNull(codeCache.cache);
    }

    @Test
    void getTriggeringLoad() {
        given(entityAccess.fetchCode(any())).willReturn(Bytes.of("abc".getBytes()));
        Code code = codeCache.get(Address.fromHexString("0xabc"));

        assertEquals(Hash.fromHexString("0x4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"), code.getCodeHash());
    }

    @Test
    void getContractNotFound() {
        given(entityAccess.fetchCode(any())).willReturn(Bytes.EMPTY);
        Code code = codeCache.get(Address.fromHexString("0xabc"));

        assertTrue(code.getBytes().isEmpty());
    }

    @Test
    void invalidateSuccess() {
        given(entityAccess.fetchCode(any())).willReturn(Bytes.of("abc".getBytes()));
        Code code = codeCache.get(Address.fromHexString("0xabc"));

        assertEquals(1L, codeCache.size());
        codeCache.invalidate(Address.fromHexString("0xabc"));
        assertEquals(0, codeCache.size());
    }

    @Test
    void bytesKeyEquals() {
        CodeCache.BytesKey key1 = new CodeCache.BytesKey("abc".getBytes());
        CodeCache.BytesKey key2 = new CodeCache.BytesKey("abc".getBytes());
        assertEquals(key1, key2);
        assertEquals(key1, key1);

        // Workaround to sonar code-smells. It should NOT be an error to use assertTrue/assertFalse.
        boolean eq1 = key1.equals(null);
        boolean eq2 = key1.equals("abc");

        assertEquals(eq1, eq2);
    }
}