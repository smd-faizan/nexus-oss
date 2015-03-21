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
import javax.management.ObjectName;

import org.sonatype.nexus.jmx.ManagedObject;
import org.sonatype.nexus.jmx.ManagedProperty;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.base.Strings;
import com.google.inject.Key;
import org.eclipse.sisu.BeanEntry;
import org.eclipse.sisu.EagerSingleton;
import org.eclipse.sisu.Mediator;
import org.eclipse.sisu.inject.BeanLocator;
import org.weakref.jmx.MBeanExporter;

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
  private final MBeanExporter exporter;

  @Inject
  public ManagedObjectRegistrar(final BeanLocator beanLocator,
                                final MBeanExporter exporter)
  {
    checkNotNull(beanLocator);

    System.out.println("\n\nHERE\n\n");

    this.exporter = checkNotNull(exporter);

    Key<Object> managedObjectKey = Key.get(Object.class, ManagedObject.class);
    if (managedObjectKey.hasAttributes()) {
      // workaround Guice 'feature' that upgrades annotation class with all default attributes to an instance
      // we want the annotation class without attributes here to match against all @ManagedObject annotations
      managedObjectKey = managedObjectKey.withoutAttributes();
    }
    beanLocator.watch(managedObjectKey, new ManageObjectMediator(), this);
  }

  private class ManageObjectMediator
    implements Mediator<ManagedObject,Object,ManagedObjectRegistrar>
  {
    @Override
    public void add(final BeanEntry<ManagedObject, Object> entry, final ManagedObjectRegistrar watcher)
        throws Exception
    {
      System.out.println("\n\n\n\n\n");

      log.info("Adding: {}", entry);

      try {
        ObjectName name = name(entry);
        log.info("Exporting: {}", name);
        exporter.export(name, entry.getValue());
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
        ObjectName name = name(entry);
        log.info("Un-exporting: {}", name);
        exporter.unexport(name);
      }
      catch (Exception e) {
        log.warn("Failed to un-export: {}; ignoring", entry, e);
      }
    }
  }

  /**
   * Determine {@link ObjectName} for given {@link BeanEntry}.
   */
  private ObjectName name(final BeanEntry<ManagedObject, Object> entry) throws Exception {
    Class<?> type = entry.getImplementationClass();

    ManagedObject descriptor = type.getAnnotation(ManagedObject.class);
    assert descriptor != null;

    Hashtable<String,String> properties = properties(descriptor);

    // default domain to package if missing
    String domain = descriptor.domain();
    if (Strings.emptyToNull(domain) == null) {
      domain = type.getPackage().getName();
    }

    // add class-name as type
    properties.put("type", type.getSimpleName());

    // add binding-name if present
    //Named name = type.getAnnotation(Named.class);
    //if (name != null) {
    //  properties.put("name", name.value());
    //}

    return new ObjectName(domain, properties);
  }

  /**
   * Extract {@link ObjectName} properties from descriptor.
   */
  private Hashtable<String, String> properties(final ManagedObject descriptor) {
    Hashtable<String,String> result = new Hashtable<>();
    for (ManagedProperty property : descriptor.properties()) {
      result.put(property.name(), property.value());
    }
    return result;
  }
}
