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

package org.sonatype.nexus.jmx.internal;

import java.util.Hashtable;

import javax.inject.Inject;
import javax.inject.Named;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.sonatype.nexus.jmx.MBean;
import org.sonatype.nexus.jmx.MBeanBuilder;
import org.sonatype.nexus.jmx.ManagedObject;
import org.sonatype.nexus.jmx.ObjectNameEntry;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.inject.Key;
import org.eclipse.sisu.BeanEntry;
import org.eclipse.sisu.EagerSingleton;
import org.eclipse.sisu.Mediator;
import org.eclipse.sisu.inject.BeanLocator;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Handles registration of {@link ManagedObject} components.
 *
 * @since 3.0
 */
@Named
@EagerSingleton
public class ManagedObjectRegistrar
    extends ComponentSupport
{
  private final MBeanServer server;

  @Inject
  public ManagedObjectRegistrar(final BeanLocator beanLocator,
                                final MBeanServer server)
  {
    checkNotNull(beanLocator);
    this.server = checkNotNull(server);

    Key<Object> managedObjectKey = Key.get(Object.class, ManagedObject.class);
    if (managedObjectKey.hasAttributes()) {
      // workaround Guice 'feature' that upgrades annotation class with all default attributes to an instance
      // we want the annotation class without attributes here to match against all @ManagedObject annotations
      managedObjectKey = managedObjectKey.withoutAttributes();
    }
    beanLocator.watch(managedObjectKey, new ManageObjectMediator(), this);
  }

  private class ManageObjectMediator
      implements Mediator<ManagedObject, Object, ManagedObjectRegistrar>
  {
    @Override
    public void add(final BeanEntry<ManagedObject, Object> entry, final ManagedObjectRegistrar watcher)
        throws Exception
    {
      try {
        ObjectName name = objectName(entry);
        log.info("Registering: {} -> {}", name, entry);
        MBean mbean = mbean(entry);
        server.registerMBean(mbean, name);
      }
      catch (Exception e) {
        log.warn("Failed to export: {}; ignoring", entry, e);
      }
    }

    @Override
    public void remove(final BeanEntry<ManagedObject, Object> entry, final ManagedObjectRegistrar watcher)
        throws Exception
    {
      try {
        ObjectName name = objectName(entry);
        log.info("Un-registering: {} -> {}", name, entry);
        server.unregisterMBean(name);
      }
      catch (Exception e) {
        log.warn("Failed to un-export: {}; ignoring", entry, e);
      }
    }
  }

  /**
   * Determine {@link ObjectName} for given {@link BeanEntry}.
   */
  private ObjectName objectName(final BeanEntry<ManagedObject, Object> entry) throws Exception {
    Class<?> type = entry.getImplementationClass();
    ManagedObject descriptor = type.getAnnotation(ManagedObject.class);

    // default domain to package if missing
    String domain = descriptor.domain();
    if (Strings.emptyToNull(domain) == null) {
      domain = type.getPackage().getName();
    }

    Hashtable<String, String> entries = new Hashtable<>();

    // add custom object-name entries
    for (ObjectNameEntry kv : descriptor.entries()) {
      entries.put(kv.name(), kv.value());
    }

    // add class-name as type
    entries.put("type", type.getSimpleName());

    // add binding-name if present
    Named name = type.getAnnotation(Named.class);
    if (name != null) {
      String value = Strings.emptyToNull(name.value());
      if (value != null) {
        entries.put("name", value);
      }
    }

    return new ObjectName(domain, entries);
  }

  /**
   * Construct mbean for given {@link BeanEntry} discovering its attributes and operations.
   */
  private MBean mbean(final BeanEntry<ManagedObject, Object> entry) throws Exception {
    Class<?> type = entry.getImplementationClass();
    ManagedObject descriptor = type.getAnnotation(ManagedObject.class);

    return new MBeanBuilder(type.getName())
        .target(new Supplier()
        {
          @Override
          public Object get() {
            return entry.getValue();
          }
        })
        .description(Strings.emptyToNull(descriptor.description()))
        .discover(type)
        .build();
  }
}
