package jumble.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;

/**
 * A class which performs a similar funtion to the RTSI class, but using BCEL
 * instead of reflection. Has a slightly different API to make it more useful
 * for use with Jumble.
 * 
 * @author Tin Pavlinic
 * 
 */
public class BCELRTSI {
  public final static boolean DEBUG = false;
  
  private final static String CLASSPATH = System.getProperty("java.class.path");

  private final static String PS = System.getProperty("path.separator");

  private final static String FS = System.getProperty("file.separator");

  /**
   * Gets a collection of strings representing the names of all the classes that
   * are visible in the class path.
   * 
   * @return all the visible classes.
   */
  public static Collection getAllClasses() {
    return getAllClasses(null);
  }

  /**
   * Gets a collection of strings representing the names of all the classes that
   * are visible in the class path and are part of the specified package.
   * 
   * @param packageName
   *          the name of the package from which to collect classes.
   * 
   * @return all the visible classes in the package.
   */
  public static Collection getAllClasses(String packageName) {
    Collection ret = new HashSet();
    for (StringTokenizer tokens = new StringTokenizer(CLASSPATH, PS); tokens
        .hasMoreTokens();) {
      String curPath = tokens.nextToken();

      if (curPath.toLowerCase().endsWith(".jar")) {
        ret.addAll(getClassesFromJar(packageName, curPath));
      } else if (new File(curPath).isDirectory()) {
        ret.addAll(getClassesFromDir(packageName, curPath));
      } else {
        // Invalid classpath entry, ignore
      }
    }
    return ret;
  }

  /**
   * Gets a collection of strings representing the names of all the classes that
   * are visible in the class path and are derived from the specified class.
   * 
   * @param superclassName
   *          the name of the superclass.
   * @return all the visible classes deriving from the superclass.
   */
  public static Collection getAllDerivedClasses(String superclassName) {
    Collection c = filterSuperclass(getAllClasses(), superclassName);
    c.remove(superclassName);
    return c;
  }

  /**
   * Gets a collection of strings representing the names of all the classes that
   * are visible in the class path which are derived from the specified class
   * and are part of the given package.
   * 
   * @param superclassName
   *          the name of the superclass.
   * @param packageName
   *          the name of the package.
   * @return all the visible classes deriving from the superclass in the
   *         package.
   */
  public static Collection getAllDerivedClasses(String superclassName,
      String packageName) {
    Collection c = filterSuperclass(getAllClasses(packageName), superclassName);
    c.remove(superclassName);
    return c;
  }

  /**
   * Filters only the classes which have a given superclass from a collection.
   * 
   * @param classes
   *          original class names to filter.
   * @param superclassName
   *          superclass name.
   * @return the filtered collection of class names.
   */
  private static Collection filterSuperclass(Collection classes,
      String superclassName) {
    //The BCEL produces output on stderr which doesn't matter in our
    //case. Eat up the output and only ouput it if DEBUG is on
    PrintStream oldErr = System.err;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(baos);
    
    Collection ret = new HashSet();
    
    JavaClass superclass = Repository.lookupClass(superclassName);
    
    assert superclass != null;
    for (Iterator it = classes.iterator(); it.hasNext();) {
      String className = (String) it.next();
      JavaClass clazz = null;
      //hijack err here
      System.setErr(err);
      clazz = Repository.lookupClass(className);
      System.setErr(oldErr);
      if (baos.toString().length() > 0) {
        if (DEBUG) {
          System.err.print(baos);
        }
        baos.reset();
      }
      assert clazz != null;
     
      try {
        //hijack err here
        System.setErr(err);
        if (instanceOf(clazz, superclass)) {
          ret.add(clazz.getClassName());
        }
        System.setErr(oldErr);
        if (baos.toString().length() > 0) {
          if (DEBUG) {
            System.err.print(baos);
          }
          baos.reset();
        }
        
      } catch (Exception e) {
        System.err.println(clazz.getClassName() + " : "
            + superclass.getClassName());
      }
    }
    return ret;
  }

  /**
   * Gets the classes in the package from the jar.
   * 
   * @param packageName
   *          the package.
   * @param filename
   *          the jar file.
   * @return the classes in the package in the jar file.
   */
  private static Collection getClassesFromJar(String packageName,
      String filename) {
    try {
      Collection ret = new HashSet();
      JarFile jar = new JarFile(filename);

      for (Enumeration e = jar.entries(); e.hasMoreElements();) {
        JarEntry entry = (JarEntry) e.nextElement();

        if (entry.isDirectory()) {
          continue;
        } else if (!entry.getName().endsWith(".class")) {
          continue;
        } else {
          String className = entry.getName().replace('/', '.').substring(0,
              entry.getName().length() - 6);
          if (packageName != null) {
            if (className.startsWith(packageName + ".")) {
              ret.add(className);
            }
          } else {
            ret.add(className);
          }
        }
      }
      return ret;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Picks up all the classes in a directory.
   * 
   * @param packageName
   *          the package name null if no package.
   * @param filename
   *          the directory path.
   * @return collection of classes in the directory.
   */
  private static Collection getClassesFromDir(String packageName,
      String filename) {
    if (packageName == null) {
      // hard case. need to recurse through the directory structure
      // picking everything up
      return recursePackages(filename, new File(filename));
    } else {
      // easy case - go to the package location and get all the files
      String finalDir = filename + FS + packageName.replace('.', FS.charAt(0));
      File dir = new File(finalDir);

      if (!dir.exists()) {
        System.err.println("NOT FOUND: " + dir.getAbsolutePath());
        return new HashSet();
      }

      Collection ret = new HashSet();
      File[] files = dir.listFiles(new ClassFileFilter());
      for (int i = 0; i < files.length; i++) {
        // Lop off .class
        String className = files[i].getName().substring(0,
            files[i].getName().length() - 6);

        if (!packageName.equals("")) {
          ret.add(packageName + "." + className);
        } else {
          ret.add(className);
        }
      }

      return ret;
    }
  }

  /**
   * Recursively goes through the directory picking up classes.
   * 
   * @param baseDirName
   *          name of base directory in class path.
   * @param currentDir
   *          the directory we are currently in
   * @return collection of classes.
   */
  private static Collection recursePackages(String baseDirName, File currentDir) {
    Collection ret = new HashSet();

    File[] dirs = currentDir.listFiles(new DirectoryFilter());
    File[] classes = currentDir.listFiles(new ClassFileFilter());

    for (int i = 0; i < classes.length; i++) {
      String className = classes[i].getAbsolutePath();
      assert className.startsWith(baseDirName);
      className = className.substring(baseDirName.length() + 1);
      assert !className.startsWith(FS);
      className = className.replace(FS.charAt(0), '.').substring(0,
          className.length() - 6);
      ret.add(className);
    }

    for (int i = 0; i < dirs.length; i++) {
      ret.addAll(recursePackages(baseDirName, dirs[i]));
    }
    return ret;
  }

  /**
   * Replacement of BCEL's instanceOf operation. Essentially, we want to be
   * quieter about errors than they are.
   * 
   * @param a
   *          class A.
   * @param b
   *          class B.
   * @return true if A inherits from B, false otherwise.
   */
  public static boolean instanceOf(JavaClass a, JavaClass b) {
    try {
      if (a.getClassName().equals(b.getClassName())) {
        return true;
      }
  
      JavaClass[] superclasses;
      if (b.isInterface()) {
        superclasses = a.getAllInterfaces();
      } else {
        superclasses = a.getSuperClasses();
      }
  
      for (int i = 0; i < superclasses.length; i++) {
        if (superclasses[i].getClassName().equals(b.getClassName())) {
          return true;
        }
      }
      return false;
    } catch (Throwable e) {
      if (DEBUG) {
        System.err.println("CHECKING " + a.getClassName() + " vs "
            + b.getClassName());
        e.printStackTrace();
      }
      return false;
    }
  }
}

class ClassFileFilter implements FileFilter {
  public boolean accept(File f) {
    return f.getName().endsWith(".class");
  }
}

class DirectoryFilter implements FileFilter {
  public boolean accept(File f) {
    return f.isDirectory();
  }
}