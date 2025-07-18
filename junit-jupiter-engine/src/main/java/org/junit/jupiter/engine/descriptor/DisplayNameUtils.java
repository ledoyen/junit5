/*
 * Copyright 2015-2025 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.engine.descriptor;

import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.DisplayNameGenerator.IndicativeSentences;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.DisplayNameGenerator.Simple;
import org.junit.jupiter.api.DisplayNameGenerator.Standard;
import org.junit.jupiter.engine.config.JupiterConfiguration;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.commons.util.Preconditions;
import org.junit.platform.commons.util.StringUtils;
import org.junit.platform.engine.DiscoveryIssue;
import org.junit.platform.engine.DiscoveryIssue.Severity;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.discovery.DiscoveryIssueReporter;

/**
 * Collection of utilities for working with display names.
 *
 * @since 5.4
 * @see DisplayName
 * @see DisplayNameGenerator
 * @see DisplayNameGeneration
 */
final class DisplayNameUtils {

	/**
	 * Pre-defined standard display name generator instance.
	 */
	private static final DisplayNameGenerator standardGenerator = DisplayNameGenerator.getDisplayNameGenerator(
		Standard.class);

	/**
	 * Pre-defined simple display name generator instance.
	 */
	private static final DisplayNameGenerator simpleGenerator = DisplayNameGenerator.getDisplayNameGenerator(
		Simple.class);

	/**
	 * Pre-defined display name generator instance replacing underscores.
	 */
	private static final DisplayNameGenerator replaceUnderscoresGenerator = DisplayNameGenerator.getDisplayNameGenerator(
		ReplaceUnderscores.class);

	/**
	 * Pre-defined display name generator instance producing indicative sentences.
	 */
	private static final DisplayNameGenerator indicativeSentencesGenerator = DisplayNameGenerator.getDisplayNameGenerator(
		IndicativeSentences.class);

	static String determineDisplayName(AnnotatedElement element, Supplier<String> displayNameSupplier) {
		Preconditions.notNull(element, "Annotated element must not be null");
		return findAnnotation(element, DisplayName.class) //
				.map(DisplayName::value) //
				.filter(StringUtils::isNotBlank) //
				.map(String::strip) //
				.orElseGet(displayNameSupplier);
	}

	static void validateAnnotation(AnnotatedElement element, Supplier<String> elementDescription,
			Supplier<@Nullable TestSource> sourceProvider, DiscoveryIssueReporter reporter) {
		findAnnotation(element, DisplayName.class) //
				.map(DisplayName::value) //
				.filter(StringUtils::isBlank) //
				.ifPresent(__ -> {
					String message = "@DisplayName on %s must be declared with a non-blank value.".formatted(
						elementDescription.get());
					reporter.reportIssue(
						DiscoveryIssue.builder(Severity.WARNING, message).source(sourceProvider.get()).build());
				});
	}

	static String determineDisplayNameForMethod(Supplier<List<Class<?>>> enclosingInstanceTypes, Class<?> testClass,
			Method testMethod, JupiterConfiguration configuration) {
		return determineDisplayName(testMethod,
			createDisplayNameSupplierForMethod(enclosingInstanceTypes, testClass, testMethod, configuration));
	}

	static Supplier<String> createDisplayNameSupplierForClass(Class<?> testClass, JupiterConfiguration configuration) {
		return createDisplayNameSupplier(Collections::emptyList, testClass, configuration,
			(generator, __) -> generator.generateDisplayNameForClass(testClass));
	}

	static Supplier<String> createDisplayNameSupplierForNestedClass(
			Supplier<List<Class<?>>> enclosingInstanceTypesSupplier, Class<?> testClass,
			JupiterConfiguration configuration) {
		return createDisplayNameSupplier(enclosingInstanceTypesSupplier, testClass, configuration,
			(generator, enclosingInstanceTypes) -> generator.generateDisplayNameForNestedClass(enclosingInstanceTypes,
				testClass));
	}

	private static Supplier<String> createDisplayNameSupplierForMethod(
			Supplier<List<Class<?>>> enclosingInstanceTypesSupplier, Class<?> testClass, Method testMethod,
			JupiterConfiguration configuration) {
		return createDisplayNameSupplier(enclosingInstanceTypesSupplier, testClass, configuration,
			(generator, enclosingInstanceTypes) -> generator.generateDisplayNameForMethod(enclosingInstanceTypes,
				testClass, testMethod));
	}

	private static Supplier<String> createDisplayNameSupplier(Supplier<List<Class<?>>> enclosingInstanceTypesSupplier,
			Class<?> testClass, JupiterConfiguration configuration,
			BiFunction<DisplayNameGenerator, List<Class<?>>, String> generatorFunction) {
		return () -> {
			List<Class<?>> enclosingInstanceTypes = List.copyOf(enclosingInstanceTypesSupplier.get());
			return findDisplayNameGenerator(enclosingInstanceTypes, testClass) //
					.map(it -> generatorFunction.apply(it, enclosingInstanceTypes)) //
					.orElseGet(() -> generatorFunction.apply(configuration.getDefaultDisplayNameGenerator(),
						enclosingInstanceTypes));
		};
	}

	private static Optional<DisplayNameGenerator> findDisplayNameGenerator(List<Class<?>> enclosingInstanceTypes,
			Class<?> testClass) {
		Preconditions.notNull(testClass, "Test class must not be null");

		return findAnnotation(testClass, DisplayNameGeneration.class, enclosingInstanceTypes) //
				.map(DisplayNameGeneration::value) //
				.map(displayNameGeneratorClass -> {
					if (displayNameGeneratorClass == Standard.class) {
						return standardGenerator;
					}
					if (displayNameGeneratorClass == Simple.class) {
						return simpleGenerator;
					}
					if (displayNameGeneratorClass == ReplaceUnderscores.class) {
						return replaceUnderscoresGenerator;
					}
					if (displayNameGeneratorClass == IndicativeSentences.class) {
						return indicativeSentencesGenerator;
					}
					return ReflectionSupport.newInstance(displayNameGeneratorClass);
				});
	}

	private DisplayNameUtils() {
	}

}
