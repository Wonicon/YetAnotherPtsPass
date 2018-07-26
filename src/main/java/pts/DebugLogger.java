package pts;

public class DebugLogger {
    boolean debug_all;
    boolean intraProc;
    boolean interProc;
    boolean disasm;
    boolean typePrint;
    boolean constraintPrint;
    boolean fieldSensitive;

    DebugLogger() {
        debug_all = true;
        interProc = false;
        intraProc = false;
        disasm = false;
        typePrint = false;
        constraintPrint = false;
        fieldSensitive = true;
    }

    void log(boolean flag, String format, Object... args) {
        if (debug_all && flag) {
            System.out.print(String.format(format, (Object[]) args));
        }
    }

    void loge(boolean flag, String format, Object... args) {
        if (debug_all && flag) {
            System.out.print(String.format(format, (Object[]) args));
        }
    }
}
