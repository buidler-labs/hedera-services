package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenTransferWrapperTest {
	@Test
	void translatesNftExchangesAsExpected() {
		final var inputExchanges = wellKnownExchanges();
		final var nftSubject = new TokenTransferWrapper(inputExchanges, Collections.emptyList());

		final var builder = nftSubject.asGrpcBuilder();
		assertEquals(token, builder.getToken());
		final var exchanges = builder.getNftTransfersList();
		assertEquals(inputExchanges.stream().map(SyntheticTxnFactory.NftExchange::asGrpc).toList(), exchanges);
	}

	@Test
	void translatesFungibleTransfersAsExpected() {
		final var inputTransfers = wellKnownTransfers();
		final var expectedAdjustments = TokenTransferList.newBuilder()
				.setToken(token)
				.addTransfers(aaWith(anAccount, aChange))
				.addTransfers(aaWith(otherAccount, bChange))
				.addTransfers(aaWith(anotherAccount, cChange))
				.build();
		final var fungibleSubject = new TokenTransferWrapper(Collections.emptyList(), inputTransfers);

		final var builder = fungibleSubject.asGrpcBuilder();
		assertEquals(expectedAdjustments, builder.build());
	}

	@Test
	void translatesNoopAsExpected() {
		final var nothingSubject = new TokenTransferWrapper(Collections.emptyList(), Collections.emptyList());

		final var builder = nothingSubject.asGrpcBuilder();
		assertEquals(TokenTransferList.getDefaultInstance(), builder.build());
	}

	private AccountAmount aaWith(final AccountID account, final long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(account)
				.setAmount(amount)
				.build();
	}

	private List<SyntheticTxnFactory.NftExchange> wellKnownExchanges() {
		return List.of(
				new SyntheticTxnFactory.NftExchange(aSerialNo, token, anAccount, otherAccount),
				new SyntheticTxnFactory.NftExchange(bSerialNo, token, anAccount, anotherAccount));
	}

	private List<SyntheticTxnFactory.FungibleTokenTransfer> wellKnownTransfers() {
		return List.of(
				new SyntheticTxnFactory.FungibleTokenTransfer(Math.abs(aChange), token, anAccount, null),
				new SyntheticTxnFactory.FungibleTokenTransfer(Math.abs(bChange), token, null, otherAccount),
				new SyntheticTxnFactory.FungibleTokenTransfer(Math.abs(cChange), token, null, anotherAccount));
	}

	private final long aSerialNo = 1_234L;
	private final long bSerialNo = 2_234L;
	private final long aChange = -100L;
	private final long bChange = +75;
	private final long cChange = +25;
	private final AccountID anAccount = new Id(0, 0, 1234).asGrpcAccount();
	private final AccountID otherAccount = new Id(0, 0, 2345).asGrpcAccount();
	private final AccountID anotherAccount = new Id(0, 0, 3456).asGrpcAccount();
	private final TokenID token = new Id(0, 0, 75231).asGrpcToken();
	private final Id aNft = new Id(0, 0, 9999);
	private final Id bNft = new Id(0, 0, 10000);
}