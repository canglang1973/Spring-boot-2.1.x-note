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

package org.springframework.boot.context.event;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ErrorHandler;

/**
 * {@link SpringApplicationRunListener} to publish {@link SpringApplicationEvent}s.
 * <p>
 * Uses an internal {@link ApplicationEventMulticaster} for the events that are fired
 * before the context is actually refreshed.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Artsiom Yudovin
 * @since 1.0.0
 */
public class EventPublishingRunListener implements SpringApplicationRunListener, Ordered {

	private final SpringApplication application;

	private final String[] args;

	//一个简单的事件广播器,主要用于上下文还没加载好或上下文加载失败需要广播事件的时候使用
	private final SimpleApplicationEventMulticaster initialMulticaster;

	public EventPublishingRunListener(SpringApplication application, String[] args) {
		this.application = application;
		this.args = args;
		this.initialMulticaster = new SimpleApplicationEventMulticaster();
		for (ApplicationListener<?> listener : application.getListeners()) {
			//给广播器初始化所有的监听器listener
			this.initialMulticaster.addApplicationListener(listener);
		}
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public void starting() {
		//发布应用开始启动事件ApplicationStartingEvent
		this.initialMulticaster.multicastEvent(new ApplicationStartingEvent(this.application, this.args));
	}

	@Override
	public void environmentPrepared(ConfigurableEnvironment environment) {
		//发布环境已经准备好的事件ApplicationEnvironmentPreparedEvent
		this.initialMulticaster
				.multicastEvent(new ApplicationEnvironmentPreparedEvent(this.application, this.args, environment));
	}

	@Override
	public void contextPrepared(ConfigurableApplicationContext context) {
		//发布上下文已经初始化完毕事件ApplicationContextInitializedEvent,此实现在2.1.0添加,在此之前为空实现,但是此时不存在监听此事件的listener
		this.initialMulticaster
				.multicastEvent(new ApplicationContextInitializedEvent(this.application, this.args, context));
	}

	@Override
	public void contextLoaded(ConfigurableApplicationContext context) {
		for (ApplicationListener<?> listener : this.application.getListeners()) {
			if (listener instanceof ApplicationContextAware) {
				/**
				 * ApplicationContextAware:
				 * 希望由在其运行的{@link ApplicationContext}收到通知的任何对象实现的接口。
				 * 例如，当对象需要访问一组协作bean时，实现此接口很有意义。请注意，对于bean查找而言，通过bean引用进行配置比仅实现该接口更可取。
				 * 如果对象需要访问文件资源（例如，想要调用{@code getResource}，想要发布应用程序事件或需要访问MessageSource），则也可以实现此接口。
				 * 但是，在这种特定情况下，最好实现更具体的{@link ResourceLoaderAware}，
				 * {@link ApplicationEventPublisherAware}或{@link MessageSourceAware}接口。
				 * 请注意，文件资源依赖项也可以作为{@link org.springframework.core.io.Resource}类型的bean属性公开，通过Strings填充，
				 * 并且由bean工厂自动进行类型转换。这样就无需为了访问特定文件资源而实现任何回调接口。
				 *  {@link org.springframework.context.support.ApplicationObjectSupport}是应用程序对象的便捷基类，实现了此接口。
				 *  有关所有bean生命周期方法的列表，请参阅 {@link org.springframework.beans.factory.BeanFactory BeanFactory javadocs}。
				 */
				((ApplicationContextAware) listener).setApplicationContext(context);
			}
			context.addApplicationListener(listener);
		}
		/**
		 * 资源已加载但还没有刷新 可以开始使用环境资源Environment了 发布ApplicationPreparedEvent
		 * 依次执行以下listener
		 * 0 = {ConfigFileApplicationListener} 添加适当的后处理器PropertySourceOrderingPostProcessor以后配置属性源
		 * 1 = {LoggingApplicationListener} 将loggingSystem注入beanFactory
		 * 2 = {BackgroundPreinitializer} 此监听器对于事件ApplicationPreparedEvent什么也没做
		 * 3 = {DelegatingApplicationListener}此监听器对于事件ApplicationPreparedEvent什么也没做
		 */
		this.initialMulticaster.multicastEvent(new ApplicationPreparedEvent(this.application, this.args, context));
	}

	@Override
	public void started(ConfigurableApplicationContext context) {
		//发布应用已启动事件ApplicationStartedEvent
		context.publishEvent(new ApplicationStartedEvent(this.application, this.args, context));
	}

	@Override
	public void running(ConfigurableApplicationContext context) {
		//发布应用已经准备好接受请求的事件ApplicationReadyEvent
		context.publishEvent(new ApplicationReadyEvent(this.application, this.args, context));
	}

	@Override
	public void failed(ConfigurableApplicationContext context, Throwable exception) {
		ApplicationFailedEvent event = new ApplicationFailedEvent(this.application, this.args, context, exception);
		if (context != null && context.isActive()) {
			// Listeners have been registered to the application context so we should
			// use it at this point if we can
			//如果监听器已经注册到了上下文中,我们就使用上下文发布事件
			context.publishEvent(event);
		}
		else {
			// An inactive context may not have a multicaster so we use our multicaster to
			// call all of the context's listeners instead
			//非活动上下文可能没有多播程序，因此使用自己的多播程序调用上下文的所有侦听器
			if (context instanceof AbstractApplicationContext) {
				for (ApplicationListener<?> listener : ((AbstractApplicationContext) context)
						.getApplicationListeners()) {
					this.initialMulticaster.addApplicationListener(listener);
				}
			}
			this.initialMulticaster.setErrorHandler(new LoggingErrorHandler());
			this.initialMulticaster.multicastEvent(event);
		}
	}

	private static class LoggingErrorHandler implements ErrorHandler {

		private static Log logger = LogFactory.getLog(EventPublishingRunListener.class);

		@Override
		public void handleError(Throwable throwable) {
			logger.warn("Error calling ApplicationEventListener", throwable);
		}

	}

}
