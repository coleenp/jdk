/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 8359366
 * @summary Set breakpoint in running EMCP method, stop it, then delete the method. Clearing the breakpoint in the next
 *          redefinition should not find the deleted method.
 * @requires vm.jvmti
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.compiler
 *          java.instrument
 *          jdk.jartool/sun.tools.jar
 * @run main RedefineClassHelper
 * @compile RedefineSetBreakpoint.java
 * @run main/othervm/native -Xlog:redefine+class+breakpoint=debug -agentlib:RedefineSetBreakpoint -javaagent:redefineagent.jar RedefineSetBreakpoint
 */


// package access top-level class to avoid problem with RedefineClassHelper
// and nested types.
class RedefineSetBreakpoint_B {
    static int count2 = 0;
    public static volatile boolean stop = false;
    static void localSleep() {
        try{
            Thread.currentThread().sleep(10);//sleep for 10 ms
        } catch(InterruptedException ie) {
        }
    }

    public static void infinite_emcp() {
        while (!stop) { count2++; localSleep(); }
    }
}

public class RedefineSetBreakpoint {

    public static String newB = """
                class RedefineSetBreakpoint_B {
                    static int count2 = 0;
                    public static volatile boolean stop = false;
                    static void localSleep() {
                        try {
                            Thread.currentThread().sleep(10);
                        } catch(InterruptedException ie) {
                        }
                    }
                    public static void infinite_emcp() {
                        while (!stop) { count2++; localSleep(); }
                    }
                }
                """;

    public static String evenNewerB = """
                class RedefineSetBreakpoint_B {
                    static int count2 = 0;
                    public static volatile boolean stop = false;
                    static void localSleep() {
                        try {
                            Thread.currentThread().sleep(1);
                        } catch(InterruptedException ie) {
                        }
                    }
                    public static void infinite_emcp() {
                        System.out.println("infinite_emcp now obsolete called");
                    }
                }
                """;

    static native void setBreakpoint();

    public static void main(String[] args) throws Exception {

        var t2 = Thread.ofPlatform().start(RedefineSetBreakpoint_B::infinite_emcp);

        RedefineClassHelper.redefineClass(RedefineSetBreakpoint_B.class, newB);

        // Set a breakpoint in infinite_emcp.
        setBreakpoint();

        // And stop infinite_emcp.
        RedefineSetBreakpoint_B.stop = true;

        // Do some GC but need to trigger the ServiceThread to clean up old infinite_emcp.
        for (int i = 0; i < 20 ; i++) {
            String s = new String("some garbage");
            System.gc();
        }

        // Redefine again. Clearing breakpoints shouldn't barf on the old infinite_emcp method.
        RedefineClassHelper.redefineClass(RedefineSetBreakpoint_B.class, evenNewerB);

        // Call one last time.
        RedefineSetBreakpoint_B.infinite_emcp();

        t2.join();
    }
}
