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

interface Util {
  class PackageClass {
    final String packageName;
    final String className;

    PackageClass(String packageName, String className) {
      this.packageName = packageName;
      this.className = className;
    }
  }

  static PackageClass splitClassName(String name) {
    int i = name.indexOf('.');
    int n = name.length();
    while (i != -1 && i < n && Character.isLowerCase(name.charAt(i + 1))) {
      i = name.indexOf('.', i + 1);
    }
    if (i == -1 || i == n) {
      return null;
    }

    String packageName = name.substring(0, i);
    String className = subclassName(name.substring(i + 1));

    return new PackageClass(packageName, className);
  }

  static String subclassName(String className) {
    return className.replace('.', '$');
  }
}
