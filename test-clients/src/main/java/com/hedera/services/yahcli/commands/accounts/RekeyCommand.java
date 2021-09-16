package com.hedera.services.yahcli.commands.accounts;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.common.io.Files;
import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.RekeySuite;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@CommandLine.Command(
		name = "rekey",
		subcommands = { CommandLine.HelpCommand.class },
		description = "Replaces the key on an account")
public class RekeyCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	AccountsCommand accountsCommand;

	@CommandLine.Option(names = { "-k", "--replacement-key" },
			paramLabel = "path to new key file",
			defaultValue = "<N/A>")
	String replKeyLoc;

	@CommandLine.Option(names = { "-g", "--gen-new-key" },
			paramLabel = "auto-generate new key",
			defaultValue = "false")
	boolean genNewKey;

	@CommandLine.Parameters(
			arity = "1",
			paramLabel = "<account>",
			description = "number of account to rekey")
	String accountNum;

	@Override
	public Integer call() throws Exception {
		var config = ConfigManager.from(accountsCommand.getYahcli());
		config.assertNoMissingDefaults();
		COMMON_MESSAGES.printGlobalInfo(config);

		if ("<N/A>".equals(replKeyLoc) && !genNewKey) {
			throw new CommandLine.ParameterException(
					accountsCommand.getYahcli().getSpec().commandLine(),
					"Must set --gen-new-key if no --replacement-key given");
		}

		final var optKeyFile = backupCurrentAssets(config, accountNum);
		final String replTarget = optKeyFile
				.map(File::getPath)
				.orElseGet(() -> config.keysLoc() + File.separator + "account" + accountNum + ".pem");

		final var delegate = new RekeySuite(config.asSpecConfig(), accountNum, replKeyLoc, genNewKey, replTarget);
		delegate.runSuiteSync();
		COMMON_MESSAGES.info("SUCCESS - account 0.0." + accountNum + " has been re-keyed");

		return 0;
	}

	private Optional<File> backupCurrentAssets(ConfigManager configManager, String num) throws IOException {
		var optKeyFile = ConfigUtils.keyFileFor(configManager.keysLoc(), "account" + num);
		if (optKeyFile.isPresent()) {
			final var keyFile = optKeyFile.get();
			final var keyLoc = keyFile.getAbsolutePath();
			final var backupLoc = keyLoc + ".bkup";
			Files.copy(keyFile, java.nio.file.Files.newOutputStream(Paths.get(backupLoc)));
			if (keyLoc.endsWith(".pem")) {
				final var optPassFile = ConfigUtils.passFileFor(keyFile);
				if (optPassFile.isPresent()) {
					final var passFile = optPassFile.get();
					final var passBackupLoc = passFile.getAbsolutePath() + ".bkup";
					Files.copy(passFile, java.nio.file.Files.newOutputStream(Paths.get(passBackupLoc)));
				}
			}
		} else {
			COMMON_MESSAGES.warn("No current key for account " + num + ", payer will need special privileges");
		}
		return optKeyFile;
	}
}