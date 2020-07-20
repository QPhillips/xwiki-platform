/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

define([
  BASE_PATH + "displayers/defaultDisplayer.js",
  "polyfills",
], function (
  DefaultDisplayer
) {


  /**
   * Create an HTML displayer for a property of an entry
   * Extends the default displayer class
   */
  var LinkDisplayer = function (propertyId, entryData, logic) {
    DefaultDisplayer.call(this, propertyId, entryData, logic);
  };
  LinkDisplayer.prototype = Object.create(DefaultDisplayer.prototype);
  LinkDisplayer.prototype.constructor = LinkDisplayer;


  /**
   * Create link viewer element for the displayer
   * Override the default inherted method
   */
  LinkDisplayer.prototype.createView = function (params) {
    return new Promise (function (resolve, reject) {

      // default href
      var href = params.config.propertyHref && params.entry[params.config.propertyHref] || "#";

      // create element
      var element = document.createElement("a");
      element.href = href ;
      if (params.value) {
        element.innerText = params.value;
      } else {
        var emptyValue = document.createElement("span");
        emptyValue.className = "explicit-empty-value";
        emptyValue.setAttribute("data-text", "- no title -");
        element.appendChild(emptyValue);
      }

      resolve(element);

    });
  };


  return LinkDisplayer;
});