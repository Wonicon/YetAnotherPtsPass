package pts;

public class DebugLogger {
    boolean debug_all;
    boolean intraProc;
    boolean interProc;
    boolean disasm;

    DebugLogger() {
        debug_all = true;
        interProc = false;
        intraProc = true;
        disasm = true;
    }

    void log(boolean flag, String format, Object... args) {
        if (debug_all && flag) {
            System.out.print(String.format(format, (Object[]) args));
        }
    }
}
