package org.basex.query.func;

import static java.lang.String.format;
import static org.basex.query.QueryError.*;
import static org.basex.query.func.Function.*;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.basex.*;
import org.basex.util.*;
import org.junit.jupiter.api.*;

/**
 * This class tests the functions of the Process Module.
 *
 * @author BaseX Team 2005-24, BSD License
 * @author Christian Gruen
 */
public final class ProcModuleTest extends SandboxTest {
  /** Test method. */
  @Test public void execute() {
    final Function func = _PROC_EXECUTE;
    // queries
    query("exists(" + func.args("java", "x") + "/code)", true);
    query("exists(" + func.args("a b c") + "/error)", true);
    query("empty(" + func.args("a b c") + "/(output, code))", true);

    error(func.args("java", "-version", " map { 'encoding': 'xx' }"), PROC_ENCODING_X);
  }

  /** Test method. */
  @Test public void fork() {
    final Function func = _PROC_FORK;
    // queries
    query(func.args("java", "-version"), "");
    query(func.args("a b c"), "");
  }

  /** Test method. */
  @Test public void property() {
    final Function func = _PROC_PROPERTY;
    // queries
    query(func.args("path.separator"), File.pathSeparator);

    Prop.put("A", "B");
    try {
      query(func.args("A"), "B");
      query(func.args("XYZ"), "");
    } finally {
      Prop.clear();
    }
  }

  /** Test method. */
  @Test public void propertyNames() {
    final Function func = _PROC_PROPERTY_NAMES;
    // queries
    // checks if all system properties exist (i.e., have a value)
    query(func.args() + "[empty(" + _PROC_PROPERTY.args(" .") + ")]", "");

    Prop.put("A", "B");
    try {
      query(func.args() + "[. = 'A']", "A");
      query(func.args() + "[. = 'XYZ']", "");
    } finally {
      Prop.clear();
    }
  }

  /** Test method. */
  @Test public void system() {
    final Function func = _PROC_SYSTEM;
    // queries
    query(func.args("java", "-version"), "");
    query("try { " + func.args("java", "x") + "} catch proc:* { 'error' }", "error");

    error(func.args("a b c"), PROC_ERROR_X);

    // test encoding option
    error(func.args("java", "-version", " map { 'encoding': 'xx' }"), PROC_ENCODING_X);
    query(func.args("java",
                    format(" ('-cp', 'target/test-classes', '%s', 'UTF-16')", writeEnc.class.getName()),
                    " map { 'encoding': 'UTF-16'}"),
            "\u03b1\n");
    query(func.args("java",
                    format(" ('-cp', 'target/test-classes', '%s', 'UTF-8')", writeEnc.class.getName()),
                    " map { 'encoding': 'UTF-8'}"),
            "\u03b1\n");

    // test environment option
    query(func.args("java",
                    format(" ('-cp', 'target/test-classes', '%s')", env.class.getName()),
                    " map { 'environment': map { 'FOO': 'bar' }}"),
            "FOO=bar\n");

    // test timeout option
    error(func.args("java",
                    format(" ('-cp', 'target/test-classes', '%s', '2')", sleep.class.getName()),
                    " map { 'timeout': '1'}"),
            PROC_TIMEOUT);
    query(func.args("java",
                  format(" ('-cp', 'target/test-classes', '%s', '0')", sleep.class.getName()),
                  " map { 'timeout': '10'}"),
            "");
 
    // test dir option
    Path dir = FileSystems.getDefault().getRootDirectories().iterator().next();
    Path testClassPath = Path.of("target/test-classes").toAbsolutePath();
    assert !dir.toString().contains("'");
    assert !testClassPath.toString().contains("'");
    query(func.args("java",
                    format(" ('-cp', '%s', '%s')", testClassPath, pwd.class.getName()),
                    format(" map { 'dir': '%s' }", dir)),
            dir + "\n");
  }

  // used as external process in proc:system tests.
  public static class writeEnc {
    public static void main(String[] args) throws InterruptedException, UnsupportedEncodingException {
      String enc = args[0];
      System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, enc));
      // Greek Small Letter Alpha
      System.out.println("\u03b1");
    }
  }

  // used as external process in proc:system tests.
  public static class env {
    public static void main(String[] args) {

      Set<String> ignoreVars = new HashSet<>();

      // this may be set on every process in macos
      ignoreVars.add("__CF_USER_TEXT_ENCODING");

      // this may be set on every process in Windows
      ignoreVars.add("SystemRoot");

      for (Map.Entry<String, String> e : System.getenv().entrySet()) {
        if (ignoreVars.contains(e.getKey())) {
          continue;
        }
        System.out.println(e.getKey() + "=" + e.getValue());
      }
    }
  }

  // used as external process in proc:system tests.
  public static class pwd {
    public static void main(String[] args) {
      System.out.println(System.getProperty("user.dir"));
    }
  }

  // used as external process in proc:system tests.
  public static class sleep {
    public static void main(String[] args) throws InterruptedException {
      Thread.sleep(1000 * Long.parseLong(args[0]));
    }
  }
}
