package com.hedera.services.sigs.metadata;

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

import com.hedera.services.config.MockFileNumbers;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.sigs.order.KeyOrderingFailure;
import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.sigs.order.KeyOrderingFailure.IMMUTABLE_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_TOPIC;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_SCHEDULE;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_TOKEN;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StateChildrenSigMetadataLookupTest {
	@Mock
	private MutableStateChildren stateChildren;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private MerkleTopic topic;
	@Mock
	private MerkleToken token;
	@Mock
	private MerkleSchedule schedule;
	@Mock
	private MerkleAccount account;
	@Mock
	private TokenSigningMetadata tokenMeta;
	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private MerkleMap<EntityNum, MerkleTopic> topics;
	@Mock
	private Function<MerkleToken, TokenSigningMetadata> tokenMetaTransform;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private MerkleMap<EntityNum, MerkleSchedule> schedules;

	private MerkleMap<String, MerkleOptionalBlob> storage;
	private Map<FileID, HFileMeta> metaMap;

	private StateChildrenSigMetadataLookup subject;

	@BeforeEach
	void setUp() {
		subject = new StateChildrenSigMetadataLookup(
				new MockFileNumbers(), aliasManager, stateChildren, tokenMetaTransform);
	}

	@Test
	void canReportSourceSigningTime() {
		final var signedAt = Instant.ofEpochSecond(1_234_567L, 890);
		given(stateChildren.signedAt()).willReturn(signedAt);
		assertSame(signedAt, subject.sourceSignedAt());
	}

	@Test
	void recognizesMissingSchedule() {
		given(stateChildren.schedules()).willReturn(schedules);

		final var result = subject.scheduleSigningMetaFor(unknownSchedule, null);

		assertEquals(MISSING_SCHEDULE, result.failureIfAny());
	}

	@Test
	void recognizesScheduleWithoutExplicitPayer() {
		final var mockTxn = TransactionBody.newBuilder()
				.setContractCall(ContractCallTransactionBody.getDefaultInstance())
				.build();

		given(stateChildren.schedules()).willReturn(schedules);
		given(schedules.get(EntityNum.fromScheduleId(knownSchedule))).willReturn(schedule);
		given(schedule.adminKey()).willReturn(Optional.of(simple));
		given(schedule.ordinaryViewOfScheduledTxn()).willReturn(mockTxn);

		final var result = subject.scheduleSigningMetaFor(knownSchedule, null);

		assertTrue(result.succeeded());
		assertEquals(Optional.empty(), result.metadata().designatedPayer());
		assertSame(mockTxn, result.metadata().scheduledTxn());
		assertEquals(Optional.of(simple), result.metadata().adminKey());
	}

	@Test
	void recognizesScheduleWithExplicitPayer() {
		final var explicitPayer = new EntityId(0, 0, 5678);
		final var mockTxn = TransactionBody.newBuilder()
				.setContractCall(ContractCallTransactionBody.getDefaultInstance())
				.build();

		given(stateChildren.schedules()).willReturn(schedules);
		given(schedules.get(EntityNum.fromScheduleId(knownSchedule))).willReturn(schedule);
		given(schedule.hasExplicitPayer()).willReturn(true);
		given(schedule.payer()).willReturn(explicitPayer);
		given(schedule.adminKey()).willReturn(Optional.of(simple));
		given(schedule.ordinaryViewOfScheduledTxn()).willReturn(mockTxn);

		final var linkedRefs = new LinkedRefs();
		final var result = subject.scheduleSigningMetaFor(knownSchedule, linkedRefs);

		assertTrue(result.succeeded());
		assertEquals(knownSchedule.getScheduleNum(), linkedRefs.linkedNumbers()[0]);
		assertEquals(Optional.of(explicitPayer.toGrpcAccountId()), result.metadata().designatedPayer());
		assertSame(mockTxn, result.metadata().scheduledTxn());
		assertEquals(Optional.of(simple), result.metadata().adminKey());
	}

	@Test
	void recognizesMissingContract() {
		given(stateChildren.accounts()).willReturn(accounts);

		final var result = subject.contractSigningMetaFor(unknownContract, null);

		assertEquals(INVALID_CONTRACT, result.failureIfAny());
	}

	@Test
	void recognizesDeletedContract() {
		given(stateChildren.accounts()).willReturn(accounts);
		given(accounts.get(EntityNum.fromContractId(knownContract))).willReturn(account);
		given(account.isDeleted()).willReturn(true);

		final var result = subject.contractSigningMetaFor(knownContract, null);

		assertEquals(INVALID_CONTRACT, result.failureIfAny());
	}

	@Test
	void recognizesNonContractAccount() {
		given(stateChildren.accounts()).willReturn(accounts);
		given(accounts.get(EntityNum.fromContractId(knownContract))).willReturn(account);

		final var result = subject.contractSigningMetaFor(knownContract, null);

		assertEquals(INVALID_CONTRACT, result.failureIfAny());
	}

	@Test
	void recognizesImmutableContract() {
		given(stateChildren.accounts()).willReturn(accounts);
		given(accounts.get(EntityNum.fromContractId(knownContract))).willReturn(account);
		given(account.isSmartContract()).willReturn(true);

		final var nullResult = subject.contractSigningMetaFor(knownContract, null);
		assertEquals(IMMUTABLE_CONTRACT, nullResult.failureIfAny());

		given(account.getAccountKey()).willReturn(contract);
		final var contractResult = subject.contractSigningMetaFor(knownContract, null);
		assertEquals(IMMUTABLE_CONTRACT, contractResult.failureIfAny());
	}

	@Test
	void recognizesExtantContract() {
		given(stateChildren.accounts()).willReturn(accounts);
		given(accounts.get(EntityNum.fromContractId(knownContract))).willReturn(account);
		given(account.getAccountKey()).willReturn(simple);
		given(account.isSmartContract()).willReturn(true);
		given(account.isReceiverSigRequired()).willReturn(true);

		final var linkedRefs = new LinkedRefs();
		final var result = subject.contractSigningMetaFor(knownContract, linkedRefs);

		assertTrue(result.succeeded());
		assertEquals(knownContract.getContractNum(), linkedRefs.linkedNumbers()[0]);
		assertTrue(result.metadata().receiverSigRequired());
		assertSame(simple, result.metadata().key());
	}

	@Test
	void recognizesMissingAccountNum() {
		given(stateChildren.accounts()).willReturn(accounts);

		final var linkedRefs = new LinkedRefs();
		final var result = subject.accountSigningMetaFor(unknownAccount, linkedRefs);

		assertEquals(unknownAccount.getAccountNum(), linkedRefs.linkedNumbers()[0]);
		assertEquals(MISSING_ACCOUNT, result.failureIfAny());
	}

	@Test
	void recognizesExtantAccount() {
		given(stateChildren.accounts()).willReturn(accounts);
		given(accounts.get(EntityNum.fromAccountId(knownAccount))).willReturn(account);
		given(account.getAccountKey()).willReturn(simple);
		given(account.isReceiverSigRequired()).willReturn(true);

		final var linkedRefs = new LinkedRefs();
		final var result = subject.aliasableAccountSigningMetaFor(knownAccount, linkedRefs);

		assertTrue(linkedRefs.linkedAliases().isEmpty());
		assertEquals(knownAccount.getAccountNum(), linkedRefs.linkedNumbers()[0]);
		assertTrue(result.succeeded());
		assertTrue(result.metadata().receiverSigRequired());
		assertSame(simple, result.metadata().key());
	}

	@Test
	void recognizesExtantAlias() {
		final var knownNum = EntityNum.fromAccountId(knownAccount);
		given(stateChildren.accounts()).willReturn(accounts);
		given(aliasManager.lookupIdBy(alias.getAlias())).willReturn(knownNum);
		given(accounts.get(knownNum)).willReturn(account);
		given(account.getAccountKey()).willReturn(simple);
		given(account.isReceiverSigRequired()).willReturn(true);

		final var linkedRefs = new LinkedRefs();
		final var result = subject.aliasableAccountSigningMetaFor(alias, linkedRefs);

		assertTrue(result.succeeded());
		assertEquals(List.of(alias.getAlias()), linkedRefs.linkedAliases());
		assertEquals(knownAccount.getAccountNum(), linkedRefs.linkedNumbers()[0]);
		assertTrue(result.metadata().receiverSigRequired());
		assertSame(simple, result.metadata().key());
	}

	@Test
	void recognizesMissingAlias() {
		given(aliasManager.lookupIdBy(alias.getAlias())).willReturn(EntityNum.MISSING_NUM);

		final var linkedRefs = new LinkedRefs();
		final var result = subject.aliasableAccountSigningMetaFor(alias, linkedRefs);

		assertEquals(List.of(alias.getAlias()), linkedRefs.linkedAliases());
		assertEquals(MISSING_ACCOUNT, result.failureIfAny());
	}

	@Test
	void recognizesMissingToken() {
		given(stateChildren.tokens()).willReturn(tokens);

		final var result = subject.tokenSigningMetaFor(unknownToken, null);

		assertEquals(MISSING_TOKEN, result.failureIfAny());
	}

	@Test
	void recognizesExtantToken() {
		given(stateChildren.tokens()).willReturn(tokens);
		given(tokens.get(EntityNum.fromTokenId(knownToken))).willReturn(token);
		given(tokenMetaTransform.apply(token)).willReturn(tokenMeta);

		final var linkedRefs = new LinkedRefs();
		final var result = subject.tokenSigningMetaFor(knownToken, linkedRefs);

		assertSame(tokenMeta, result.metadata());
		assertEquals(knownToken.getTokenNum(), linkedRefs.linkedNumbers()[0]);
	}

	@Test
	void includesTopicKeysIfPresent() {
		given(stateChildren.topics()).willReturn(topics);
		given(topics.get(EntityNum.fromTopicId(knownTopic))).willReturn(topic);
		given(topic.hasAdminKey()).willReturn(true);
		given(topic.hasSubmitKey()).willReturn(true);
		given(topic.getAdminKey()).willReturn(wacl);
		given(topic.getSubmitKey()).willReturn(simple);

		final var linkedRefs = new LinkedRefs();
		final var result = subject.topicSigningMetaFor(knownTopic, linkedRefs);

		assertTrue(result.succeeded());
		assertSame(wacl, result.metadata().adminKey());
		assertSame(simple, result.metadata().submitKey());
		assertEquals(knownTopic.getTopicNum(), linkedRefs.linkedNumbers()[0]);
	}

	@Test
	void omitsTopicKeysIfAbsent() {
		given(stateChildren.topics()).willReturn(topics);
		given(topics.get(EntityNum.fromTopicId(knownTopic))).willReturn(topic);

		final var result = subject.topicSigningMetaFor(knownTopic, null);

		assertTrue(result.succeeded());
		assertFalse(result.metadata().hasAdminKey());
		assertFalse(result.metadata().hasSubmitKey());
	}

	@Test
	void returnsMissingTopicMeta() {
		given(stateChildren.topics()).willReturn(topics);

		final var result = subject.topicSigningMetaFor(unknownTopic, null);

		assertEquals(INVALID_TOPIC, result.failureIfAny());
	}

	@Test
	void failsOnDeletedTopic() {
		given(stateChildren.topics()).willReturn(topics);
		given(topics.get(EntityNum.fromTopicId(knownTopic))).willReturn(topic);
		given(topic.isDeleted()).willReturn(true);

		final var result = subject.topicSigningMetaFor(knownTopic, null);

		Assertions.assertEquals(INVALID_TOPIC, result.failureIfAny());
	}

	@Test
	void returnsExtantFileMeta() {
		setupNonSpecialFileTest();
		givenFile(knownFile, false, expiry, wacl);

		final var linkedRefs = new LinkedRefs();
		final var result = subject.fileSigningMetaFor(knownFile, linkedRefs);

		assertTrue(result.succeeded());
		assertEquals(wacl.toString(), result.metadata().wacl().toString());
		assertEquals(knownFile.getFileNum(), linkedRefs.linkedNumbers()[0]);
	}

	@Test
	void failsOnMissingFileMeta() {
		setupNonSpecialFileTest();
		givenFile(knownFile, false, expiry, wacl);

		final var result = subject.fileSigningMetaFor(unknownFile, null);

		assertFalse(result.succeeded());
		assertEquals(KeyOrderingFailure.MISSING_FILE, result.failureIfAny());
	}

	@Test
	void returnsSpecialFileMeta() {
		final var result = subject.fileSigningMetaFor(knownSpecialFile, null);

		assertTrue(result.succeeded());
		assertSame(StateView.EMPTY_WACL, result.metadata().wacl());
	}

	private void setupNonSpecialFileTest() {
		storage = new MerkleMap<>();
		given(stateChildren.storage()).willReturn(storage);
		metaMap = MetadataMapFactory.metaMapFrom(new FcBlobsBytesStore(MerkleOptionalBlob::new, () -> storage));
	}

	private void givenFile(final FileID fid, final boolean isDeleted, final long expiry, final JKey wacl) {
		final var meta = new HFileMeta(isDeleted, wacl, expiry);
		metaMap.put(fid, meta);
	}

	private static final FileID knownFile = IdUtils.asFile("0.0.898989");
	private static final FileID unknownFile = IdUtils.asFile("0.0.989898");
	private static final FileID knownSpecialFile = IdUtils.asFile("0.0.150");
	private static final TopicID knownTopic = IdUtils.asTopic("0.0.1111");
	private static final TopicID unknownTopic = IdUtils.asTopic("0.0.2222");
	private static final TokenID knownToken = IdUtils.asToken("0.0.1111");
	private static final TokenID unknownToken = IdUtils.asToken("0.0.2222");
	private static final long expiry = 1_234_567L;
	private static final JKey simple = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
	private static final JKeyList wacl = new JKeyList(List.of(
			new JECDSASecp256k1Key("012345789012345789012345789012".getBytes())
	));
	private static final JContractIDKey contract = new JContractIDKey(0, 0, 1234);
	private static final AccountID alias = AccountID.newBuilder()
			.setAlias(asKeyUnchecked(simple).toByteString())
			.build();
	private static final AccountID knownAccount = IdUtils.asAccount("0.0.1234");
	private static final AccountID unknownAccount = IdUtils.asAccount("0.0.4321");
	private static final ContractID knownContract = IdUtils.asContract("0.0.1234");
	private static final ContractID unknownContract = IdUtils.asContract("0.0.4321");
	private static final ScheduleID knownSchedule = IdUtils.asSchedule("0.0.1234");
	private static final ScheduleID unknownSchedule = IdUtils.asSchedule("0.0.4321");
}
