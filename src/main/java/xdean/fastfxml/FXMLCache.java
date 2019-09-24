/*
 * Copyright 2019 XDean
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xdean.fastfxml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xdean.fastfxml.Util.PackageClass;

class FXMLCache {

  private static final ThreadLocal<FXMLCache> global = new ThreadLocal<FXMLCache>() {
    @Override
    protected FXMLCache initialValue() {
      return new FXMLCache();
    }
  };

  static final FXMLCache getContext() {
    return global.get();
  }

  class ClassLoaderCache {
    final ClassLoader classLoader;
    final Map<String, Map<String, Class<?>>> classPackages = new HashMap<>();

    public ClassLoaderCache(ClassLoader classLoader) {
      this.classLoader = classLoader;
    }

    Class<?> findClass(List<String> packages, String className) {
      String relClassName = Util.subclassName(className);
      Map<String, Class<?>> classes = classPackages.computeIfAbsent(className, k -> new HashMap<>());
      String findPkg = classes.keySet().stream()
          .filter(s -> packages.contains(s))
          .findAny()
          .orElse(null);
      if (findPkg == null) {
        return packages.stream()
            .map(p -> findClass(p, relClassName))
            .filter(c -> c != null)
            .findFirst()
            .orElse(null);
      } else {
        return classes.get(findPkg);
      }
    }

    Class<?> findClass(String name) {
      PackageClass s = Util.splitClassName(name);
      return findClass(s.packageName, s.className);
    }

    private Class<?> findClass(String packageName, String className) {
      Class<?> cache = classPackages.computeIfAbsent(className, k -> new HashMap<>()).get(packageName);
      if (cache != null) {
        return cache;
      }
      try {
        Class<?> result = classLoader.loadClass(packageName + "." + className);
        classPackages.computeIfAbsent(className, k -> new HashMap<>()).put(packageName, result);
        return result;
      } catch (ClassNotFoundException e) {
        return null;
      }
    }
  }

  final Map<ClassLoader, ClassLoaderCache> classLoaders = new HashMap<>();

  ClassLoaderCache getClassLoaderCache(ClassLoader cl) {
    return classLoaders.computeIfAbsent(cl, k -> new ClassLoaderCache(k));
  }
}