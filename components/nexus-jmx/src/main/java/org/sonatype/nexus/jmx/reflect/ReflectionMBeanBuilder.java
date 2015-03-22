/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

package org.sonatype.nexus.jmx.reflect;

import java.lang.reflect.Method;
import java.util.Map;

import javax.annotation.Nullable;

import org.sonatype.nexus.jmx.MBeanBuilder;
import org.sonatype.nexus.jmx.ManagedAttribute;
import org.sonatype.nexus.jmx.ManagedObject;
import org.sonatype.nexus.jmx.ManagedOperation;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Reflection {@link MBeanBuilder}.
 *
 * @see ReflectionMBeanAttribute
 * @see ReflectionMBeanOperation
 * @since 3.0
 */
public class ReflectionMBeanBuilder
    extends MBeanBuilder
{
  private Supplier target;

  public ReflectionMBeanBuilder(final String className) {
    super(className);
  }

  public ReflectionMBeanBuilder target(final Supplier target) {
    this.target = target;
    return this;
  }

  /**
   * Discover managed attributes and operations for the given type.
   *
   * Target must have been previously configured.
   */
  public ReflectionMBeanBuilder discover(final Class<?> type) throws Exception {
    checkNotNull(target);

    log.trace("Discovering managed members of type: {}", type);

    ManagedObject descriptor = type.getAnnotation(ManagedObject.class);
    assert descriptor != null;

    // track attribute builders for getter/setter correlation
    Map<String,ReflectionMBeanAttribute.Builder> attributeBuilders = Maps.newHashMap();

    // discover attributes and operations
    for (Method method : type.getMethods()) {
      // skip non-manageable methods
      if (method.isBridge() || method.isSynthetic()) {
        continue;
      }

      log.trace("Scanning for managed annotations on method: {}", method);

      ManagedAttribute attributeDescriptor = type.getAnnotation(ManagedAttribute.class);
      ManagedOperation operationDescriptor = type.getAnnotation(ManagedOperation.class);

      // skip if no configuration
      if (attributeDescriptor == null && operationDescriptor == null) {
        continue;
      }

      // complain if method marked as both attribute and operation
      checkState(attributeDescriptor != null && operationDescriptor != null);

      if (attributeDescriptor != null) {
        // add attribute
        String name = Strings.emptyToNull(attributeDescriptor.name());
        if (name == null) {
          name = attributeName(method);
        }
        boolean getter = isGetter(method);
        boolean setter = isSetter(method);

        // complain if method is not a valid getter or setter
        if (name == null || (!getter && !setter)) {
          log.warn("Invalid attribute getter or setter method: {}", method);
          continue;
        }

        // lookup or create a new attribute builder
        ReflectionMBeanAttribute.Builder builder = attributeBuilders.get(name);
        if (builder == null) {
          builder = new ReflectionMBeanAttribute.Builder()
              .name(name)
              .target(target);

          attributeBuilders.put(name, builder);
        }

        // do not clobber description if set on only one attribute method
        if (Strings.emptyToNull(attributeDescriptor.description()) != null) {
          builder.description(attributeDescriptor.description());
        }

        if (getter) {
          log.trace("Found getter for attribute: {} -> {}", name, method);
          builder.getter(method);
        }
        else {
          log.trace("Found setter for attribute: {} -> {}", name, method);
          builder.setter(method);
        }
      }
      else {
        // add operation
        String name = Strings.emptyToNull(operationDescriptor.name());
        if (name == null) {
          name = method.getName();
        }

        log.trace("Found operation: {} -> {}", name, method);
        operation(new ReflectionMBeanOperation.Builder()
            .name(name)
            .target(target)
            .impact(operationDescriptor.impact())
            .description(Strings.emptyToNull(operationDescriptor.description()))
            .method(method)
            .build());
      }
    }

    return this;
  }

  //
  // Helpers
  //

  /**
   * Is the given method a setter?
   */
  private static boolean isSetter(final Method method) {
    return method.getName().startsWith("set") &&
        method.getParameterTypes().length == 1 &&
        method.getReturnType().equals(Void.TYPE);
  }

  /**
   * Is the given method a getter?
   */
  private static boolean isGetter(final Method method) {
    String name = method.getName();
    return (name.startsWith("get") || name.startsWith("is")) &&
        method.getParameterTypes().length == 0 &&
        !method.getReturnType().equals(Void.TYPE);
  }

  /**
   * Extract attribute name from method.
   */
  @Nullable
  private static String attributeName(final Method method) {
    String name = method.getName();
    if (name.startsWith("is")) {
      return name.substring(2, name.length());
    }
    else if (name.startsWith("get") || name.startsWith("set")) {
      return name.substring(3, name.length());
    }
    return null;
  }
}
