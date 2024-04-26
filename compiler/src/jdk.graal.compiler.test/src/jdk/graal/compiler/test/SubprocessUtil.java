/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Assume;

import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.services.Services;

/**
 * Utility methods for spawning a VM in a subprocess during unit tests.
 */
public final class SubprocessUtil {

    private SubprocessUtil() {
    }

    /**
     * Gets the command line for the current process.
     *
     * @return the command line arguments for the current process or {@code null} if they are not
     *         available
     */
    public static List<String> getProcessCommandLine() {
        String processArgsFile = System.getenv().get("MX_SUBPROCESS_COMMAND_FILE");
        if (processArgsFile != null) {
            try {
                return Files.readAllLines(new File(processArgsFile).toPath());
            } catch (IOException e) {
            }
        } else {
            Assume.assumeTrue("Process command line unavailable", false);
        }
        return null;
    }

    /**
     * Pattern for a single shell command argument that does not need to quoted.
     */
    private static final Pattern SAFE_SHELL_ARG = Pattern.compile("[A-Za-z0-9@%_\\-\\+=:,\\./]+");

    /**
     * Reliably quote a string as a single shell command argument.
     */
    public static String quoteShellArg(String arg) {
        if (arg.isEmpty()) {
            return "\"\"";
        }
        Matcher m = SAFE_SHELL_ARG.matcher(arg);
        if (m.matches()) {
            return arg;
        }
        // See http://stackoverflow.com/a/1250279
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }

    /**
     * Quote an argument for use in an argfile.
     */
    public static String quoteArgfileArgument(String arg) {
        if (arg.isEmpty()) {
            return "\"\"";
        }
        if (arg.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == '#')) {
            if (arg.indexOf('"') != -1) {
                assert arg.indexOf('\'') == -1 : "unquotable: " + arg;
                return "'" + arg + "'";
            }
            assert arg.indexOf('"') == -1 : "unquotable: " + arg;
            return '"' + arg + '"';
        }
        return arg;
    }

    /**
     * Returns a new copy {@code args} with debugger arguments removed.
     */
    public static List<String> withoutDebuggerArguments(List<String> args) {
        List<String> result = new ArrayList<>(args.size());
        for (String arg : args) {
            if (!(arg.equals("-Xdebug") || arg.startsWith("-Xrunjdwp:") || arg.startsWith("-agentlib:jdwp") || arg.startsWith("-XX:+ShowMessageBoxOnError"))) {
                result.add(arg);
            }
        }
        return result;
    }

    /**
     * Gets the command line options to do the same package opening and exporting specified by the
     * {@code --open-packages} option to the {@code mx unittest} command.
     *
     * Properties defined in {@code com.oracle.mxtool.junit.MxJUnitWrapper}.
     */
    public static List<String> getPackageOpeningOptions() {
        List<String> result = new ArrayList<>();
        String[] actions = {"opens", "exports"};
        for (String action : actions) {
            String opens = System.getProperty("com.oracle.mxtool.junit." + action);
            if (opens != null && !opens.isEmpty()) {
                for (String value : opens.split(System.lineSeparator())) {
                    result.add("--add-" + action + "=" + value);
                }
            }
        }
        return result;
    }

    public static List<String> expandArgFileArgs(List<String> args) {
        try {
            return CommandLine.parse(args);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Gets the command line used to start the current Java VM, including all VM arguments, but not
     * including the main class or any Java arguments. This can be used to spawn an identical VM,
     * but running different Java code.
     *
     * @param removeDebuggerArguments if {@code true}, pre-process the returned list with
     *            {@link #withoutDebuggerArguments(List)}
     * @return {@code null} if the command line for the current process cannot be accessed
     */
    public static List<String> getVMCommandLine(boolean removeDebuggerArguments) {
        List<String> args = getProcessCommandLine();
        if (args == null) {
            return null;
        } else {
            int index = findMainClassIndex(args);
            args = args.subList(0, index);
            if (removeDebuggerArguments) {
                args = withoutDebuggerArguments(args);
            }
            return args;
        }
    }

    /**
     * Shortcut for {@link #getVMCommandLine(boolean) getVMCommandLine(false)}.
     */
    public static List<String> getVMCommandLine() {
        return getVMCommandLine(false);
    }

    /**
     * Detects whether a Java agent matching {@code agentPredicate} is specified in the VM
     * arguments.
     *
     * @param agentPredicate a predicate that is given the value of a {@code -javaagent} VM argument
     */
    public static boolean isJavaAgentAttached(Predicate<String> agentPredicate) {
        return expandArgFileArgs(getVMCommandLine()).stream().//
                        filter(args -> args.startsWith("-javaagent:")).//
                        map(s -> s.substring("-javaagent:".length())).//
                        anyMatch(agentPredicate);
    }

    /**
     * Detects whether a Java agent is specified in the VM arguments.
     */
    public static boolean isJavaAgentAttached() {
        return isJavaAgentAttached(javaAgentValue -> true);
    }

    /**
     * Detects whether the JaCoCo Java agent is specified in the VM arguments.
     */
    public static boolean isJaCoCoAttached() {
        return isJavaAgentAttached(s -> s.toLowerCase().contains("jacoco"));
    }

    /**
     * The details of a subprocess execution.
     */
    public static class Subprocess {

        /**
         * The command line of the subprocess.
         */
        public final List<String> command;

        /**
         * Exit code of the subprocess.
         */
        public final int exitCode;

        /**
         * Output from the subprocess broken into lines.
         */
        public final List<String> output;

        /**
         * Whether the subprocess execution exceeded the supplied timeout.
         */
        public final boolean timedOut;

        /**
         * OS level pid.
         */
        public final long pid;

        /**
         * Explicit environment variables.
         */
        private Map<String, String> env;

        public Subprocess(List<String> command, Map<String, String> env, long pid, int exitCode, List<String> output, boolean timedOut) {
            this.command = command;
            this.env = env;
            this.pid = pid;
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }

        /**
         * Returns the process execution as a string with a header line followed by one or more body
         * lines followed by a trailer with a new line.
         *
         * @see #asString(Map)
         */
        @Override
        public String toString() {
            return asString(null);
        }

        /**
         * Returns the process execution as a string with a header line followed by one or more body
         * lines followed by a trailer with a new line.
         *
         * The header is {@code "----------subprocess[<pid>]:(<lines>/<chars>)----------"} where
         * {@code pid} is the id of the process and {@code chars} and {@code lines} provide the
         * dimensions of the body.
         *
         * The sections in the body are the environment variables (key: "env"), the command line
         * (key: "cmd"), the lines of output produced (key: "output") and the exit code (key:
         * "exitCode").
         *
         * The trailer is {@code "==========subprocess[<pid>]=========="}
         *
         * @param sections selects which sections are in the body. If null, all sections are
         *            included.
         */
        public String asString(Map<String, Boolean> sections) {
            Formatter msg = new Formatter();
            if (include(sections, "env")) {
                if (env != null && !env.isEmpty()) {
                    msg.format("env");
                    for (Map.Entry<String, String> e : env.entrySet()) {
                        msg.format(" %s=%s", e.getKey(), quoteShellArg(e.getValue()));
                    }
                    msg.format("\\%n");
                }
            }
            if (include(sections, "cmd")) {
                msg.format("%s%n", CollectionsUtil.mapAndJoin(command, e -> quoteShellArg(String.valueOf(e)), " "));
            }
            if (include(sections, "output")) {
                for (String line : output) {
                    msg.format("%s%n", line);
                }
            }
            if (include(sections, "exitCode")) {
                msg.format("exit code: %s%n", exitCode);
            }
            String body = msg.toString();
            if (!body.endsWith(System.lineSeparator())) {
                body = body + System.lineSeparator();
            }
            long lines = body.chars().filter(ch -> ch == '\n').count();
            int chars = body.length();
            String head = String.format("----------subprocess[%d]:(%d/%d)----------", pid, lines, chars);
            String tail = String.format("==========subprocess[%d]==========", pid);
            return String.format("%s%n%s%s%n", head, body, tail);
        }

        private static boolean include(Map<String, Boolean> sections, String key) {
            return sections == null || sections.getOrDefault(key, false);
        }
    }

    /**
     * A sentinel value which when present in the {@code vmArgs} parameter for any of the
     * {@code java(...)} methods in this class is replaced with the contents of
     * {@link #getPackageOpeningOptions}.
     */
    public static final String PACKAGE_OPENING_OPTIONS = ";:PACKAGE_OPENING_OPTIONS:;";

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments
     * @param mainClassAndArgs the main class and its arguments
     */
    public static Subprocess java(List<String> vmArgs, String... mainClassAndArgs) throws IOException, InterruptedException {
        return java(vmArgs, Arrays.asList(mainClassAndArgs));
    }

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments
     * @param mainClassAndArgs the main class and its arguments
     */
    public static Subprocess java(List<String> vmArgs, List<String> mainClassAndArgs) throws IOException, InterruptedException {
        return javaHelper(vmArgs, null, null, mainClassAndArgs, null);
    }

    /**
     * Executes a Java subprocess with a timeout.
     *
     * @param vmArgs the VM arguments
     * @param mainClassAndArgs the main class and its arguments
     * @param timeout the timeout duration until the process is killed
     */
    public static Subprocess java(List<String> vmArgs, List<String> mainClassAndArgs, Duration timeout) throws IOException, InterruptedException {
        return javaHelper(vmArgs, null, null, mainClassAndArgs, timeout);
    }

    /**
     * Executes a Java subprocess with a timeout in the specified working directory.
     *
     * @param vmArgs the VM arguments
     * @param workingDir the working directory of the subprocess. If null, the working directory of
     *            the current process is used.
     * @param mainClassAndArgs the main class and its arguments
     * @param timeout the timeout duration until the process is killed
     */
    public static Subprocess java(List<String> vmArgs, File workingDir, List<String> mainClassAndArgs, Duration timeout) throws IOException, InterruptedException {
        return javaHelper(vmArgs, null, workingDir, mainClassAndArgs, timeout);
    }

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments
     * @param env the environment variables
     * @param mainClassAndArgs the main class and its arguments
     */
    public static Subprocess java(List<String> vmArgs, Map<String, String> env, String... mainClassAndArgs) throws IOException, InterruptedException {
        return java(vmArgs, env, Arrays.asList(mainClassAndArgs));
    }

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments
     * @param env the environment variables
     * @param mainClassAndArgs the main class and its arguments
     */
    public static Subprocess java(List<String> vmArgs, Map<String, String> env, List<String> mainClassAndArgs) throws IOException, InterruptedException {
        return javaHelper(vmArgs, env, null, mainClassAndArgs, null);
    }

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments. If {@code vmArgs} contains {@link #PACKAGE_OPENING_OPTIONS},
     *            the argument is replaced with arguments to do package opening (see
     *            {@link SubprocessUtil#getPackageOpeningOptions()}
     * @param env the environment variables
     * @param workingDir the working directory of the subprocess. If null, the working directory of
     *            the current process is used.
     * @param mainClassAndArgs the main class and its arguments
     * @param timeout the duration to wait for the process to finish. If null, the calling thread
     *            waits for the process indefinitely.
     */
    private static Subprocess javaHelper(List<String> vmArgs, Map<String, String> env, File workingDir, List<String> mainClassAndArgs, Duration timeout) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(vmArgs.size());
        for (String vmArg : vmArgs) {
            if (vmArg == PACKAGE_OPENING_OPTIONS) {
                List<String> packageOpeningOptions = getPackageOpeningOptions();
                if (!packageOpeningOptions.isEmpty()) {
                    command.addAll(packageOpeningOptions);
                }
            } else {
                command.add(vmArg);
            }
        }
        command.addAll(mainClassAndArgs);
        return process(command, env, workingDir, timeout);
    }

    private static final Set<String> EXECUTABLES_USING_ARGFILES = Set.of("java", "java.exe", "javac", "javac.exe");

    // Keep in sync with the {@code catch_files} array in {@code ci/common.jsonnet}.
    private static final TemporaryFiles SUBPROCESS_ARGFILES = new TemporaryFiles(SubprocessUtil.class.getName() + ".", ".argfile");

    /**
     * Translates {@code command} to a command line using an argfile if {@code command.get(0)}
     * denotes an {@linkplain #EXECUTABLES_USING_ARGFILES executable supporting argfiles}.
     *
     * @see "https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html#java-command-line-argument-files"
     */
    public static List<String> withArgfile(List<String> command) {
        File exe = new File(command.get(0));
        if (EXECUTABLES_USING_ARGFILES.contains(exe.getName())) {
            Path argfile = SUBPROCESS_ARGFILES.newFile();
            expandArgFileArgs(command);
            try {
                String content = expandArgFileArgs(command).stream().map(SubprocessUtil::quoteArgfileArgument).collect(Collectors.joining("\n"));
                Files.writeString(argfile, "# " + content);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            return List.of(command.get(0), "@" + argfile);
        }
        return command;
    }

    /**
     * Executes a command in a subprocess.
     *
     * @param command the command to be executed in a separate process.
     * @param env environment variables of the subprocess. If null, no environment variables are
     *            passed.
     * @param workingDir the working directory of the subprocess. If null, the working directory of
     *            the current process is used.
     * @param timeout the duration to wait for the process to finish. When the timeout is reached,
     *            the subprocess is terminated forcefully. If the timeout is null, the calling
     *            thread waits for the process indefinitely.
     */
    public static Subprocess process(List<String> command, Map<String, String> env, File workingDir, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(withArgfile(command));

        if (workingDir != null) {
            pb.directory(workingDir);
        }
        if (env != null) {
            Map<String, String> processBuilderEnv = pb.environment();
            processBuilderEnv.putAll(env);
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();
        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        List<String> output = new ArrayList<>();
        if (timeout == null) {
            String line;
            while ((line = stdout.readLine()) != null) {
                output.add(line);
            }
            return new Subprocess(pb.command(), env, process.pid(), process.waitFor(), output, false);
        } else {
            // The subprocess might produce output forever. We need to grab the output in a
            // separate thread, so we can terminate the process after the timeout if necessary.
            Thread outputReader = new Thread(() -> {
                try {
                    String line;
                    while ((line = stdout.readLine()) != null) {
                        output.add(line);
                    }
                } catch (IOException e) {
                    // happens when the process ends
                }
            });
            outputReader.start();
            boolean finishedOnTime = process.waitFor(timeout.getSeconds(), TimeUnit.SECONDS);
            if (!finishedOnTime) {
                process.destroyForcibly().waitFor();
            }
            outputReader.join();
            return new Subprocess(command, env, process.pid(), process.exitValue(), output, !finishedOnTime);
        }
    }

    private static boolean hasArg(String optionName) {
        if (optionName.equals("-cp") || optionName.equals("-classpath") || optionName.equals("-p")) {
            return true;
        }
        if (optionName.equals("--version") ||
                        optionName.equals("--show-version") ||
                        optionName.equals("--dry-run") ||
                        optionName.equals("--disable-@files") ||
                        optionName.equals("--dry-run") ||
                        optionName.equals("--help") ||
                        optionName.equals("--help-extra")) {
            return false;
        }
        if (optionName.startsWith("--")) {
            return optionName.indexOf('=') == -1;
        }
        return false;
    }

    private static int findMainClassIndex(List<String> commandLine) {
        int i = 1; // Skip the java executable

        while (i < commandLine.size()) {
            String s = commandLine.get(i);
            if (s.charAt(0) != '-') {
                // https://bugs.openjdk.java.net/browse/JDK-8027634
                if (s.charAt(0) != '@') {
                    return i;
                }
                i++;
            } else if (hasArg(s)) {
                i += 2;
            } else {
                i++;
            }
        }
        throw new InternalError();
    }

    private static final boolean DEBUG = Boolean.parseBoolean(Services.getSavedProperty("debug." + SubprocessUtil.class.getName()));

    /**
     * Manages temporary files with a given prefix and suffix.
     */
    public static class TemporaryFiles {
        private final Path dir;
        private final FileTime maxAge;
        private final String prefix;
        private final String suffix;

        public TemporaryFiles(String prefix, String suffix) {
            this.dir = Path.of(System.getProperty("java.io.tmpdir"));
            this.maxAge = DEBUG ? //
                            FileTime.from(Instant.now().minus(5, ChronoUnit.SECONDS)) : //
                            FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS));
            this.prefix = prefix;
            this.suffix = suffix;
        }

        /**
         * Creates a new temporary file after {@linkplain #removeOldFiles() deleting} old temporary
         * files.
         */
        Path newFile() {
            removeOldFiles();
            try {
                Path path = Files.createTempFile(dir, prefix, suffix).toAbsolutePath();
                if (DEBUG) {
                    System.out.println("created " + path);
                }
                return path;
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * Deletes argfiles in {@link #dir} that start with {@link #prefix} and end with
         * {@link #suffix} whose last modification time is before {@link #maxAge}.
         */
        private void removeOldFiles() {
            try {
                Files.list(dir).filter(this::match).forEach(p -> {
                    try {
                        if (Files.getLastModifiedTime(p).compareTo(maxAge) < 0) {
                            Files.delete(p);
                            if (DEBUG) {
                                System.out.println("deleted " + p);
                            }
                            return;
                        }
                    } catch (IOException e) {
                        System.err.printf("Error deleting %s: %e%n", p, e);
                    }
                });
            } catch (IOException e) {
                System.err.printf("Error listing files in %s: %e%n", dir, e);
            }
        }

        private boolean match(Path p) {
            String fileName = p.getFileName().toString();
            return fileName.startsWith(prefix) && fileName.endsWith(suffix);
        }
    }
}
