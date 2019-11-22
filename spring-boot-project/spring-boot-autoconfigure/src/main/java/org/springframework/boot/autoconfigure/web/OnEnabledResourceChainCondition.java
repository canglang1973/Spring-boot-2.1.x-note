/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.web;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * {@link Condition} that checks whether or not the Spring resource handling chain is
 * enabled.
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @author Madhura Bhave
 * @see ConditionalOnEnabledResourceChain
 */
class OnEnabledResourceChainCondition extends SpringBootCondition {

	private static final String WEBJAR_ASSET_LOCATOR = "org.webjars.WebJarAssetLocator";

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		ConfigurableEnvironment environment = (ConfigurableEnvironment) context.getEnvironment();
		// 获得spring.resources.chain.strategy.fixed.enabled 的配置,默认为false
		boolean fixed = getEnabledProperty(environment, "strategy.fixed.", false);
		// 获得spring.resources.chain.strategy.content.enabled 的配置,默认为false
		boolean content = getEnabledProperty(environment, "strategy.content.", false);
		// 获得spring.resources.chain.enabled 的配置,默认为null
		Boolean chain = getEnabledProperty(environment, "", null);
		Boolean match = ResourceProperties.Chain.getEnabled(fixed, content, chain);
		ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnEnabledResourceChain.class);
		if (match == null) {
			if (ClassUtils.isPresent(WEBJAR_ASSET_LOCATOR, getClass().getClassLoader())) {
				// 如果存在 org.webjars.WebJarAssetLocator,则返回匹配
				return ConditionOutcome.match(message.found("class").items(WEBJAR_ASSET_LOCATOR));
			}
			// 否则返回不匹配
			return ConditionOutcome.noMatch(message.didNotFind("class").items(WEBJAR_ASSET_LOCATOR));
		}
		if (match) {
			return ConditionOutcome.match(message.because("enabled"));
		}
		return ConditionOutcome.noMatch(message.because("disabled"));
	}

	private Boolean getEnabledProperty(ConfigurableEnvironment environment, String key, Boolean defaultValue) {
		String name = "spring.resources.chain." + key + "enabled";
		return environment.getProperty(name, Boolean.class, defaultValue);
	}

}
