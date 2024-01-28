/* Java-GI - Java language bindings for GObject-Introspection-based libraries
 * Copyright (C) 2022-2024 the Java-GI developers
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see <http://www.gnu.org/licenses/>.
 */

package io.github.jwharm.javagi.gir;

import com.squareup.javapoet.TypeName;

import java.util.List;
import java.util.Map;

public abstract sealed class AnyType extends GirElement permits Type, Array {

    public AnyType(Map<String, String> attributes, List<GirElement> children) {
        super(attributes, children);
    }

    @Override
    public TypedValue parent() {
        return (TypedValue) super.parent();
    }

    public abstract TypeName typeName();

    public String name() {
        String name = attr("name");
        return "GType".equals(name) ? "GLib.Type" : name;
    }

    public String cType() {
        return attr("c:type");
    }

    public boolean isVoid() {
        return "void".equals(cType());
    }
}
