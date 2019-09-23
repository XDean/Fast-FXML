/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved. DO NOT ALTER OR
 * REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License version 2 only, as published by the Free Software Foundation. Oracle
 * designates this particular file as subject to the "Classpath" exception as provided by Oracle in
 * the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version 2 along with this work;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA or visit www.oracle.com
 * if you need additional information or have any questions.
 */
package xdean.fastfxml;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.javafx.fxml.builder.JavaFXFontBuilder;
import com.sun.javafx.fxml.builder.JavaFXImageBuilder;
import com.sun.javafx.fxml.builder.JavaFXSceneBuilder;
import com.sun.javafx.fxml.builder.ProxyBuilder;
import com.sun.javafx.fxml.builder.TriangleMeshBuilder;
import com.sun.javafx.fxml.builder.URLBuilder;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.NamedArg;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.text.Font;
import javafx.util.Builder;
import javafx.util.BuilderFactory;
import sun.reflect.misc.ConstructorUtil;

/**
 * JavaFX builder factory.
 *
 * @since JavaFX 2.0
 */
@SuppressWarnings({ "restriction", "unchecked", "rawtypes" })
public final class JavaFXBuilderFactory implements BuilderFactory {
  private final JavaFXBuilder NO_BUILDER = new JavaFXBuilder();

  private final Map<Class<?>, JavaFXBuilder> builders = new HashMap<Class<?>, JavaFXBuilder>();

  private final ClassLoader classLoader;
  private final boolean alwaysUseBuilders;

  private final boolean webSupported;

  /**
   * Default constructor.
   */
  public JavaFXBuilderFactory() {
    this(FXMLLoader.getDefaultClassLoader(), false);
  }

  /**
   * @treatAsPrivate This constructor is for internal use only.
   * @deprecated
   */
  @Deprecated
  public JavaFXBuilderFactory(boolean alwaysUseBuilders) {
    // SB-dependency: RT-21230 has been filed to track this
    this(FXMLLoader.getDefaultClassLoader(), alwaysUseBuilders);
  }

  /**
   * Constructor that takes a class loader.
   *
   * @param classLoader
   * @since JavaFX 2.1
   */
  public JavaFXBuilderFactory(ClassLoader classLoader) {
    this(classLoader, false);
  }

  /**
   * @treatAsPrivate This constructor is for internal use only.
   * @since JavaFX 2.1
   * @deprecated
   */
  @Deprecated
  public JavaFXBuilderFactory(ClassLoader classLoader, boolean alwaysUseBuilders) {
    // SB-dependency: RT-21230 has been filed to track this
    if (classLoader == null) {
      throw new NullPointerException();
    }

    this.classLoader = classLoader;
    this.alwaysUseBuilders = alwaysUseBuilders;
    this.webSupported = Platform.isSupported(ConditionalFeature.WEB);
  }

  @Override
  public Builder<?> getBuilder(Class<?> type) {
    Builder<?> builder;

    if (type == Scene.class) {
      builder = new JavaFXSceneBuilder();
    } else if (type == Font.class) {
      builder = new JavaFXFontBuilder();
    } else if (type == Image.class) {
      builder = new JavaFXImageBuilder();
    } else if (type == URL.class) {
      builder = new URLBuilder(classLoader);
    } else if (type == TriangleMesh.class) {
      builder = new TriangleMeshBuilder();
    } else if (scanForConstructorAnnotations(type)) {
      builder = new ProxyBuilder(type);
    } else {
      Builder<Object> objectBuilder = null;
      JavaFXBuilder typeBuilder = builders.get(type);

      if (typeBuilder != NO_BUILDER) {
        if (typeBuilder == null) {
          // We want to retun a builder here
          // only for those classes that reqire it.
          // For now we assume that an object that has a default
          // constructor does not require a builder. This is the case
          // for most platform classes, except those handled above.
          // We may need to add other exceptions to the rule if the need
          // arises...
          //
          boolean hasDefaultConstructor;
          try {
            ConstructorUtil.getConstructor(type, new Class[] {});
            // found!
            // forces the factory to return a builder if there is one.
            // TODO: delete the line below when we are sure that both
            // builders and default constructors are working!
            if (alwaysUseBuilders) {
              throw new Exception();
            }

            hasDefaultConstructor = true;
          } catch (Exception x) {
            hasDefaultConstructor = false;
          }

          // Force the loader to use a builder for WebView even though
          // it defines a default constructor
          if (!hasDefaultConstructor || (webSupported && type.getName().equals("javafx.scene.web.WebView"))) {
            try {
              typeBuilder = createTypeBuilder(type);
            } catch (ClassNotFoundException ex) {
              // no builder... will fail later when the FXMLLoader
              // will try to instantiate the bean...
            }
          }

          builders.put(type, typeBuilder == null ? NO_BUILDER : typeBuilder);

        }
        if (typeBuilder != null) {
          objectBuilder = typeBuilder.createBuilder();
        }
      }

      builder = objectBuilder;
    }

    return builder;
  }

  JavaFXBuilder createTypeBuilder(Class<?> type) throws ClassNotFoundException {
    JavaFXBuilder typeBuilder = null;
    Class<?> builderClass = classLoader.loadClass(type.getName() + "Builder");
    try {
      typeBuilder = new JavaFXBuilder(builderClass);
    } catch (Exception ex) {
      // TODO should be reported
      Logger.getLogger(JavaFXBuilderFactory.class.getName()).log(Level.WARNING,
          "Failed to instantiate JavaFXBuilder for " + builderClass, ex);
    }
    if (!alwaysUseBuilders) {
      Logger.getLogger(JavaFXBuilderFactory.class.getName()).log(Level.FINER, "class {0} requires a builder.", type);
    }
    return typeBuilder;
  }

  private boolean scanForConstructorAnnotations(Class<?> type) {
    Constructor constructors[] = ConstructorUtil.getConstructors(type);
    for (Constructor constructor : constructors) {
      Annotation[][] paramAnnotations = constructor.getParameterAnnotations();
      Parameter[] params = constructor.getParameters();
      for (int i = 0; i < params.length; i++) {
        for (Annotation annotation : paramAnnotations[i]) {
          if (annotation instanceof NamedArg) {
            return true;
          }
        }
      }
    }
    return false;
  }

}
