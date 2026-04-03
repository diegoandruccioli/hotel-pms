/**
 * Swagger UI aggregation configuration for the api-gateway.
 *
 * <p>
 * The multi-service dropdown is configured entirely in {@code application.yml}
 * under the {@code springdoc.swagger-ui.urls} key. This is the correct approach
 * for a Spring Cloud Gateway (WebFlux / reactive) context.
 *
 * <p>
 * A Java {@code @Bean} that injects {@code SwaggerUiConfigProperties} would
 * fail in a reactive context because that class belongs to the servlet-stack
 * API
 * ({@code springdoc-openapi-starter-webmvc-ui}) and is not available on the
 * WebFlux classpath ({@code springdoc-openapi-starter-webflux-ui}).
 */
package com.hotelpms.gateway.config;
