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

package org.sonatype.nexus.jmx;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.management.Descriptor;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;

import org.sonatype.nexus.jmx.MBeanAttribute.Builder;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * {@link MBean} builder.
 *
 * @since 3.0
 */
public class MBeanBuilder
    extends ComponentSupport
{
  private final String className;

  private final List<MBeanAttribute.Builder> attributeBuilders = Lists.newArrayList();

  private final List<MBeanOperation.Builder> operationBuilders = Lists.newArrayList();

  private Supplier target;

  private String description;

  public MBeanBuilder(final String className) {
    this.className = checkNotNull(className);
  }

  public MBeanBuilder target(final Supplier target) {
    this.target = target;
    return this;
  }

  public MBeanBuilder description(final String description) {
    this.description = description;
    return this;
  }

  public MBeanAttribute.Builder attribute() {
    MBeanAttribute.Builder builder = new MBeanAttribute.Builder();
    attributeBuilders.add(builder);
    return builder;
  }

  public MBeanOperation.Builder operation() {
    MBeanOperation.Builder builder = new MBeanOperation.Builder();
    operationBuilders.add(builder);
    return builder;
  }

  /**
   * Discover managed attributes and operations for the given type.
   */
  public MBeanBuilder discover(final Class<?> type) throws Exception {
    log.trace("Discovering managed members of type: {}", type);

    ManagedObject descriptor = type.getAnnotation(ManagedObject.class);
    assert descriptor != null;

    // track attribute builders for getter/setter correlation
    Map<String,Builder> attributeBuilders = Maps.newHashMap();

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
        MBeanAttribute.Builder builder = attributeBuilders.get(name);
        if (builder == null) {
          builder = attribute()
              .name(name);
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
        operation()
            .name(name)
            .description(Strings.emptyToNull(operationDescriptor.description()))
            .method(method);
      }
    }

    return this;
  }

  public MBean build() {
    checkNotNull(target);

    // build attributes and attribute-info
    List<MBeanAttribute> attributes = Lists.newArrayListWithCapacity(attributeBuilders.size());
    List<MBeanAttributeInfo> ainfos = Lists.newArrayListWithCapacity(attributeBuilders.size());
    for (MBeanAttribute.Builder builder : attributeBuilders) {
      builder.target(target);
      MBeanAttribute attribute = builder.build();
      attributes.add(attribute);
      ainfos.add(attribute.getInfo());
    }

    // build operations and operation-info
    List<MBeanOperation> operations = Lists.newArrayListWithCapacity(operationBuilders.size());
    List<MBeanOperationInfo> oinfos = Lists.newArrayListWithCapacity(operationBuilders.size());
    for (MBeanOperation.Builder builder : operationBuilders) {
      builder.target(target);
      MBeanOperation operation = builder.build();
      operations.add(operation);
      oinfos.add(operation.getInfo());
    }

    // TODO: generate descriptor
    Descriptor descriptor = null;

    // TODO: Sort out if we want to support ctor or notification muck
    MBeanConstructorInfo[] cinfos = {};
    MBeanNotificationInfo[] ninfos = {};

    MBeanInfo info = new MBeanInfo(
        className,
        description,
        ainfos.toArray(new MBeanAttributeInfo[ainfos.size()]),
        cinfos,
        oinfos.toArray(new MBeanOperationInfo[oinfos.size()]),
        ninfos,
        descriptor
    );

    return new MBean(info, attributes, operations);
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
