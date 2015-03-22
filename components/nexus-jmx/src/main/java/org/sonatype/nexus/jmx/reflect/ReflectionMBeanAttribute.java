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

import javax.annotation.Nullable;
import javax.management.Descriptor;
import javax.management.ImmutableDescriptor;
import javax.management.MBeanAttributeInfo;

import org.sonatype.nexus.jmx.MBeanAttribute;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.base.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Reflection {@link MBeanAttribute}.
 *
 * @since 3.0
 */
public class ReflectionMBeanAttribute
  extends ComponentSupport
  implements MBeanAttribute
{
  private final MBeanAttributeInfo info;

  private final Supplier target;

  private final Method getter;

  private final Method setter;

  public ReflectionMBeanAttribute(final MBeanAttributeInfo info,
                                  final Supplier target,
                                  final @Nullable Method getter,
                                  final @Nullable Method setter)
  {
    this.info = checkNotNull(info);
    this.target = checkNotNull(target);
    this.getter = getter;
    this.setter = setter;
  }

  @Override
  public MBeanAttributeInfo getInfo() {
    return info;
  }

  @Override
  public String getName() {
    return info.getName();
  }

  public Supplier getTarget() {
    return target;
  }

  public Method getGetter() {
    return getter;
  }

  public Method getSetter() {
    return setter;
  }

  private Object target() {
    Object result = target.get();
    checkState(result != null);
    return result;
  }

  @Override
  public Object getValue() throws Exception {
    checkState(getter != null);
    log.debug("Get value: {}", getter);
    //noinspection ConstantConditions
    return getter.invoke(target());
  }

  @Override
  public void setValue(final Object value) throws Exception {
    checkState(setter != null);
    log.debug("Set value: {} -> {}", value, setter);
    //noinspection ConstantConditions
    setter.invoke(target(), value);
  }

  //
  // Builder
  //

  /**
   * {@link ReflectionMBeanAttribute} builder.
   */
  public static class Builder
    extends ComponentSupport
  {
    private String name;

    private String description;

    private Supplier target;

    private Method getter;

    private Method setter;

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder description(final String description) {
      this.description = description;
      return this;
    }

    public Builder target(final Supplier target) {
      this.target = target;
      return this;
    }

    public Builder getter(final Method getter) {
      this.getter = getter;
      return this;
    }

    public Builder setter(final Method setter) {
      this.setter = setter;
      return this;
    }

    public ReflectionMBeanAttribute build() {
      checkState(name != null);
      checkState(target != null);
      checkState(getter != null || setter != null);

      MBeanAttributeInfo info = new MBeanAttributeInfo(
          name,
          attributeType(getter, setter).getName(),
          description,
          getter != null, // readable
          setter != null, // writable
          isIs(getter),
          ImmutableDescriptor.union(descriptor(getter), descriptor(setter))
      );

      return new ReflectionMBeanAttribute(info, target, getter, setter);
    }

    //
    // Helpers
    //

    private Class attributeType(final Method getter, final Method setter) {
      // TODO: sanity check methods
      if (getter != null) {
        return getter.getReturnType();
      }
      else {
        return setter.getParameterTypes()[0];
      }
    }

    private boolean isIs(final Method getter) {
      // TODO: sanity check method
      return getter != null && getter.getName().startsWith("is");
    }

    private Descriptor descriptor(final Method method) {
      // TODO: generate descriptor
      return null;
    }
  }
}
