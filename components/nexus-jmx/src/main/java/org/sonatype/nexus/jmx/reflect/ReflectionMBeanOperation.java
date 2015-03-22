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
import java.util.Arrays;

import javax.management.Descriptor;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;

import org.sonatype.nexus.jmx.MBeanOperation;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.base.Supplier;
import com.thoughtworks.paranamer.BytecodeReadingParanamer;
import com.thoughtworks.paranamer.Paranamer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Reflection {@link MBeanOperation}.
 *
 * @since 3.0
 */
public class ReflectionMBeanOperation
  extends ComponentSupport
  implements MBeanOperation
{
  private final MBeanOperationInfo info;

  private final Supplier target;

  private final Method method;

  public ReflectionMBeanOperation(final MBeanOperationInfo info,
                                  final Supplier target,
                                  final Method method)
  {
    this.info = checkNotNull(info);
    this.target = checkNotNull(target);
    this.method = checkNotNull(method);
  }

  @Override
  public MBeanOperationInfo getInfo() {
    return info;
  }

  @Override
  public String getName() {
    return info.getName();
  }

  public Supplier getTarget() {
    return target;
  }

  public Method getMethod() {
    return method;
  }

  private Object target() {
    Object result = target.get();
    checkState(result != null);
    return result;
  }

  @Override
  public Object invoke(final Object[] params) throws Exception {
    log.debug("Invoke: {} -> {}", Arrays.asList(params), method);
    return method.invoke(target(), params);
  }

  //
  // Builder
  //

  /**
   * {@link ReflectionMBeanOperation} builder.
   */
  public static class Builder
    extends ComponentSupport
  {
    private String name;

    private String description;

    private Supplier target;

    private Method method;

    private int impact = MBeanOperationInfo.UNKNOWN;

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

    public Builder method(final Method method) {
      this.method = method;
      return this;
    }

    public Builder impact(final int impact) {
      this.impact = impact;
      return this;
    }

    public ReflectionMBeanOperation build() {
      checkState(target != null);
      checkState(method != null);

      // default to method-name if not provided
      if (name == null) {
        name = method.getName();
      }

      MBeanOperationInfo info = new MBeanOperationInfo(
          name,
          description,
          signature(method),
          method.getReturnType().getName(),
          impact,
          descriptor(method)
      );

      return new ReflectionMBeanOperation(info, target, method);
    }

    //
    // Helpers
    //

    private MBeanParameterInfo[] signature(final Method method) {
      Paranamer paranamer = new BytecodeReadingParanamer();
      String[] names = paranamer.lookupParameterNames(method);
      Class[] types = method.getParameterTypes();

      MBeanParameterInfo[] result = new MBeanParameterInfo[names.length];
      for (int i=0; i< names.length; i++) {
        // TODO: generate descriptor
        Descriptor descriptor = null;

        // TODO: detect description
        String description = null;

        result[i] = new MBeanParameterInfo(
            names[i],
            types[i].getName(),
            description,
            descriptor
        );
      }

      return result;
    }

    private Descriptor descriptor(final Method method) {
      // TODO: generate descriptor
      return null;
    }
  }
}
