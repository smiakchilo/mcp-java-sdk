/*
 * Copyright 2024-2025 the original author or authors.
 */

package io.modelcontextprotocol.server;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.Utils;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP stateless server features specification that a particular server can choose to support.
 * @author Dariusz Jędrzejczyk
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class McpStatelessServerFeatures {

	/**
	 * Asynchronous server features specification
	 */
	@NoArgsConstructor
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class Async {

		/**
		 * The server implementation details
		 */
		private McpSchema.Implementation serverInfo;

		/**
		 * The server capabilities
		 */
		private McpSchema.ServerCapabilities serverCapabilities;

		/**
		 * The list of tool specifications
		 */
		private List<McpStatelessServerFeatures.AsyncToolSpecification> tools;

		/**
		 * The map of resource specifications
		 */
		private Map<String, AsyncResourceSpecification> resources;

		/**
		 * The list of resource templates
		 */
		private List<McpSchema.ResourceTemplate> resourceTemplates;

		/**
		 * The map of prompt specifications
		 */
		private Map<String, McpStatelessServerFeatures.AsyncPromptSpecification> prompts;

		/**
		 * The map of completion specifications
		 */
		private Map<McpSchema.CompleteReference, McpStatelessServerFeatures.AsyncCompletionSpecification> completions;

		/**
		 * The server instructions text
		 */
		private String instructions;

		/**
		 * Create an instance and validate the arguments
		 * @param serverInfo         The server implementation details
		 * @param serverCapabilities The server capabilities
		 * @param tools              The list of tool specifications
		 * @param resources          The map of resource specifications
		 * @param resourceTemplates  The list of resource templates
		 * @param prompts            The map of prompt specifications
		 * @param completions        The map of completion specifications
		 * @param instructions       The server instructions text
		 */
		public Async(
				McpSchema.Implementation serverInfo,
				McpSchema.ServerCapabilities serverCapabilities,
				List<AsyncToolSpecification> tools,
				Map<String, AsyncResourceSpecification> resources,
				List<McpSchema.ResourceTemplate> resourceTemplates,
				Map<String, AsyncPromptSpecification> prompts,
				Map<McpSchema.CompleteReference, AsyncCompletionSpecification> completions,
				String instructions) {

			Assert.notNull(serverInfo, "Server info must not be null");

			this.serverInfo = serverInfo;
			this.serverCapabilities = (serverCapabilities != null) ? serverCapabilities
					: new McpSchema.ServerCapabilities(null, // completions
					null, // experimental
					new McpSchema.ServerCapabilities.LoggingCapabilities(), // Enable logging by default
					!Utils.isEmpty(prompts) ? new McpSchema.ServerCapabilities.PromptCapabilities(false) : null,
					!Utils.isEmpty(resources)
							? new McpSchema.ServerCapabilities.ResourceCapabilities(false, false) : null,
					!Utils.isEmpty(tools) ? new McpSchema.ServerCapabilities.ToolCapabilities(false) : null);

			this.tools = (tools != null) ? tools : List.of();
			this.resources = (resources != null) ? resources : Map.of();
			this.resourceTemplates = (resourceTemplates != null) ? resourceTemplates : List.of();
			this.prompts = (prompts != null) ? prompts : Map.of();
			this.completions = (completions != null) ? completions : Map.of();
			this.instructions = instructions;
		}

		/**
		 * Convert a synchronous specification into an asynchronous one and provide blocking code offloading to prevent
		 * accidental blocking of the non-blocking transport.
		 * @param syncSpec           a potentially blocking, synchronous specification.
		 * @param immediateExecution when true, do not offload. Do NOT set to true when using a non-blocking transport.
		 * @return a specification which is protected from blocking calls specified by the user.
		 */
		static Async fromSync(Sync syncSpec, boolean immediateExecution) {
			List<McpStatelessServerFeatures.AsyncToolSpecification> tools = new ArrayList<>();
			for (var tool : syncSpec.tools()) {
				tools.add(AsyncToolSpecification.fromSync(tool, immediateExecution));
			}

			Map<String, AsyncResourceSpecification> resources = new HashMap<>();
			syncSpec.resources().forEach((key, resource) ->
					resources.put(key, AsyncResourceSpecification.fromSync(resource, immediateExecution)));

			Map<String, AsyncPromptSpecification> prompts = new HashMap<>();
			syncSpec.prompts().forEach((key, prompt) ->
					prompts.put(key, AsyncPromptSpecification.fromSync(prompt, immediateExecution)));

			Map<McpSchema.CompleteReference, McpStatelessServerFeatures.AsyncCompletionSpecification> completions =
					new HashMap<>();
			syncSpec.completions().forEach((key, completion) ->
					completions.put(key, AsyncCompletionSpecification.fromSync(completion, immediateExecution)));

			return new Async(syncSpec.serverInfo(), syncSpec.serverCapabilities(), tools, resources,
					syncSpec.resourceTemplates(), prompts, completions, syncSpec.instructions());
		}
	}

	/**
	 * Synchronous server features specification
	 */
	@NoArgsConstructor
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class Sync {

		/**
		 * The server implementation details
		 */
		private McpSchema.Implementation serverInfo;

		/**
		 * The server capabilities
		 */
		private McpSchema.ServerCapabilities serverCapabilities;

		/**
		 * The list of tool specifications
		 */
		private List<McpStatelessServerFeatures.SyncToolSpecification> tools;

		/**
		 * The map of resource specifications
		 */
		private Map<String, McpStatelessServerFeatures.SyncResourceSpecification> resources;

		/**
		 * The list of resource templates
		 */
		private List<McpSchema.ResourceTemplate> resourceTemplates;

		/**
		 * The map of prompt specifications
		 */
		private Map<String, McpStatelessServerFeatures.SyncPromptSpecification> prompts;

		/**
		 * The map of completion specifications
		 */
		private Map<McpSchema.CompleteReference, McpStatelessServerFeatures.SyncCompletionSpecification> completions;

		/**
		 * The server instructions text
		 */
		private String instructions;

		/**
		 * Create an instance and validate the arguments
		 * @param serverInfo         The server implementation details
		 * @param serverCapabilities The server capabilities
		 * @param tools              The list of tool specifications
		 * @param resources          The map of resource specifications
		 * @param resourceTemplates  The list of resource templates
		 * @param prompts            The map of prompt specifications
		 * @param completions        The map of completion specifications
		 * @param instructions       The server instructions text
		 */
		public Sync(
				McpSchema.Implementation serverInfo, McpSchema.ServerCapabilities serverCapabilities,
				List<McpStatelessServerFeatures.SyncToolSpecification> tools,
				Map<String, McpStatelessServerFeatures.SyncResourceSpecification> resources,
				List<McpSchema.ResourceTemplate> resourceTemplates,
				Map<String, McpStatelessServerFeatures.SyncPromptSpecification> prompts,
				Map<McpSchema.CompleteReference, McpStatelessServerFeatures.SyncCompletionSpecification> completions,
				String instructions) {

			Assert.notNull(serverInfo, "Server info must not be null");

			this.serverInfo = serverInfo;
			this.serverCapabilities = (serverCapabilities != null)
					? serverCapabilities
					: new McpSchema.ServerCapabilities(
							null, // completions
							null, // experimental
							new McpSchema.ServerCapabilities.LoggingCapabilities(), // Enable logging by default
							!Utils.isEmpty(prompts)
									? new McpSchema.ServerCapabilities.PromptCapabilities(false)
									: null,
							!Utils.isEmpty(resources)
									? new McpSchema.ServerCapabilities.ResourceCapabilities(false, false)
									: null,
							!Utils.isEmpty(tools)
									? new McpSchema.ServerCapabilities.ToolCapabilities(false)
									: null);

			this.tools = (tools != null) ? tools : new ArrayList<>();
			this.resources = (resources != null) ? resources : new HashMap<>();
			this.resourceTemplates = (resourceTemplates != null) ? resourceTemplates : new ArrayList<>();
			this.prompts = (prompts != null) ? prompts : new HashMap<>();
			this.completions = (completions != null) ? completions : new HashMap<>();
			this.instructions = instructions;
		}
	}

	/**
	 * Specification of a tool with its asynchronous handler function. Tools are the primary way for MCP servers to expose functionality to AI models. Each tool represents a specific capability
	 */
	@AllArgsConstructor
	@NoArgsConstructor
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class AsyncToolSpecification {

		/**
		 * The tool definition including name, description, and parameter schema
		 */
		private McpSchema.Tool tool;

		/**
		 * The function that implements the tool's logic, receiving a CallToolRequest and returning the result
		 */
		private BiFunction<McpTransportContext, CallToolRequest, Mono<McpSchema.CallToolResult>> callHandler;

		static AsyncToolSpecification fromSync(SyncToolSpecification syncToolSpec) {
			return fromSync(syncToolSpec, false);
		}

		static AsyncToolSpecification fromSync(SyncToolSpecification syncToolSpec, boolean immediate) {

			// FIXME: This is temporary, proper validation should be implemented
			if (syncToolSpec == null) {
				return null;
			}

			BiFunction<McpTransportContext, CallToolRequest, Mono<McpSchema.CallToolResult>> callHandler =
					(ctx, req) -> {
						var toolResult = Mono.fromCallable(() -> syncToolSpec.callHandler().apply(ctx, req));
						return immediate ? toolResult : toolResult.subscribeOn(Schedulers.boundedElastic());
					};

			return new AsyncToolSpecification(syncToolSpec.tool(), callHandler);
		}
	}

	/**
	 * Specification of a resource with its asynchronous handler function. Resources provide context to AI models by exposing data such as file contents, database records, API responses, system information, and application state
	 */
	@AllArgsConstructor
	@NoArgsConstructor
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class AsyncResourceSpecification {

		/**
		 * The resource definition including name, description, and MIME type
		 */
		private McpSchema.Resource resource;

		/**
		 * The function that handles resource read requests. The function's argument is a ReadResourceRequest
		 */
		private BiFunction<McpTransportContext, McpSchema.ReadResourceRequest, Mono<McpSchema.ReadResourceResult>> readHandler;

		static AsyncResourceSpecification fromSync(SyncResourceSpecification resource, boolean immediateExecution) {
			// FIXME: This is temporary, proper validation should be implemented
			if (resource == null) {
				return null;
			}
			return new AsyncResourceSpecification(
					resource.resource(),
					(ctx, req) -> {
						var resourceResult = Mono.fromCallable(() -> resource.readHandler().apply(ctx, req));
						return immediateExecution
								? resourceResult
								: resourceResult.subscribeOn(Schedulers.boundedElastic());
					});
		}
	}

	/**
	 * Specification of a prompt template with its asynchronous handler function. Prompts provide structured templates for AI model interactions, supporting consistent message formatting, parameter substitution, context injection, response formatting, and instruction templating
	 */
	@AllArgsConstructor
	@NoArgsConstructor
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class AsyncPromptSpecification {

		/**
		 * The prompt definition including name and description
		 */
		private McpSchema.Prompt prompt;

		/**
		 * The function that processes prompt requests and returns formatted templates. The function's argument is a GetPromptRequest
		 */
		private BiFunction<McpTransportContext, McpSchema.GetPromptRequest, Mono<McpSchema.GetPromptResult>> promptHandler;

		static AsyncPromptSpecification fromSync(SyncPromptSpecification prompt, boolean immediateExecution) {
			// FIXME: This is temporary, proper validation should be implemented
			if (prompt == null) {
				return null;
			}
			return new AsyncPromptSpecification(prompt.prompt(), (ctx, req) -> {
				var promptResult = Mono.fromCallable(() -> prompt.promptHandler().apply(ctx, req));
				return immediateExecution ? promptResult : promptResult.subscribeOn(Schedulers.boundedElastic());
			});
		}
	}

	/**
	 * Specification of a completion handler function with asynchronous execution support. Completions generate AI model outputs based on prompt or resource references and user-provided arguments. This abstraction enables customizable response generation logic, parameter-driven template expansion, and dynamic interaction with connected clients
	 */
	@AllArgsConstructor
	@NoArgsConstructor
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class AsyncCompletionSpecification {

		/**
		 * The unique key representing the completion reference
		 */
		private McpSchema.CompleteReference referenceKey;

		/**
		 * The asynchronous function that processes completion requests and returns results. The function's argument is a McpSchema.CompleteRequest
		 */
		private BiFunction<McpTransportContext, McpSchema.CompleteRequest, Mono<McpSchema.CompleteResult>> completionHandler;

		/**
		 * Converts a synchronous SyncCompletionSpecification into an AsyncCompletionSpecification by wrapping the handler in a bounded elastic scheduler for safe non-blocking execution
		 * @param completion the synchronous completion specification
		 * @param immediateExecution whether to execute immediately or offload to scheduler
		 * @return an asynchronous wrapper of the provided sync specification, or null if input is null
		 */
		static AsyncCompletionSpecification fromSync(SyncCompletionSpecification completion, boolean immediateExecution) {
			if (completion == null) {
				return null;
			}
			return new AsyncCompletionSpecification(completion.referenceKey(), (ctx, req) -> {
				var completionResult = Mono.fromCallable(() -> completion.completionHandler().apply(ctx, req));
				return immediateExecution ? completionResult
						: completionResult.subscribeOn(Schedulers.boundedElastic());
			});
		}
	}

	/**
	 * Specification of a tool with its synchronous handler function. Tools are the primary way for MCP servers to
	 * expose functionality to AI models.
	 */
	@AllArgsConstructor
	@NoArgsConstructor
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class SyncToolSpecification {

		/**
		 * The tool definition including name, description, and parameter schema
		 */
		private McpSchema.Tool tool;

		/**
		 * The function that implements the tool's logic, receiving a CallToolRequest and returning results.
		 */
		private BiFunction<McpTransportContext, CallToolRequest, McpSchema.CallToolResult> callHandler;
	}

	/**
	 * Specification of a resource with its synchronous handler function. Resources provide context to AI models by
	 * exposing data such as:
	 * <ul>
	 * <li>File contents
	 * <li>Database records
	 * <li>API responses
	 * <li>System information
	 * <li>Application state
	 * </ul>
	 */
	@AllArgsConstructor
	@NoArgsConstructor
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class SyncResourceSpecification {

		/**
		 * The resource definition including name, description, and MIME type
		 */
		private McpSchema.Resource resource;

		/**
		 * The function that handles resource read requests. The function's argument is a ReadResourceRequest
		 */
		private BiFunction<McpTransportContext, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> readHandler;
	}

	/**
	 * Specification of a prompt template with its synchronous handler function. Prompts provide structured templates
	 * for AI model interactions, supporting:
	 * <ul>
	 * <li>Consistent message formatting
	 * <li>Parameter substitution
	 * <li>Context injection
	 * <li>Response formatting
	 * <li>Instruction templating
	 * </ul>
	 */
	@AllArgsConstructor
	@NoArgsConstructor
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class SyncPromptSpecification {

		/**
		 * The prompt definition including name and description
		 */
		private McpSchema.Prompt prompt;

		/**
		 * The function that processes prompt requests and returns formatted templates. The function's argument is a
		 * GetPromptRequest
		 */
		private BiFunction<McpTransportContext, McpSchema.GetPromptRequest, McpSchema.GetPromptResult> promptHandler;
	}

	/**
	 * Specification of a completion handler function with synchronous execution support.
	 * Completions generate AI model outputs based on prompt or resource references and
	 * user-provided arguments, enabling customizable response generation logic.
	 */
	@AllArgsConstructor
	@NoArgsConstructor
	@Accessors(fluent = true)
	@Getter
	@EqualsAndHashCode
	public static class SyncCompletionSpecification {

		/**
		 * The unique key representing the completion reference
		 */
		private McpSchema.CompleteReference referenceKey;

		/**
		 * The synchronous function that processes completion requests and returns results. The function's argument is a McpSchema.CompleteRequest
		 */
		private BiFunction<McpTransportContext, McpSchema.CompleteRequest, McpSchema.CompleteResult> completionHandler;
	}
}
