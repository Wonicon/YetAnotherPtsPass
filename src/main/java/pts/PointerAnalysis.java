package pts;

import java.io.File;

import soot.PackManager;
import soot.Transform;

public class PointerAnalysis {
    public static void main(String[] args) {
        String classpath = args[0]
                + File.pathSeparator + args[0] + File.separator + "rt.jar"
                + File.pathSeparator + args[0] + File.separator + "jce.jar";
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.ptr", new WholeProgramTransformer()));
        soot.Main.main(new String[] {
                "-w",
                "-p", "wjtp.ptr", "enabled:true",
                "-soot-class-path", classpath,
                args[1]
        });
    }
}