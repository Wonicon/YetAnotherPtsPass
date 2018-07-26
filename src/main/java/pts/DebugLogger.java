package pts;

public class DebugLogger {
    boolean debug_all;
    boolean intraProc;
    boolean interProc;
    boolean disasm;
    boolean typePrint;
    boolean constraintPrint;
    boolean fieldSensitive;
    public boolean param;

    DebugLogger() {
        debug_all = true;
        interProc = false;
        intraProc = false;
        disasm = false;
        typePrint = false;
        constraintPrint = false;
        fieldSensitive = true;
        param = true;
    }

    void log(boolean flag, String format, Object... args) {
        if (debug_all && flag) {
            System.out.println(String.format(format, (Object[]) args));
        }
    }

    void loge(boolean flag, String format, Object... args) {
        if (debug_all && flag) {
            System.out.println(String.format(format, (Object[]) args));
        }
    }

    void info(String format, Object... args) {
        System.out.println(String.format(format, args));
    }
}
