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
/*global Ext, NX, Image*/

/**
 * Main uber mode controller.
 *
 * @since 3.0
 */
Ext.define('NX.controller.Icon', {
  extend: 'Ext.app.Controller',
  requires: [
    'Ext.Error',
    'Ext.util.CSS',
    'NX.util.Url',
    'NX.Icons'
  ],
  mixins: {
    logAware: 'NX.LogAware'
  },

  models: [
    'Icon'
  ],

  stores: [
    'Icon'
  ],

  /**
   * @private
   */
  stylesheet: undefined,

  /**
   * @override
   */
  onLaunch: function() {
    var me = this;

    // install stylesheet after all other controllers have had a chance to init & add icons.
    me.installStylesheet();

    // HACK: preload some additional image resources
    me.preloadImage(NX.util.Url.baseUrl + '/static/rapture/resources/images/shared/icon-error.png');
    me.preloadImage(NX.util.Url.baseUrl + '/static/rapture/resources/images/shared/icon-info.png');
    me.preloadImage(NX.util.Url.baseUrl + '/static/rapture/resources/images/shared/icon-question.png');
    me.preloadImage(NX.util.Url.baseUrl + '/static/rapture/resources/images/shared/icon-warning.png');
  },

  /**
   * @private
   * @param url
   */
  preloadImage: function(url) {
    var me = this,
        img;

    //<if debug>
    me.logDebug('Preloading: ' + url);
    //</if>

    img = new Image();
    img.src = url;
  },

  /**
   * Generate and install stylesheet for icons when the applications is launching.
   *
   * @private
   */
  installStylesheet: function () {
    var me = this,
        styles = [];

    //<if debug>
    me.logDebug('Installing stylesheet');
    //</if>

    // build styles for each icon in store
    me.getIconStore().each(function (record) {
      var img, style = me.buildIconStyle(record.data);
      //me.logDebug('Adding style: ' + style);
      styles.push(style);

      // Optionally pre-load icon
      if (record.data.preload) {
        me.preloadImage(record.data.url);
      }
    });

    // create the style sheet
    me.stylesheet = Ext.util.CSS.createStyleSheet(styles.join(' '), 'nx-icons');

    //<if debug>
    me.logDebug('Stylesheet installed with ' + me.stylesheet.cssRules.length + ' rules');
    //</if>
  },

  /**
   * Build style for given icon.
   *
   * @private
   */
  buildIconStyle: function (icon) {
    var me = this,
        style;

    style = '.' + icon.cls + ' {';
    style += 'background: url(' + icon.url + ') no-repeat center center !important;';
    style += 'height: ' + icon.height + 'px;';
    style += 'width: ' + icon.width + 'px;';
    style += 'vertical-align: middle;';  // needed to get iconCls lined up in trees when height/width is set
    style += '}';

    // TODO: Consider adding *additional* plain style w/o height/width/etc on it?

    return style;
  },

  /**
   * Add new icons.
   *
   * @public
   * @param icons Array or object.
   */
  addIcons: function (icons) {
    var me = this;
    if (Ext.isArray(icons)) {
      Ext.Array.each(icons, function (icon) {
        me.addIcon(icon);
      });
    }
    else if (Ext.isObject(icons)) {
      Ext.Object.each(icons, function (key, value) {
        var copy = Ext.clone(value);
        copy.name = key;
        me.addIcon(copy);
      });
    }
    else {
      Ext.Error.raise('Expected array or object, found: ' + icons);
    }
  },

  /**
   * Add a new icon.
   *
   * @public
   */
  addIcon: function (icon) {
    var me = this;

    // If icon contains 'variants' field then create an icon for each variant
    if (Ext.isArray(icon.variants)) {
      var copy = Ext.clone(icon);
      delete copy.variants;
      Ext.each(icon.variants, function (variant) {
        copy.variant = variant;
        me.addIcon(copy);
      });
      return;
    }

    me.configureIcon(icon);

    // complain if height/width are missing as this could cause the image not to display
    if (!icon.height) {
      me.logWarn('Icon missing height: ' + icon.css);
    }
    if (!icon.width) {
      me.logWarn('Icon missing width: ' + icon.css);
    }

    // TODO: complain if we are overwriting an icon

    me.getIconStore().add(icon);
  },

  /**
   * Apply basic icon configuration.
   *
   * @private
   */
  configureIcon: function (icon) {
    var me = this,
        variant = icon.variant;

    // automatically apply 'x<size>'
    if (Ext.isString(variant)) {
      if (variant.charAt(0) === 'x' && variant.length > 1) {
        var size = Ext.Number.from(variant.substring(1), -1);
        if (size === -1) {
          throw Ext.Error.raise('Invalid variant format: ' + variant);
        }
        icon.height = icon.width = size;
      }
    }

    icon.url = NX.Icons.url2(icon.file, icon.variant);
    icon.cls = NX.Icons.cls(icon.name, icon.variant);
  },

  /**
   * Find an icon by name with optional variant.
   *
   * @public
   */
  findIcon: function (name, variant) {
    var me = this,
        store = me.getIconStore(),
        recordId;

    recordId = store.findBy(function (record, id) {
      // find matching icon name
      if (name === record.get('name')) {
        // if icon has a variant match that too
        if (variant) {
          if (variant === record.get('variant')) {
            return true; // match
          }
        }
      }
      return false; // no match
    });

    if (recordId === -1) {
      return null;
    }
    return store.getAt(recordId);
  }
});
