package com.hedera.services.context.annotations;

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

import com.swirlds.common.AddressBook;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldDualState;

import javax.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Distinguishes a bound {@code String} instance that represents the address book memo of the node during
 * {@link com.hedera.services.ServicesState#init(Platform, AddressBook, SwirldDualState)}. The "static"
 * qualifier is meant to emphasize the current system does not allow for the possibility of the node's
 * account changing dynamically (i.e., without a network restart).
 */
@Target({ ElementType.METHOD, ElementType.PARAMETER })
@Qualifier
@Retention(RUNTIME)
public @interface StaticAccountMemo {
}
