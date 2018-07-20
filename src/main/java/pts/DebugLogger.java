package pts;

public class DebugLogger {
    boolean debug_all;
    boolean intraProc;
    boolean interProc;
    boolean disasm;
    boolean typePrint;

    DebugLogger() {
        debug_all = true;
        interProc = false;
        intraProc = true;
        disasm = false;
        typePrint = false;
    }

    void log(boolean flag, String format, Object... args) {
        if (debug_all && flag) {
            System.out.print(String.format(format, (Object[]) args));
        }
    }

    void loge(boolean flag, String format, Object... args) {
        if (debug_all && flag) {
            System.err.print(String.format(format, (Object[]) args));
        }
    }
}
