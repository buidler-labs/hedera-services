package com.hedera.services.context.properties;

import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static com.hedera.services.context.properties.BootstrapProperties.GLOBAL_DYNAMIC_PROPS;
import static com.hedera.services.context.properties.BootstrapProperties.PROP_TRANSFORMS;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.API_THROTTLING_PREFIX;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromString;
import static java.util.Map.entry;

public class ScreenedSysFileProps implements PropertySource {
	static Logger log = LogManager.getLogger(ScreenedSysFileProps.class);

	static String MISPLACED_PROP_TPL = "Property '%s' is not global/dynamic, please find it a proper home!";
	static String DEPRECATED_PROP_TPL = "Property name '%s' is deprecated, please use '%s' instead!";
	static String UNPARSEABLE_PROP_TPL = "Value '%s' is unparseable for '%s' (%s), being ignored!";
	static String UNTRANSFORMABLE_PROP_TPL = "Value '%s' is untransformable for deprecated '%s' (%s), being ignored!";

	static Map<String, String> STANDARDIZED_NAMES = Map.ofEntries(
			entry("configAccountNum", "ledger.maxAccountNum"),
			entry("defaultContractReceiverThreshold", "contracts.defaultReceiveThreshold"),
			entry("defaultContractSenderThreshold", "contracts.defaultSendThreshold"),
			entry("maxContractStateSize", "contracts.maxStorageKb"),
			entry("maxFileSize", "files.maxSizeKb"),
			entry("defaultFeeCollectionAccount", "ledger.fundingAccount"),
			entry("txReceiptTTL", "cache.records.ttl")
	);
	static Map<String, UnaryOperator<String>> STANDARDIZED_FORMATS = Map.ofEntries(
			entry("defaultFeeCollectionAccount", legacy -> "" + accountParsedFromString(legacy).getAccountNum())
	);

	Map<String, Object>	from121 = Collections.emptyMap();

	public void screenNew(ServicesConfigurationList rawProps) {
		from121 = rawProps.getNameValueList()
				.stream()
				.map(this::withStandardizedName)
				.filter(this::isValidGlobalDynamic)
				.filter(this::hasParseableValue)
				.collect(Collectors.toMap(Setting::getName, this::asTypedValue));
		var msg = "Global/dynamic properties overridden in system file are:\n " + GLOBAL_DYNAMIC_PROPS.stream()
				.filter(from121::containsKey)
				.sorted()
				.map(name -> String.format("%s=%s", name, from121.get(name)))
				.collect(Collectors.joining("\n  "));
		log.info(msg);
	}

	private boolean isValidGlobalDynamic(Setting prop) {
		var name = prop.getName();
		var clearlyBelongs = GLOBAL_DYNAMIC_PROPS.contains(name);
		var isThrottleProp = name.startsWith(API_THROTTLING_PREFIX);
		if (!clearlyBelongs && !isThrottleProp) {
			log.warn(String.format(MISPLACED_PROP_TPL, name));
		}
		return clearlyBelongs;
	}

	private Setting withStandardizedName(Setting rawProp) {
		var rawName = rawProp.getName();
		var standardizedName = STANDARDIZED_NAMES.getOrDefault(rawName, rawName);
		if (standardizedName != rawName) {
			log.warn(String.format(DEPRECATED_PROP_TPL, rawName, standardizedName));
		}
		var builder = rawProp.toBuilder().setName(standardizedName);
		if (STANDARDIZED_FORMATS.containsKey(rawName)) {
			try {
				builder.setValue(STANDARDIZED_FORMATS.get(rawName).apply(rawProp.getValue()));
			} catch (Exception reason) {
				log.warn(String.format(
						UNTRANSFORMABLE_PROP_TPL,
						rawProp.getValue(),
						rawName,
						reason.getClass().getSimpleName()));
				return rawProp;
			}
		}
		return builder.build();
	}

	private Object asTypedValue(Setting prop) {
		return PROP_TRANSFORMS.get(prop.getName()).apply(prop.getValue());
	}

	private boolean hasParseableValue(Setting prop) {
		try {
			PROP_TRANSFORMS.get(prop.getName()).apply(prop.getValue());
			return true;
		} catch (Exception reason) {
			log.warn(String.format(
					UNPARSEABLE_PROP_TPL,
					prop.getValue(),
					prop.getName(),
					reason.getClass().getSimpleName()));
			return false;
		}
	}

	@Override
	public boolean containsProperty(String name) {
		return from121.containsKey(name);
	}

	@Override
	public Object getProperty(String name) {
		return from121.get(name);
	}

	@Override
	public Set<String> allPropertyNames() {
		return from121.keySet();
	}
}
