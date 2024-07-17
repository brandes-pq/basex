package org.basex.query.func;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.basex.query.QueryError.*;
import static org.basex.query.func.Function.*;

import org.basex.query.QueryError;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;

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
    query(proc(writeEnc.class, "UTF-16"), "'encoding': 'UTF-16'", "\u03b1\n", func);
    query(proc(writeEnc.class, "UTF-8"), "'encoding': 'UTF-8'", "\u03b1\n", func);

    // test environment option
    query(proc(env.class), "'environment': map { 'FOO': 'bar' }", "FOO=bar\n", func);

    // test timeout option
    error(proc(sleep.class, "2"), "'timeout': 1", PROC_TIMEOUT, func);
    query(proc(sleep.class, "0"), "'timeout': 10", "", func);

    // test dir option
    Path dir = FileSystems.getDefault().getRootDirectories().iterator().next();
    assert !dir.toString().contains("'");
    query(proc(pwd.class), "'dir': '" + dir + "'", dir + "\n", func);
    error(proc(pwd.class), "'dir': ''", PROC_ERROR_X, func);
  }

  static class ProcDescr {
    final Class<?> mainClass;
    final String[] args;

    public ProcDescr(Class<?> mainClass, String[] args) {
      assert Arrays.stream(args).noneMatch(a -> a.contains("'"));
      this.mainClass = mainClass;
      this.args = args;
    }
  }

  /**
   * Describe a call to an external java process
   *
   * @param mainClass must be defined in src/test/java and include a main method
   * @param args      arguments must not contain an apostrophe
   */
  private static ProcDescr proc(Class<?> mainClass, String... args) {
    return new ProcDescr(mainClass, args);
  }

  private static void query(ProcDescr proc, String options, String expected, Function func) {
    String query = buildQuery(proc, options, func);
    query(query, expected);
  }

  private static void error(ProcDescr proc, String options, QueryError expected, Function func) {
    String query = buildQuery(proc, options, func);
    error(query, expected);
  }

  /**
   * Build a query that runs a Java class as external process with proc:system/execute/fork
   *
   * @param process     the process to start
   * @param options     will be wrapped with "map { ... }"
   * @param func        the xquery function to use
   */
  private static String buildQuery(ProcDescr process, String options, Function func) {
    Path classPath = Path.of("target/test-classes").toAbsolutePath();
    assert !classPath.toString().contains("'");

    List<String> javaArgs = new ArrayList<>(List.of(
            "-cp", classPath.toString(),
            process.mainClass.getName()
    ));
    javaArgs.addAll(List.of(process.args));

    String args = format(" ( %s )",
            javaArgs.stream().map(a -> format("'%s'", a)).collect(joining(", ")));

    return options.isEmpty() ? func.args("java", args) :
            func.args("java", args, format(" map { %s }", options));
  }

  /**
   * write the letter "alpha" to stdout; the first argument is the output encoding
   */
  public static class writeEnc {
    public static void main(String[] args) throws InterruptedException, UnsupportedEncodingException {
      String enc = args[0];
      System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, enc));
      // Greek Small Letter Alpha
      System.out.println("\u03b1");
    }
  }

  /** dump the environment to stdout */
  public static class env {
    public static void main(String[] args) {

      Set<String> ignoreVars = new HashSet<>();

      // this may be set on every process in macos
      ignoreVars.add("__CF_USER_TEXT_ENCODING");

      // this may be set on every process in Windows
      ignoreVars.add("SystemRoot");

      for (Map.Entry<String, String> e : System.getenv().entrySet()) {
        if (!ignoreVars.contains(e.getKey())) {
          System.out.println(e.getKey() + "=" + e.getValue());
        }
      }
    }
  }

  /** write the current working directory to stdout */
  public static class pwd {
    public static void main(String[] args) {
      System.out.println(System.getProperty("user.dir"));
    }
  }

  /** sleep for the number of seconds given in the first argument */
  public static class sleep {
    public static void main(String[] args) throws InterruptedException {
      Thread.sleep(1000 * Long.parseLong(args[0]));
    }
  }
}
