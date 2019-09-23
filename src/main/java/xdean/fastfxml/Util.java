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
