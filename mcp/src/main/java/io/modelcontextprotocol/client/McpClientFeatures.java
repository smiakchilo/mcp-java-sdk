/*
 * Copyright 2024-2024 the original author or authors.
 */

package io.modelcontextprotocol.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.Utils;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Representation of features and capabilities for Model Context Protocol (MCP) clients.
 * This class provides two types for managing client features:
 * <ul>
 * <li>{@link Async} for non-blocking operations with Project Reactor's Mono responses
 * <li>{@link Sync} for blocking operations with direct responses
 * </ul>
 *
 * <p>
 * Each feature specification includes:
 * <ul>
 * <li>Client implementation information and capabilities
 * <li>Root URI mappings for resource access
 * <li>Change notification handlers for tools, resources, and prompts
 * <li>Logging message consumers
 * <li>Message sampling handlers for request processing
 * </ul>
 *
 * <p>
 * The class supports conversion between synchronous and asynchronous specifications
 * through the {@link Async#fromSync} method, which ensures proper handling of blocking
 * operations in non-blocking contexts by scheduling them on a bounded elastic scheduler.
 *
 * @author Dariusz Jędrzejczyk
 * @see McpClient
 * @see McpSchema.Implementation
 * @see McpSchema.ClientCapabilities
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class McpClientFeatures {

	/**
	 * Asynchronous client features specification providing the capabilities and request
	 * and notification handlers
	 */
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	static class Async {

		private final McpSchema.Implementation clientInfo;
		private final McpSchema.ClientCapabilities clientCapabilities;
		private final Map<String, McpSchema.Root> roots;
		private final List<Function<List<McpSchema.Tool>, Mono<Void>>> toolsChangeConsumers;
		private final List<Function<List<McpSchema.Resource>, Mono<Void>>> resourcesChangeConsumers;
		private final List<Function<List<McpSchema.ResourceContents>, Mono<Void>>> resourcesUpdateConsumers;
		private final List<Function<List<McpSchema.Prompt>, Mono<Void>>> promptsChangeConsumers;
		private final List<Function<McpSchema.LoggingMessageNotification, Mono<Void>>> loggingConsumers;
		private final List<Function<McpSchema.ProgressNotification, Mono<Void>>> progressConsumers;
		private final Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>> samplingHandler;
		private final Function<McpSchema.ElicitRequest, Mono<McpSchema.ElicitResult>> elicitationHandler;

		/**
		 * Create an instance and validate the arguments.
		 * @param clientCapabilities the client capabilities.
		 * @param roots the roots.
		 * @param toolsChangeConsumers the tools change consumers.
		 * @param resourcesChangeConsumers the resources change consumers.
		 * @param promptsChangeConsumers the prompts change consumers.
		 * @param loggingConsumers the logging consumers.
		 * @param progressConsumers the progress consumers.
		 * @param samplingHandler the sampling handler.
		 * @param elicitationHandler the elicitation handler.
		 */
		public Async(
				McpSchema.Implementation clientInfo, McpSchema.ClientCapabilities clientCapabilities,
				Map<String, McpSchema.Root> roots,
				List<Function<List<McpSchema.Tool>, Mono<Void>>> toolsChangeConsumers,
				List<Function<List<McpSchema.Resource>, Mono<Void>>> resourcesChangeConsumers,
				List<Function<List<McpSchema.ResourceContents>, Mono<Void>>> resourcesUpdateConsumers,
				List<Function<List<McpSchema.Prompt>, Mono<Void>>> promptsChangeConsumers,
				List<Function<McpSchema.LoggingMessageNotification, Mono<Void>>> loggingConsumers,
				List<Function<McpSchema.ProgressNotification, Mono<Void>>> progressConsumers,
				Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>> samplingHandler,
				Function<McpSchema.ElicitRequest, Mono<McpSchema.ElicitResult>> elicitationHandler) {

			Assert.notNull(clientInfo, "Client info must not be null");
			this.clientInfo = clientInfo;
			this.clientCapabilities = (clientCapabilities != null) ? clientCapabilities
					: new McpSchema.ClientCapabilities(null,
							!Utils.isEmpty(roots) ? new McpSchema.ClientCapabilities.RootCapabilities(false) : null,
							samplingHandler != null ? new McpSchema.ClientCapabilities.Sampling() : null,
							elicitationHandler != null ? new McpSchema.ClientCapabilities.Elicitation() : null);
			this.roots = roots != null ? new ConcurrentHashMap<>(roots) : new ConcurrentHashMap<>();

			this.toolsChangeConsumers = toolsChangeConsumers != null ? toolsChangeConsumers : List.of();
			this.resourcesChangeConsumers = resourcesChangeConsumers != null ? resourcesChangeConsumers : List.of();
			this.resourcesUpdateConsumers = resourcesUpdateConsumers != null ? resourcesUpdateConsumers : List.of();
			this.promptsChangeConsumers = promptsChangeConsumers != null ? promptsChangeConsumers : List.of();
			this.loggingConsumers = loggingConsumers != null ? loggingConsumers : List.of();
			this.progressConsumers = progressConsumers != null ? progressConsumers : List.of();
			this.samplingHandler = samplingHandler;
			this.elicitationHandler = elicitationHandler;
		}

		/**
		 * Convert a synchronous specification into an asynchronous one and provide
		 * blocking code offloading to prevent accidental blocking of the non-blocking
		 * transport.
		 * @param syncSpec a potentially blocking, synchronous specification.
		 * @return a specification which is protected from blocking calls specified by the
		 * user.
		 */
		public static Async fromSync(Sync syncSpec) {
			List<Function<List<McpSchema.Tool>, Mono<Void>>> toolsChangeConsumers = new ArrayList<>();
			for (Consumer<List<McpSchema.Tool>> consumer : syncSpec.toolsChangeConsumers()) {
				Function<List<McpSchema.Tool>, Mono<Void>> function = t -> Mono
						.<Void>fromRunnable(() -> consumer.accept(t))
						.subscribeOn(Schedulers.boundedElastic());
				toolsChangeConsumers.add(function);
			}

			List<Function<List<McpSchema.Resource>, Mono<Void>>> resourcesChangeConsumers = new ArrayList<>();
			for (Consumer<List<McpSchema.Resource>> consumer : syncSpec.resourcesChangeConsumers()) {
				Function<List<McpSchema.Resource>, Mono<Void>> function = r -> Mono
								.<Void>fromRunnable(() -> consumer.accept(r))
								.subscribeOn(Schedulers.boundedElastic());
				resourcesChangeConsumers.add(function);
			}

			List<Function<List<McpSchema.ResourceContents>, Mono<Void>>> resourcesUpdateConsumers = new ArrayList<>();
			for (Consumer<List<McpSchema.ResourceContents>> consumer : syncSpec.resourcesUpdateConsumers()) {
				Function<List<McpSchema.ResourceContents>, Mono<Void>> function = r -> Mono
						.<Void>fromRunnable(() -> consumer.accept(r))
						.subscribeOn(Schedulers.boundedElastic());
				resourcesUpdateConsumers.add(function);
			}

			List<Function<List<McpSchema.Prompt>, Mono<Void>>> promptsChangeConsumers = new ArrayList<>();
			for (Consumer<List<McpSchema.Prompt>> consumer : syncSpec.promptsChangeConsumers()) {
				Function<List<McpSchema.Prompt>, Mono<Void>> function = p -> Mono
						.<Void>fromRunnable(() -> consumer.accept(p))
						.subscribeOn(Schedulers.boundedElastic());
				promptsChangeConsumers.add(function);
			}

			List<Function<McpSchema.LoggingMessageNotification, Mono<Void>>> loggingConsumers = new ArrayList<>();
			for (Consumer<McpSchema.LoggingMessageNotification> consumer : syncSpec.loggingConsumers()) {
				Function<McpSchema.LoggingMessageNotification, Mono<Void>> function =
						l -> Mono
								.<Void>fromRunnable(() -> consumer.accept(l))
								.subscribeOn(Schedulers.boundedElastic());
				loggingConsumers.add(function);
			}

			List<Function<McpSchema.ProgressNotification, Mono<Void>>> progressConsumers = new ArrayList<>();
			for (Consumer<McpSchema.ProgressNotification> consumer : syncSpec.progressConsumers()) {
				Function<McpSchema.ProgressNotification, Mono<Void>> function = l -> Mono
						.<Void>fromRunnable(() -> consumer.accept(l))
						.subscribeOn(Schedulers.boundedElastic());
				progressConsumers.add(function);
			}

			Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>> samplingHandler = r -> Mono
							.fromCallable(() -> syncSpec.samplingHandler().apply(r))
							.subscribeOn(Schedulers.boundedElastic());

			Function<McpSchema.ElicitRequest, Mono<McpSchema.ElicitResult>> elicitationHandler = r -> Mono
				.fromCallable(() -> syncSpec.elicitationHandler().apply(r))
				.subscribeOn(Schedulers.boundedElastic());

			return new Async(
					syncSpec.clientInfo(),
					syncSpec.clientCapabilities(),
					syncSpec.roots(),
					toolsChangeConsumers,
					resourcesChangeConsumers,
					resourcesUpdateConsumers,
					promptsChangeConsumers,
					loggingConsumers,
					progressConsumers,
					samplingHandler,
					elicitationHandler);
		}
	}

	/**
	 * Synchronous client features specification providing the capabilities and request
	 * and notification handlers.
	 */
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class Sync {

		private final McpSchema.Implementation clientInfo;
		private final McpSchema.ClientCapabilities clientCapabilities;
		private final Map<String, McpSchema.Root> roots;
		private final List<Consumer<List<McpSchema.Tool>>> toolsChangeConsumers;
		private final List<Consumer<List<McpSchema.Resource>>> resourcesChangeConsumers;
		private final List<Consumer<List<McpSchema.ResourceContents>>> resourcesUpdateConsumers;
		private final List<Consumer<List<McpSchema.Prompt>>> promptsChangeConsumers;
		private final List<Consumer<McpSchema.LoggingMessageNotification>> loggingConsumers;
		private final List<Consumer<McpSchema.ProgressNotification>> progressConsumers;
		private final Function<McpSchema.CreateMessageRequest, McpSchema.CreateMessageResult> samplingHandler;
		private final Function<McpSchema.ElicitRequest, McpSchema.ElicitResult> elicitationHandler;

		/**
		 * Create an instance and validate the arguments.
		 * @param clientInfo the client implementation information.
		 * @param clientCapabilities the client capabilities.
		 * @param roots the roots.
		 * @param toolsChangeConsumers the tools change consumers.
		 * @param resourcesChangeConsumers the resources change consumers.
		 * @param resourcesUpdateConsumers the resource update consumers.
		 * @param promptsChangeConsumers the prompts change consumers.
		 * @param loggingConsumers the logging consumers.
		 * @param progressConsumers the progress consumers.
		 * @param samplingHandler the sampling handler.
		 * @param elicitationHandler the elicitation handler.
		 */
		public Sync(
				McpSchema.Implementation clientInfo,
				McpSchema.ClientCapabilities clientCapabilities,
				Map<String, McpSchema.Root> roots, List<Consumer<List<McpSchema.Tool>>> toolsChangeConsumers,
				List<Consumer<List<McpSchema.Resource>>> resourcesChangeConsumers,
				List<Consumer<List<McpSchema.ResourceContents>>> resourcesUpdateConsumers,
				List<Consumer<List<McpSchema.Prompt>>> promptsChangeConsumers,
				List<Consumer<McpSchema.LoggingMessageNotification>> loggingConsumers,
				List<Consumer<McpSchema.ProgressNotification>> progressConsumers,
				Function<McpSchema.CreateMessageRequest, McpSchema.CreateMessageResult> samplingHandler,
				Function<McpSchema.ElicitRequest, McpSchema.ElicitResult> elicitationHandler) {

			Assert.notNull(clientInfo, "Client info must not be null");
			this.clientInfo = clientInfo;
			this.clientCapabilities = (clientCapabilities != null) ? clientCapabilities
					: new McpSchema.ClientCapabilities(null,
							!Utils.isEmpty(roots) ? new McpSchema.ClientCapabilities.RootCapabilities(false) : null,
							samplingHandler != null ? new McpSchema.ClientCapabilities.Sampling() : null,
							elicitationHandler != null ? new McpSchema.ClientCapabilities.Elicitation() : null);
			this.roots = roots != null ? new HashMap<>(roots) : new HashMap<>();

			this.toolsChangeConsumers = toolsChangeConsumers != null ? toolsChangeConsumers : List.of();
			this.resourcesChangeConsumers = resourcesChangeConsumers != null ? resourcesChangeConsumers : List.of();
			this.resourcesUpdateConsumers = resourcesUpdateConsumers != null ? resourcesUpdateConsumers : List.of();
			this.promptsChangeConsumers = promptsChangeConsumers != null ? promptsChangeConsumers : List.of();
			this.loggingConsumers = loggingConsumers != null ? loggingConsumers : List.of();
			this.progressConsumers = progressConsumers != null ? progressConsumers : List.of();
			this.samplingHandler = samplingHandler;
			this.elicitationHandler = elicitationHandler;
		}
	}
}
