package com.hedera.services.bdd.suites.contract.precompile;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CRYPTO_TRANSFER_CONS_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.nftTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferLists;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;

public class CryptoTransferHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoTransferHTSSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String FUNGIBLE_TOKEN = "TokenA";
	private static final String NFT_TOKEN = "Token_NFT";
	private static final String TOKEN_TREASURY = "treasury";
	private static final String RECEIVER = "receiver";
	private static final String RECEIVER2 = "receiver2";
	private static final String SENDER = "sender";
	private static final String SENDER2 = "sender2";

	public static void main(String... args) {
		new CryptoTransferHTSSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> negativeSpecs() {
		return List.of();
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
//				nonNestedCryptoTransferFungibleToken(),
//				nonNestedCryptoTransferMultipleFungibleTokens(),
//				nonNestedCryptoTransferNonFungibleToken(),
//				nonNestedCryptoTransferMultipleNonFungibleTokens(),
				nonNestedCryptoTransferFungibleAndNonFungibleTokens()
		);
	}

	private HapiApiSpec nonNestedCryptoTransferFungibleToken() {
		final var cryptoTransferFileByteCode = "cryptoTransferFileByteCode";
		final var multiKey = "purpose";
		final var theContract = "cryptoTransferContract";
		final var firstCryptoTransferTxn = "firstCryptoTransferTxn";

		return defaultHapiSpec("CryptoTransferFungibleToken")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.treasury(TOKEN_TREASURY),
						fileCreate(cryptoTransferFileByteCode)
								.payingWith(SENDER),
						updateLargeFile(SENDER, cryptoTransferFileByteCode,
								extractByteCode(ContractResources.CRYPTO_TRANSFER_CONTRACT)),
						tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)).payingWith(SENDER)
				).when(
						sourcing(() -> contractCreate(theContract, CRYPTO_TRANSFER_CONS_ABI)
								.bytecode(cryptoTransferFileByteCode).payingWith(SENDER)
								.gas(300_000L))
				).then(
						withOpContext(
								(spec, opLog) ->
								{
									final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
									final var sender = spec.registry().getAccountID(SENDER);
									final var receiver = spec.registry().getAccountID(RECEIVER);
									final var amountToBeSent = 50L;
									allRunFor(
											spec,
											contractCall(theContract, CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST,
													tokenTransferList().forToken(token).withAccountAmounts(accountAmount(sender, -amountToBeSent),
															accountAmount(receiver, amountToBeSent)).build()).payingWith(SENDER)
													.via(firstCryptoTransferTxn)
													.alsoSigningWithFullPrefix(multiKey));
								}),
						getTxnRecord(firstCryptoTransferTxn).andAllChildRecords().logged(),
						getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
						getAccountBalance(RECEIVER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 50),
						getAccountBalance(SENDER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 150),
						getTokenInfo(FUNGIBLE_TOKEN).logged()
				);
	}

	private HapiApiSpec nonNestedCryptoTransferMultipleFungibleTokens() {
		final var cryptoTransferFileByteCode = "cryptoTransferFileByteCode";
		final var multiKey = "purpose";
		final var theContract = "cryptoTransferContract";
		final var firstCryptoTransferTxn = "firstCryptoTransferTxn";

		return defaultHapiSpec("CryptoTransferFungibleTokens")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER2).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.treasury(TOKEN_TREASURY),
						fileCreate(cryptoTransferFileByteCode)
								.payingWith(SENDER),
						updateLargeFile(SENDER, cryptoTransferFileByteCode,
								extractByteCode(ContractResources.CRYPTO_TRANSFER_CONTRACT)),
						tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECEIVER2, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)).payingWith(SENDER)
				).when(
						sourcing(() -> contractCreate(theContract, CRYPTO_TRANSFER_CONS_ABI)
								.bytecode(cryptoTransferFileByteCode).payingWith(SENDER)
								.gas(300_000L))
				).then(
						withOpContext(
								(spec, opLog) ->
								{
									final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
									final var sender = spec.registry().getAccountID(SENDER);
									final var receiver = spec.registry().getAccountID(RECEIVER);
									final var receiver2 = spec.registry().getAccountID(RECEIVER2);
									allRunFor(
											spec,
											contractCall(theContract, CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST,
													tokenTransferList().forToken(token).withAccountAmounts(
															accountAmount(sender, -30L),
															accountAmount(sender, -20L),
															accountAmount(receiver, 30L),
															accountAmount(receiver2, 20L)).build()).payingWith(SENDER)
													.via(firstCryptoTransferTxn)
													.alsoSigningWithFullPrefix(multiKey));
								}),
						getTxnRecord(firstCryptoTransferTxn).andAllChildRecords().logged(),
						getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
						getAccountBalance(RECEIVER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 30),
						getAccountBalance(RECEIVER2)
								.hasTokenBalance(FUNGIBLE_TOKEN, 20),
						getAccountBalance(SENDER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 150),
						getTokenInfo(FUNGIBLE_TOKEN).logged()
				);
	}

	private HapiApiSpec nonNestedCryptoTransferNonFungibleToken() {
		final var cryptoTransferFileByteCode = "cryptoTransferFileByteCode";
		final var multiKey = "purpose";
		final var theContract = "cryptoTransferContract";
		final var firstCryptoTransferTxn = "firstCryptoTransferTxn";

		return defaultHapiSpec("CryptoTransferNonFungibleToken")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						fileCreate(cryptoTransferFileByteCode)
								.payingWith(SENDER),
						updateLargeFile(SENDER, cryptoTransferFileByteCode,
								extractByteCode(ContractResources.CRYPTO_TRANSFER_CONTRACT)),
						tokenAssociate(SENDER, List.of(NFT_TOKEN)),
						mintToken(NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(RECEIVER, List.of(NFT_TOKEN)),
						cryptoTransfer(TokenMovement.movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, SENDER)).payingWith(SENDER)
				).when(
						sourcing(() -> contractCreate(theContract, CRYPTO_TRANSFER_CONS_ABI)
								.bytecode(cryptoTransferFileByteCode).payingWith(SENDER)
								.gas(300_000L))
				).then(
						withOpContext(
								(spec, opLog) ->
								{
									final var token = spec.registry().getTokenID(NFT_TOKEN);
									final var sender = spec.registry().getAccountID(SENDER);
									final var receiver = spec.registry().getAccountID(RECEIVER);
									allRunFor(
											spec,
											contractCall(theContract, CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST,
													tokenTransferList().forToken(token).
															withNftTransfers(nftTransfer(sender, receiver, 1L)).build()).payingWith(SENDER)
													.via(firstCryptoTransferTxn)
													.alsoSigningWithFullPrefix(multiKey));
								}),
						getTxnRecord(firstCryptoTransferTxn).andAllChildRecords().logged(),
						getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
						getAccountInfo(RECEIVER).hasOwnedNfts(1),
						getAccountBalance(RECEIVER).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(SENDER).hasOwnedNfts(0),
						getAccountBalance(SENDER).hasTokenBalance(NFT_TOKEN, 0),
						getTokenInfo(NFT_TOKEN).logged()
				);
	}

	private HapiApiSpec nonNestedCryptoTransferMultipleNonFungibleTokens() {
		final var cryptoTransferFileByteCode = "cryptoTransferFileByteCode";
		final var multiKey = "purpose";
		final var theContract = "cryptoTransferContract";
		final var firstCryptoTransferTxn = "firstCryptoTransferTxn";

		return defaultHapiSpec("CryptoTransferNonFungibleTokens")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(SENDER2).balance(5 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER),
						cryptoCreate(RECEIVER2),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						fileCreate(cryptoTransferFileByteCode)
								.payingWith(SENDER),
						updateLargeFile(SENDER, cryptoTransferFileByteCode,
								extractByteCode(ContractResources.CRYPTO_TRANSFER_CONTRACT)),
						tokenAssociate(SENDER, List.of(NFT_TOKEN)),
						tokenAssociate(SENDER2, List.of(NFT_TOKEN)),
						mintToken(NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(RECEIVER, List.of(NFT_TOKEN)),
						tokenAssociate(RECEIVER2, List.of(NFT_TOKEN)),
						cryptoTransfer(TokenMovement.movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, SENDER)).payingWith(SENDER),
						cryptoTransfer(TokenMovement.movingUnique(NFT_TOKEN, 2).between(TOKEN_TREASURY, SENDER2)).payingWith(SENDER2)
				).when(
						sourcing(() -> contractCreate(theContract, CRYPTO_TRANSFER_CONS_ABI)
								.bytecode(cryptoTransferFileByteCode).payingWith(SENDER)
								.gas(300_000L))
				).then(
						withOpContext(
								(spec, opLog) ->
								{
									final var token = spec.registry().getTokenID(NFT_TOKEN);
									final var sender = spec.registry().getAccountID(SENDER);
									final var sender2 = spec.registry().getAccountID(SENDER2);
									final var receiver = spec.registry().getAccountID(RECEIVER);
									final var receiver2 = spec.registry().getAccountID(RECEIVER2);
									allRunFor(
											spec,
											contractCall(theContract, CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST,
													tokenTransferList().forToken(token).
															withNftTransfers(
																	nftTransfer(sender, receiver, 1L),
																	nftTransfer(sender2, receiver2, 2L))
																	.build()).payingWith(SENDER)
													.via(firstCryptoTransferTxn)
													.alsoSigningWithFullPrefix(multiKey));
								}),
						getTxnRecord(firstCryptoTransferTxn).andAllChildRecords().logged(),
						getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
						getAccountInfo(RECEIVER).hasOwnedNfts(1),
						getAccountBalance(RECEIVER).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(SENDER).hasOwnedNfts(0),
						getAccountBalance(SENDER).hasTokenBalance(NFT_TOKEN, 0),
						getAccountInfo(RECEIVER2).hasOwnedNfts(1),
						getAccountBalance(RECEIVER2).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(SENDER2).hasOwnedNfts(0),
						getAccountBalance(SENDER2).hasTokenBalance(NFT_TOKEN, 0),
						getTokenInfo(NFT_TOKEN).logged()
				);
	}

	private HapiApiSpec nonNestedCryptoTransferFungibleAndNonFungibleTokens() {
		final var cryptoTransferFileByteCode = "cryptoTransferFileByteCode";
		final var multiKey = "purpose";
		final var theContract = "cryptoTransferContract";
		final var firstCryptoTransferTxn = "firstCryptoTransferTxn";

		return defaultHapiSpec("CryptoTransferFungibleAndNonFungibleToken")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER),
						cryptoCreate(SENDER2).balance(5 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER2),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.treasury(TOKEN_TREASURY),
						tokenCreate(NFT_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						fileCreate(cryptoTransferFileByteCode)
								.payingWith(SENDER),
						updateLargeFile(SENDER, cryptoTransferFileByteCode,
								extractByteCode(ContractResources.CRYPTO_TRANSFER_CONTRACT)),
						tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(SENDER2, List.of(NFT_TOKEN)),
						mintToken(NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECEIVER2, List.of(NFT_TOKEN)),
						cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)).payingWith(SENDER),
						cryptoTransfer(TokenMovement.movingUnique(NFT_TOKEN, 2).between(TOKEN_TREASURY, SENDER2)).payingWith(SENDER2)
				).when(
						sourcing(() -> contractCreate(theContract, CRYPTO_TRANSFER_CONS_ABI)
								.bytecode(cryptoTransferFileByteCode).payingWith(SENDER)
								.gas(300_000L))
				).then(
						withOpContext(
								(spec, opLog) ->
								{
									final var fungibleToken = spec.registry().getTokenID(FUNGIBLE_TOKEN);
									final var nonFungibleToken = spec.registry().getTokenID(NFT_TOKEN);
									final var fungibleTokenSender = spec.registry().getAccountID(SENDER);
									final var fungibleTokenReceiver = spec.registry().getAccountID(RECEIVER);
									final var nonFungibleTokenSender = spec.registry().getAccountID(SENDER2);
									final var nonFungibleTokenReceiver = spec.registry().getAccountID(RECEIVER2);
									allRunFor(
											spec,
											contractCall(theContract, CRYPTO_TRANSFER_FUNGIBLE_TOKENS_LIST,
													tokenTransferLists().withTokenTransferList(
															tokenTransferList().isSingleList(false).forToken(fungibleToken).
																	withAccountAmounts(
																			accountAmount(fungibleTokenSender, -45L),
																			accountAmount(fungibleTokenReceiver, 45L)).build(),
															tokenTransferList().isSingleList(false).forToken(nonFungibleToken).
																	withNftTransfers(
																			nftTransfer(nonFungibleTokenSender, nonFungibleTokenReceiver, 2L)).build())
															.build()).payingWith(SENDER2)
													.via(firstCryptoTransferTxn)
													.alsoSigningWithFullPrefix(multiKey));
								}),
						getTxnRecord(firstCryptoTransferTxn).andAllChildRecords().logged(),
						getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
						getAccountBalance(RECEIVER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 45),
						getAccountBalance(SENDER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 155),
						getTokenInfo(FUNGIBLE_TOKEN).logged(),
						getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
						getAccountInfo(RECEIVER2).hasOwnedNfts(1),
						getAccountBalance(RECEIVER2).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(SENDER2).hasOwnedNfts(0),
						getAccountBalance(SENDER2).hasTokenBalance(NFT_TOKEN, 0),
						getTokenInfo(NFT_TOKEN).logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}